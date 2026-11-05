# Open-source integration audit

Reviewed on 2026-11-05. NEXUS keeps its own Kotlin domain contracts and adopts narrowly selected designs rather than embedding another complete agent framework.

## Integrated in this change

### Mem0 — memory lifecycle and retrieval

Upstream: https://github.com/mem0ai/mem0  
Licence: Apache-2.0  
Reviewed source:
- `mem0/memory/base.py` — explicit get/list/update/delete/history lifecycle.
- `mem0/memory/storage.py` — synchronized storage access, transactional writes, and a separate history/messages store.
- `skills/mem0/references/architecture.md` — user/agent/session scopes, expiry, and multi-signal retrieval concepts.

NEXUS adaptation:
- Added USER, AGENT, and SESSION memory scopes with required scope identifiers for non-user records.
- Added optional expiry and scope-aware search options.
- Added a versioned, app-private JSON store with atomic replacement, deterministic keyword/tag ranking, export, and deletion.
- Kept the store behind the existing `MemoryStore` port. No Python runtime, vector database, hosted Mem0 service, or API key is required.

Not copied: Mem0's Python implementation. NEXUS currently performs lexical/tag retrieval only; semantic embeddings, entity linking, LLM extraction, and hybrid vector/BM25 fusion are future work and are not claimed as implemented.

## Strong candidates for later PRs

### JetBrains Koog — Kotlin agent runtime patterns

Upstream: https://github.com/JetBrains/koog  
Licence: Apache-2.0  
Reviewed source:
- `agents/agents-features/agents-features-snapshot/src/commonMain/kotlin/ai/koog/agents/snapshot/providers/PersistenceStorageProvider.kt`
- `agents/agents-features/agents-features-snapshot/src/jvmMain/kotlin/ai/koog/agents/snapshot/providers/file/JVMFilePersistenceStorageProvider.kt`
- `docs/docs/features/agent-persistence.md`
- `docs/docs/history-compression.md`

Why it fits: Koog is Kotlin/JVM-first and documents portable storage-provider contracts, resumable checkpoints, retries, history compression, streaming, and OpenTelemetry exporters.

NEXUS decision: reuse the architectural pattern, not the full Koog agent runtime. NEXUS already owns its provider router, coordinator, permissions, and agent contracts. A full framework dependency would duplicate those boundaries and make the Android host harder to keep lean. Follow-up work should add session persistence/checkpoints and token-budgeted history summarization behind NEXUS ports.

### LangChain4j — JVM interoperability reference

Upstream: https://github.com/langchain4j/langchain4j  
Licence: Apache-2.0  
Reviewed source: project README and documented API surface.

Why it fits: Java-native provider, embedding, vector-store, tool-calling, MCP, agent, and RAG abstractions.

NEXUS decision: do not replace the existing provider adapters or add the entire library now. Revisit individual integrations (especially MCP and embedding adapters) only when a concrete NEXUS capability needs them and after checking Android/runtime footprint.

### AgentDojo — security regression benchmark

Upstream: https://github.com/ethz-spylab/agentdojo  
Licence: MIT  
Reviewed source: benchmark README and documented attack/defense task suites.

Why it fits: reproducible prompt-injection tests for tool-using agents.

NEXUS decision: use the benchmark as a source of adversarial test scenarios, translated into deterministic Kotlin unit tests. Do not add its Python benchmark runtime to the production core.

## Selection rules

1. Prefer Apache-2.0 or MIT for code-level adaptation; preserve attribution and licence notices whenever source code is copied.
2. Inspect the exact source file and its licence before adapting it.
3. Keep NEXUS contracts authoritative; do not add an upstream runtime merely because it has more features.
4. Avoid Python/Rust dependencies in the Kotlin core unless isolated behind an optional service or adapter.
5. Every imported idea must have focused tests. A roadmap checkbox means code exists; "working" requires passing CI or a real integration test.
6. Do not store raw prompts, credentials, or tool arguments in memory or traces by default.
