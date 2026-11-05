# Open-source integration audit

Reviewed on 2026-11-05. NEXUS keeps its own Kotlin domain contracts and adopts narrowly selected designs rather than embedding another complete agent framework.

## Existing adaptations
- OpenJarvis: trace model/store contract, declarative skill trust and capability policy, and repeated-call/ping-pong loop guard. See [OPENJARVIS_INTEGRATION.md](OPENJARVIS_INTEGRATION.md).
- Mem0: scoped memory lifecycle, expiry, retrieval, export and deletion patterns. NEXUS implementation is Kotlin and JSON-backed; retrieval remains lexical/tag-based.
- Koog: storage-provider and checkpoint patterns studied; no Koog runtime imported.
- LangChain4j: JVM interoperability patterns studied; no framework dependency imported.
- AgentDojo: adversarial tool-use scenarios studied for future deterministic tests.

## Runtime/tool/persistence milestone
- NEXUS implements its own bounded agent loop through `AgentRuntime`.
- `AgentDecisionEngine` separates model decisions from policy-controlled execution.
- `ToolRegistry` reuses the existing `ToolAdapter` contract. Registration does not grant permission.
- Existing `SkillPolicy`, `PermissionManager`, `AgentLoopGuard`, and `AuditSink` remain in the execution path.
- `JsonFileSessionStore` and `JsonFileTraceStore` provide versioned local persistence with atomic replacement.
- Text-only providers are supported through `TextOnlyDecisionEngine`; structured provider-native tool calling remains future work.
- MCP Kotlin SDK, Koog, ADK and Android Agent Runtime remain optional future integration candidates, not production dependencies.

## Selection rules
1. Prefer Apache-2.0 or MIT for code-level adaptation; preserve attribution and notices whenever source code is copied.
2. Inspect the exact source file and its licence before adapting it.
3. Keep NEXUS contracts authoritative; do not add an upstream runtime merely because it has more features.
4. Avoid Python/Rust/Go dependencies in the Kotlin core unless isolated behind an optional service or adapter.
5. Every imported idea must have focused tests. A roadmap checkbox means code exists; "working" requires passing CI or a real integration test.
6. Do not store raw prompts, credentials, or tool arguments in memory or traces by default.
