# Opt-in memory lifecycle

NEXUS can use `ProviderMemoryCandidateExtractor` to extract concise, durable facts from a conversation and `MemoryLifecycleService` to save and index them.

## Privacy defaults

Automatic capture is **disabled by default** (`MemoryCapturePolicy.enabled = false`). A host must explicitly enable it and should provide a visible setting and a way to inspect/delete saved memories. The extractor prompt excludes credentials, secrets, inferred sensitive traits, and temporary details, but model output is not a security boundary; hosts should treat extracted text as untrusted and provide user review where appropriate.

## Lifecycle

- Extraction is provider-backed and requests a strict JSON array of candidate facts.
- Candidate text and tags are validated and bounded before saving.
- SHA-256 fingerprints provide deterministic, case/whitespace-insensitive deduplication.
- Text records remain in `MemoryStore`; embeddings alone are written to `VectorIndex`.
- Failed embedding/index writes roll back the newly created text record.
- `reindexAll` computes replacement embeddings before clearing the index, then rebuilds it from source records.
- Delete removes the source record and its vector; export delegates to the source store.

## Limitations

The core exposes an opt-in capture operation; a host decides when to call it (for example, after a completed turn) and how to present review controls. It does not silently start background extraction. Re-indexing requires the same embedding dimensions across the records in a given index. A host changing embedding models should use a fresh index or run a controlled migration.

## Verification

Unit tests cover disabled-by-default behavior, deduplication, invalid candidate rejection, provider JSON parsing, re-indexing, and deletion. Tests use fake providers and local temporary vector files.
