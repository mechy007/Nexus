package com.mechy007.nexus.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

data class MemoryCandidate(val text: String, val tags: Set<String> = emptySet())

fun interface MemoryCandidateExtractor {
    suspend fun extract(session: NexusSession, agent: AgentProfile): List<MemoryCandidate>
}

data class MemoryCapturePolicy(
    val enabled: Boolean = false,
    val maxMemoriesPerTurn: Int = 5,
    val maxMemoryChars: Int = 1_000,
    val maxTranscriptChars: Int = 12_000,
    val minimumMemoryChars: Int = 12
) {
    init {
        require(maxMemoriesPerTurn > 0 && maxMemoryChars > 0)
        require(maxTranscriptChars > 0 && minimumMemoryChars > 0)
    }
}

data class MemoryCaptureResult(
    val enabled: Boolean,
    val extracted: Int = 0,
    val saved: Int = 0,
    val duplicates: Int = 0,
    val rejected: Int = 0
)

/**
 * Opt-in memory extraction, deduplication, indexing, deletion and re-indexing.
 * MemoryStore remains the source of truth; VectorIndex stores embeddings only.
 */
class MemoryLifecycleService(
    private val memories: MemoryStore,
    private val embeddings: EmbeddingProvider,
    private val index: VectorIndex,
    private val extractor: MemoryCandidateExtractor,
    private val policy: MemoryCapturePolicy = MemoryCapturePolicy()
) {
    suspend fun captureTurn(session: NexusSession, agent: AgentProfile): MemoryCaptureResult {
        if (!policy.enabled) return MemoryCaptureResult(enabled = false)
        val candidates = extractor.extract(session, agent).take(policy.maxMemoriesPerTurn)
        val existing = memories.exportAll()
        val fingerprints = existing.mapTo(mutableSetOf()) { fingerprint(it.text) }
        var saved = 0
        var duplicates = 0
        var rejected = 0
        for (candidate in candidates) {
            val normalized = candidate.text.trim().replace(Regex("\\s+"), " ")
            if (normalized.length < policy.minimumMemoryChars || normalized.length > policy.maxMemoryChars ||
                normalized.any { it.isISOControl() }) {
                rejected++
                continue
            }
            val key = fingerprint(normalized)
            if (!fingerprints.add(key)) {
                duplicates++
                continue
            }
            val record = MemoryRecord(
                id = key,
                text = normalized,
                tags = candidate.tags.map { it.trim().lowercase() }
                    .filter { it.isNotBlank() && it.length <= 48 }.toSet(),
                sourceSessionId = session.id,
                scope = MemoryScope.USER,
                createdAt = Instant.now()
            )
            memories.put(record)
            try {
                val vector = embeddings.embed(record.text)
                validateEmbedding(vector)
                index.upsert(record.id, vector)
                saved++
            } catch (failure: Exception) {
                // Avoid leaving a text record that the semantic index cannot retrieve.
                memories.delete(record.id)
                throw failure
            }
        }
        return MemoryCaptureResult(true, candidates.size, saved, duplicates, rejected)
    }

    /** Rebuilds all vectors from source records after an embedding model/version change. */
    suspend fun reindexAll(): Int {
        val records = memories.exportAll()
        val prepared = records.map { record ->
            val vector = embeddings.embed(record.text)
            validateEmbedding(vector)
            record.id to vector
        }
        index.clear()
        prepared.forEach { (id, vector) -> index.upsert(id, vector) }
        return prepared.size
    }

    suspend fun delete(id: String) {
        memories.delete(id)
        index.delete(id)
    }

    suspend fun exportAll(): List<MemoryRecord> = memories.exportAll()

    private fun validateEmbedding(vector: FloatArray) {
        require(vector.isNotEmpty() && vector.all(Float::isFinite) && vector.any { it != 0f }) {
            "Embedding provider returned an invalid vector."
        }
    }

    private fun fingerprint(text: String): String {
        val canonical = text.trim().replace(Regex("\\s+"), " ").lowercase()
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/** Uses a text-capable provider to extract concise facts; saving remains opt-in at the service policy. */
class ProviderMemoryCandidateExtractor(
    private val router: ProviderRouter,
    private val preferredProvider: ProviderId? = null,
    private val maxTranscriptChars: Int = 12_000,
    private val maxResponseChars: Int = 8_000
) : MemoryCandidateExtractor {
    init { require(maxTranscriptChars > 0 && maxResponseChars > 0) }

    override suspend fun extract(session: NexusSession, agent: AgentProfile): List<MemoryCandidate> {
        val transcript = session.messages.takeLast(30).joinToString("\n") {
            "${it.role.name}: ${it.content}"
        }.take(maxTranscriptChars)
        if (transcript.isBlank()) return emptyList()
        val prompt = buildString {
            append("Extract only durable, useful facts explicitly stated by the user. ")
            append("Do not infer sensitive traits, credentials, secrets, or temporary details. ")
            append("Return exactly JSON: {\"memories\":[{\"text\":\"fact\",\"tags\":[\"tag\"]}]}. ")
            append("Return an empty array if nothing should be remembered.\n")
            append("Conversation:\n").append(transcript)
        }
        val response = router.generate(
            GenerationRequest(
                session = session,
                agent = agent,
                prompt = prompt,
                preferredProvider = preferredProvider,
                requiredCapabilities = ProviderCapabilities(text = true)
            )
        )
        require(response.text.length <= maxResponseChars) { "Memory extraction response exceeds configured limit." }
        val root = try {
            JsonParser.parseString(response.text).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) { null }
            ?: throw IllegalArgumentException("Memory extractor returned invalid JSON.")
        val items = root.get("memories")
        require(items != null && items.isJsonArray) { "Memory extraction response must contain a memories array." }
        return items.asJsonArray.mapNotNull(::parseCandidate)
    }

    private fun parseCandidate(element: com.google.gson.JsonElement): MemoryCandidate? {
        if (!element.isJsonObject) return null
        val item = element.asJsonObject
        val text = item.get("text")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: return null
        val tags = item.get("tags")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { value ->
                value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            }?.toSet().orEmpty()
        return MemoryCandidate(text, tags)
    }
}
