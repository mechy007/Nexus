package com.mechy007.nexus.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * Small durable adapter for local-first deployments.
 *
 * The JSON format is versioned and deliberately stores only memory data, never prompts,
 * credentials, or provider request payloads. Android hosts can use their app-private files
 * directory; a future Room adapter can implement the same ScopedMemoryStore contract.
 */
class JsonFileMemoryStore(private val file: File) : ScopedMemoryStore {
    private val lock = Any()

    override suspend fun search(query: String, limit: Int): List<MemoryRecord> =
        search(MemorySearchOptions(query = query, limit = limit))

    override suspend fun search(options: MemorySearchOptions): List<MemoryRecord> = synchronized(lock) {
        val now = Instant.now()
        val tokens = tokenize(options.query)
        readAll()
            .asSequence()
            .filter { it.scope in options.scopes }
            .filter { options.scopeId == null || it.scopeId == options.scopeId }
            .filter { options.includeExpired || it.expiresAt == null || it.expiresAt.isAfter(now) }
            .map { record -> record to relevance(record, tokens, options.query) }
            .filter { tokens.isEmpty() || it.second > 0 }
            .sortedWith(compareByDescending<Pair<MemoryRecord, Int>> { it.second }
                .thenByDescending { it.first.createdAt })
            .take(options.limit)
            .map { it.first }
            .toList()
    }

    override suspend fun put(record: MemoryRecord) {
        synchronized(lock) {
            val records = readAll().associateBy { it.id }.toMutableMap()
            records[record.id] = record
            writeAll(records.values.sortedBy { it.createdAt })
        }
    }

    override suspend fun delete(id: String) {
        synchronized(lock) {
            val records = readAll().filterNot { it.id == id }
            writeAll(records)
        }
    }

    override suspend fun exportAll(): List<MemoryRecord> = synchronized(lock) {
        readAll().sortedByDescending { it.createdAt }
    }

    private fun readAll(): List<MemoryRecord> {
        if (!file.exists()) return emptyList()
        val root = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
        require(root.get("schemaVersion")?.asInt == SCHEMA_VERSION) {
            "Unsupported memory store schema version."
        }
        return root.getAsJsonArray("memories").map { element ->
            val item = element.asJsonObject
            val tags = item.getAsJsonArray("tags").map { it.asString }.toSet()
            MemoryRecord(
                id = item.get("id").asString,
                text = item.get("text").asString,
                tags = tags,
                createdAt = Instant.parse(item.get("createdAt").asString),
                sourceSessionId = item.get("sourceSessionId")?.takeUnless { it.isJsonNull }?.asString?.let(::SessionId),
                scope = MemoryScope.valueOf(item.get("scope")?.asString ?: MemoryScope.USER.name),
                scopeId = item.get("scopeId")?.takeUnless { it.isJsonNull }?.asString,
                expiresAt = item.get("expiresAt")?.takeUnless { it.isJsonNull }?.asString?.let(Instant::parse)
            )
        }
    }

    private fun writeAll(records: Collection<MemoryRecord>) {
        val parent = file.absoluteFile.parentFile
        require(parent.exists() || parent.mkdirs()) { "Could not create memory directory." }
        val root = JsonObject().apply {
            addProperty("schemaVersion", SCHEMA_VERSION)
            add("memories", JsonArray().apply {
                records.forEach { record ->
                    add(JsonObject().apply {
                        addProperty("id", record.id)
                        addProperty("text", record.text)
                        add("tags", JsonArray().apply { record.tags.sorted().forEach(::add) })
                        addProperty("createdAt", record.createdAt.toString())
                        addProperty("sourceSessionId", record.sourceSessionId?.value)
                        addProperty("scope", record.scope.name)
                        addProperty("scopeId", record.scopeId)
                        addProperty("expiresAt", record.expiresAt?.toString())
                    })
                }
            })
        }
        val temp = File.createTempFile(file.name.padEnd(3, '_'), ".tmp", parent)
        try {
            temp.writeText(root.toString(), Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun relevance(record: MemoryRecord, tokens: Set<String>, rawQuery: String): Int {
        if (tokens.isEmpty()) return 0
        val text = record.text.lowercase()
        val tagText = record.tags.joinToString(" ").lowercase()
        val tokenScore = tokens.sumOf { token ->
            (if (text.split(WORD_BOUNDARY).contains(token)) 3 else if (text.contains(token)) 1 else 0) +
                (if (tagText.split(WORD_BOUNDARY).contains(token)) 2 else 0)
        }
        return tokenScore + if (rawQuery.isNotBlank() && text.contains(rawQuery.trim().lowercase())) 4 else 0
    }

    private fun tokenize(value: String): Set<String> =
        value.lowercase().split(WORD_BOUNDARY).filter { it.length > 1 }.toSet()

    private companion object {
        const val SCHEMA_VERSION = 1
        val WORD_BOUNDARY = Regex("[^\\p{L}\\p{N}]+")
    }
}
