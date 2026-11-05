package com.mechy007.nexus.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/** Versioned app-private JSON persistence for sessions and messages. */
class JsonFileSessionStore(private val directory: File) : SessionStore {
    private val lock = Any()

    override suspend fun get(id: SessionId): NexusSession? = synchronized(lock) {
        val file = sessionFile(id)
        if (!file.exists()) null else decode(JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject)
    }

    override suspend fun save(session: NexusSession) = synchronized(lock) {
        if (!directory.exists()) require(directory.mkdirs()) { "Could not create session directory." }
        val target = sessionFile(session.id)
        val temp = File.createTempFile("nexus-session-", ".tmp", directory)
        try {
            temp.writeText(encode(session).toString(), Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    override suspend fun delete(id: SessionId) = synchronized(lock) {
        val file = sessionFile(id)
        if (file.exists() && !file.delete()) error("Could not delete session.")
    }

    override suspend fun list(): List<NexusSession> = synchronized(lock) {
        if (!directory.exists()) return@synchronized emptyList()
        directory.listFiles { file -> file.isFile && file.name.endsWith(".session.json") }
            .orEmpty().map { decode(JsonParser.parseString(it.readText(Charsets.UTF_8)).asJsonObject) }
            .sortedByDescending { it.updatedAt }
    }

    private fun sessionFile(id: SessionId): File {
        val safe = id.value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(directory, safe + ".session.json")
    }

    private fun encode(session: NexusSession) = JsonObject().apply {
        addProperty("schemaVersion", 1)
        addProperty("id", session.id.value)
        addProperty("title", session.title)
        addProperty("createdAt", session.createdAt.toString())
        addProperty("updatedAt", session.updatedAt.toString())
        add("messages", JsonArray().apply {
            session.messages.forEach { message ->
                add(JsonObject().apply {
                    addProperty("id", message.id)
                    addProperty("role", message.role.name)
                    addProperty("content", message.content)
                    addProperty("createdAt", message.createdAt.toString())
                    addProperty("agentId", message.agentId?.value)
                    add("metadata", JsonObject().apply {
                        message.metadata.toSortedMap().forEach { (key, value) -> addProperty(key, value) }
                    })
                })
            }
        })
    }

    private fun decode(root: JsonObject): NexusSession {
        require(root.get("schemaVersion")?.asInt == 1) { "Unsupported session schema version." }
        val messages = root.getAsJsonArray("messages").map { element ->
            val item = element.asJsonObject
            val metadata = item.getAsJsonObject("metadata")?.entrySet()
                ?.associate { it.key to it.value.asString }.orEmpty()
            NexusMessage(
                id = item.get("id").asString,
                role = MessageRole.valueOf(item.get("role").asString),
                content = item.get("content").asString,
                createdAt = Instant.parse(item.get("createdAt").asString),
                agentId = item.get("agentId")?.takeUnless { it.isJsonNull }?.asString?.let(::AgentId),
                metadata = metadata
            )
        }
        return NexusSession(
            id = SessionId(root.get("id").asString), title = root.get("title").asString,
            messages = messages, createdAt = Instant.parse(root.get("createdAt").asString),
            updatedAt = Instant.parse(root.get("updatedAt").asString)
        )
    }
}
