package com.mechy007.nexus.core

/**
 * Combines the existing lexical MemoryStore search with vector similarity using
 * reciprocal-rank fusion. The original MemoryStore remains the source of truth:
 * complete records (including tags, scope, expiry and source session) are returned.
 *
 * Retrieval is read-only. It never promotes retrieved content into trusted instructions.
 */
class HybridMemoryStore(
    private val records: MemoryStore,
    private val semantic: SemanticMemoryService,
    private val candidateMultiplier: Int = 4,
    private val maxCandidates: Int = 100,
    private val rankConstant: Int = 60
) : ScopedMemoryStore {
    init {
        require(candidateMultiplier > 0)
        require(maxCandidates > 0)
        require(rankConstant > 0)
    }

    override suspend fun search(query: String, limit: Int): List<MemoryRecord> =
        search(MemorySearchOptions(query = query, limit = limit))

    override suspend fun search(options: MemorySearchOptions): List<MemoryRecord> {
        if (options.limit == 0 || options.query.isBlank()) return emptyList()
        val candidateLimit = (options.limit * candidateMultiplier).coerceIn(options.limit, maxCandidates)
        val lexical = if (records is ScopedMemoryStore) {
            records.search(options.copy(limit = candidateLimit))
        } else {
            val now = java.time.Instant.now()
            records.search(options.query, candidateLimit).filter { record ->
                record.scope in options.scopes &&
                    (options.scopeId == null || record.scopeId == options.scopeId) &&
                    (options.includeExpired || record.expiresAt == null || record.expiresAt.isAfter(now))
            }
        }
        val vector = semantic.search(options.copy(limit = candidateLimit))
        val byId = linkedMapOf<String, MemoryRecord>()
        val scores = mutableMapOf<String, Double>()

        fun addRanked(items: List<MemoryRecord>) {
            items.forEachIndexed { index, record ->
                byId.putIfAbsent(record.id, record)
                scores[record.id] = (scores[record.id] ?: 0.0) +
                    1.0 / (rankConstant + index + 1)
            }
        }

        addRanked(lexical)
        addRanked(vector.map { it.record })

        return byId.values
            .sortedWith(
                compareByDescending<MemoryRecord> { scores[it.id] ?: 0.0 }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id }
            )
            .take(options.limit)
    }

    override suspend fun put(record: MemoryRecord) {
        semantic.put(record)
    }

    override suspend fun delete(id: String) {
        semantic.delete(id)
    }

    override suspend fun exportAll(): List<MemoryRecord> = records.exportAll()
}
