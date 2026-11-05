# Roadmap

The objective is to add broad capability coverage, then verify each feature. A roadmap checkbox means code exists; "working" additionally requires successful tests or an integration check.

## Foundation
- [x] Repository and module structure
- [x] Domain models for sessions, messages, agents, providers, memory, and tools
- [x] Ports for provider, storage, permission, tool, and audit adapters
- [x] Platform-independent coordinator boundary
- [x] CI build and unit-test verification

## Intelligence
- [ ] Built-in Lara, JARVIS, FRIDAY, and EDITH profiles
- [x] Provider registry and capability-aware routing
- [x] Deterministic provider fallback and cancellation propagation
- [x] Concrete HTTP adapters for OpenAI, Gemini, OpenRouter, and Groq (mock-transport tests; live credentials not verified)
- [ ] Provider health checks and configurable routing policies
- [ ] Streaming, request timeouts, and retry/backoff policies
- [ ] Context budgeting and summarization
- [x] NEXUS-owned agent execution loop with bounded steps and tool-decision boundary
- [ ] Provider-native structured tool-call planning and validation
- [ ] LiteRT-LM Android engine adapter and target-device benchmark

## Memory and observability
- [x] Platform-independent agent trace model and replaceable trace-store contract
- [x] Durable JSON trace adapter with inspection and deletion
- [x] Local JSON session persistence and migrations (schema version 1; migration framework remains future work)
- [x] Session persistence across process restarts
- [x] Scoped local memory with export, search, expiry, and deletion
- [x] Embedding-provider and vector-index contracts
- [x] Persistent cosine-similarity vector index and scoped semantic retrieval
- [ ] On-device embedding model and automatic memory extraction
- [ ] Re-indexing/migrations and encrypted-at-rest storage
- [ ] User review UI for memory and trace deletion

## Skills and tools
- [x] Declarative skill manifests and capability/trust policy
- [x] Per-turn repeated-call, ping-pong and call-budget guard
- [x] Unified native tool registry
- [x] Permission-gated tool execution and user-approval handoff
- [x] Tool execution audit and trace events
- [x] MCP remote-tool bridge contracts, allowlisting, and unreviewed trust defaults
- [ ] Concrete official Kotlin MCP SDK Streamable HTTP connector and credential flow
- [ ] MCP resources/prompts browser and server management UI
- [ ] Tool input schema validation and execution timeouts
- [ ] Durable approval checkpoints and automatic task resumption
- [ ] Search, file, and automation connectors

## Voice and avatar
- [ ] Speech-to-text and text-to-speech adapters
- [ ] Wake-word feasibility, privacy and battery evaluation
- [ ] Live2D renderer
- [ ] Validate Allium model and Cubism SDK compatibility
- [ ] Expressions, motion mapping and lip-sync

## Native delivery
- [ ] Native Android host and interface
- [ ] Android device-capability adapters
- [ ] Background-execution and permission review
- [ ] End-to-end tests on the target phone
- [ ] APK packaging, signing, upgrade and data-preservation tests

See [the OpenJarvis integration audit](OPENJARVIS_INTEGRATION.md), [the broader open-source integration audit](OPEN_SOURCE_INTEGRATION_AUDIT.md), [the runtime/tool/persistence integration note](AGENT_RUNTIME_INTEGRATION.md), [MCP integration](MCP_INTEGRATION.md), [local inference](LOCAL_INFERENCE.md), and [semantic memory](SEMANTIC_MEMORY.md).
