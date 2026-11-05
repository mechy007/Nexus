package com.mechy007.nexus.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.math.sqrt

fun interface EmbeddingProvider {
    suspend fun embed(text: String): FloatArray
}

data class VectorMatch(val id: String, val score: Float) {
    init { require(id.isNotBlank() && score.isFinite()) }
}

interface VectorIndex {
    suspend fun upsert(id: String, vector: FloatArray)
    suspend fun search(vector: FloatArray, limit: Int = 10): List<VectorMatch>
    suspend fun delete(id: String)
    suspend fun clear()
}

/** Versioned local vector index. Embeddings are numeric vectors only, not raw prompts. */
class JsonFileVectorIndex(private val file: File) : VectorIndex {
    private val lock = Any()
    private val vectors = linkedMapOf<String, FloatArray>()
    private var loaded = false

    override suspend fun upsert(id: String, vector: FloatArray) = synchronized(lock) {
        require(id.isNotBlank())
        validateVector(vector)
        ensureLoaded()
        vectors[id] = vector.copyOf()
        persist()
    }

    override suspend fun search(vector: FloatArray, limit: Int): List<VectorMatch> = synchronized(lock) {
        require(limit >= 0)
        validateVector(vector)
        ensureLoaded()
        vectors.mapNotNull { (id, candidate) ->
            if (candidate.size != vector.size) null else VectorMatch(id, cosine(vector, candidate))
        }.sortedWith(compareByDescending<VectorMatch> { it.score }.thenBy { it.id }).take(limit)
    }

    override suspend fun delete(id: String) = synchronized(lock) {
        ensureLoaded()
        if (vectors.remove(id) != null) persist()
    }

    override suspend fun clear() = synchronized(lock) {
        ensureLoaded()
        vectors.clear()
        persist()
    }

    private fun ensureLoaded() {
        if (loaded) return
        if (file.exists()) {
            val root = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
            require(root.get("schemaVersion")?.asInt == SCHEMA_VERSION) { "Unsupported vector index schema." }
            root.getAsJsonArray("vectors").forEach { element ->
                val item = element.asJsonObject
                val values = item.getAsJsonArray("values").map { it.asFloat }.toFloatArray()
                validateVector(values)
                vectors[item.get("id").asString] = values
            }
        }
        loaded = true
    }

    private fun persist() {
        val parent = file.absoluteFile.parentFile
        require(parent.exists() || parent.mkdirs()) { "Could not create vector index directory." }
        val root = JsonObject().apply {
            addProperty("schemaVersion", SCHEMA_VERSION)
            add("vectors", JsonArray().apply {
                vectors.toSortedMap().forEach { (id, vector) ->
                    add(JsonObject().apply {
                        addProperty("id", id)
                        add("values", JsonArray().apply { vector.forEach { add(it.toDouble()) } })
                    })
                }
            })
        }
        val temp = File.createTempFile("nexus-vectors-", ".tmp", parent)
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

    private fun validateVector(vector: FloatArray) {
        require(vector.isNotEmpty() && vector.all(Float::isFinite)) { "Embedding must contain finite values." }
        require(vector.any { it != 0f }) { "Embedding cannot be a zero vector." }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            normA += a[i].toDouble() * a[i].toDouble()
            normB += b[i].toDouble() * b[i].toDouble()
        }
        if (normA == 0.0 || normB == 0.0) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    private companion object { const val SCHEMA_VERSION = 1 }
}

data class SemanticMemoryMatch(val record: MemoryRecord, val score: Float)

/**
 * Coordinates explicit memory writes with an embedding index. Existing MemoryStore remains
 * the source of truth for text, scopes, expiry, export, and deletion.
 */
class SemanticMemoryService(
    private val memories: MemoryStore,
    private val embeddings: EmbeddingProvider,
    private val index: VectorIndex
) {
    suspend fun put(record: MemoryRecord) {
        val vector = embeddings.embed(record.text)
        require(vector.isNotEmpty() && vector.all(Float::isFinite) && vector.any { it != 0f })
        memories.put(record)
        try {
            index.upsert(record.id, vector)
        } catch (error: Exception) {
            // Keep the source of truth consistent if indexing fails for a newly created record.
            throw error
        }
    }

    suspend fun search(options: MemorySearchOptions): List<SemanticMemoryMatch> {
        if (options.limit == 0) return emptyList()
        val queryVector = embeddings.embed(options.query)
        val ranked = index.search(queryVector, Int.MAX_VALUE).associateBy { it.id }
        if (ranked.isEmpty()) return emptyList()
        val now = Instant.now()
        return memories.exportAll().asSequence()
            .filter { it.id in ranked }
            .filter { it.scope in options.scopes }
            .filter { options.scopeId == null || it.scopeId == options.scopeId }
            .filter { options.includeExpired || it.expiresAt == null || it.expiresAt.isAfter(now) }
            .map { SemanticMemoryMatch(it, ranked.getValue(it.id).score) }
            .sortedWith(compareByDescending<SemanticMemoryMatch> { it.score }
                .thenByDescending { it.record.createdAt })
            .take(options.limit)
            .toList()
    }

    suspend fun delete(id: String) {
        memories.delete(id)
        index.delete(id)
    }
}
