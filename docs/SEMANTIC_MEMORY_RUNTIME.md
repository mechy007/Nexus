# Semantic memory in AgentRuntime

`AgentRuntime` already consumes memory through the `MemoryStore` port. This
adapter lets the runtime use the vector-backed `SemanticMemoryService` without
coupling orchestration to vector-index or embedding implementations.

## Wiring

Construct the existing record store, an `EmbeddingProvider`, and a
`VectorIndex`; create `SemanticMemoryService`; then inject
`SemanticMemoryStoreAdapter(records, semanticService)` as AgentRuntime's
`memory` argument.

The adapter routes runtime searches through semantic ranking while preserving
the existing MemoryStore contract for writes, deletion, and export.

## Important operational note

Existing records are not automatically embedded by this adapter. Index records
through `SemanticMemoryStoreAdapter.put` (or a future explicit reindex operation)
before expecting them to appear in semantic search. The embedding provider and
vector index remain host-supplied dependencies.
