package com.mechy007.nexus.core

/**
 * Presents semantic retrieval through the existing MemoryStore port so AgentRuntime
 * can consume the vector-backed service without depending on its concrete type.
 *
 * The delegate remains the source of truth for records and export; the semantic
 * service owns embedding/index writes and deletion.
 */
class SemanticMemoryStoreAdapter(
    private val records: MemoryStore,
    private val semantic: SemanticMemoryService
) : MemoryStore {
    override suspend fun search(query: String, limit: Int): List<MemoryRecord> {
        if (limit <= 0 || query.isBlank()) return emptyList()
        return semantic.search(MemorySearchOptions(query = query, limit = limit))
            .map { it.record }
    }

    override suspend fun put(record: MemoryRecord) {
        semantic.put(record)
    }

    override suspend fun delete(id: String) {
        semantic.delete(id)
    }

    override suspend fun exportAll(): List<MemoryRecord> = records.exportAll()
}
