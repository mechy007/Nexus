# Semantic memory

Semantic memory is an optional retrieval layer over the existing MemoryStore. The text store remains authoritative; the vector index stores only record IDs and embedding vectors.

## Contracts
- `EmbeddingProvider` creates vectors. It can later be backed by an on-device embedding model or a remote embedding service.
- `VectorIndex` supports upsert, cosine-similarity search, delete, and clear.
- `JsonFileVectorIndex` persists vectors in a versioned JSON file using atomic replacement.
- `SemanticMemoryService` coordinates explicit memory writes, ranking, scope/expiry filtering, and deletion.
- Search results return scores and the original MemoryRecord. User/agent/session scoping remains enforced after retrieval.

## Boundaries
This PR supplies deterministic vector-index behavior and tests with a fake embedder. It does not download an embedding model, generate embeddings on-device, automatically extract memories from conversations, or change the existing agent runtime's memory policy. The Android host can inject a LiteRT-compatible embedding provider later. Re-indexing/migration and encrypted-at-rest storage remain follow-up work.
