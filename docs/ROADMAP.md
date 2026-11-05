# Roadmap

A checkbox means code exists; "working" additionally requires successful tests or an integration check.

## AI Intelligence Core — current implementation
- [x] Request intent and complexity classification boundary
- [x] Bounded conversation and memory context construction
- [x] Multi-model planner, solver, critic and synthesizer orchestration
- [x] Capability-first hybrid model routing with local-only and cost constraints
- [x] Provider health integration, timeout handling and fallback
- [x] Structural response evaluation boundary
- [x] Automatic learning observations, candidate proposal contract and evaluation-gated promotion
- [x] Version-checked learning configuration store contract and in-memory development implementation
- [x] Search/page-reading orchestration and citation model
- [x] Brave Search adapter with runtime credential resolution
- [x] HTTPS page fetcher with redirect checks and private-address filtering
- [ ] Parallel specialist fan-out and task-specific collaboration benchmarks
- [ ] Durable learning observations/configuration, candidate generation and rollback adapter
- [ ] Domain-specific factual/code verification and calibrated evaluation datasets
- [ ] Target-device local inference integration and performance benchmarks
- [ ] Interactive browser automation backend in an isolated host with permission-gated actions

## Foundation
- [x] Repository and module structure
- [x] Domain models for sessions, messages, agents, providers, memory and tools
- [x] Ports for provider, storage, permission, tool and audit adapters
- [x] Platform-independent coordinator boundary
- [x] CI build and unit-test verification

## Intelligence and providers
- [x] Provider registry and capability-aware routing
- [x] Deterministic provider fallback and cancellation propagation
- [x] Concrete HTTP adapters for OpenAI, Gemini, OpenRouter and Groq (mock-transport tests; live credentials not verified)
- [x] Provider health, bounded retry/backoff and cooldown policies
- [x] NEXUS-owned agent execution loop with bounded steps and tool-decision boundary
- [x] Structured agent decision and tool request validation
- [ ] Provider-native structured tool-call planning and validation
- [ ] LiteRT-LM Android engine adapter and target-device benchmark

## Memory and observability
- [x] Platform-independent agent trace model and replaceable trace-store contract
- [x] Durable JSON trace adapter with inspection and deletion
- [x] Local JSON session persistence
- [x] Scoped local memory with export, search, expiry and deletion
- [x] Embedding-provider and vector-index contracts
- [x] Persistent cosine-similarity vector index and scoped semantic retrieval
- [x] Opt-in provider-backed memory extraction and re-index lifecycle
- [ ] Encrypted-at-rest storage and robust atomic re-index migration
- [ ] User review UI for memory and trace deletion

## Skills and tools
- [x] Declarative skill manifests and capability/trust policy
- [x] Per-turn repeated-call, ping-pong and call-budget guard
- [x] Unified native tool registry
- [x] Permission-gated tool execution and user-approval handoff
- [x] Tool execution audit and trace events
- [x] MCP remote-tool bridge contracts, allowlisting and unreviewed trust defaults
- [x] Official Kotlin MCP SDK Streamable HTTP connector
- [ ] MCP resources/prompts browser and server management UI
- [ ] Durable approval checkpoints and automatic task resumption
- [ ] Additional search, file and automation connectors

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

See [AI Intelligence Core](AI_INTELLIGENCE_CORE.md), [Web Intelligence](WEB_INTELLIGENCE.md), [Automatic Learning](AUTOMATIC_LEARNING.md), [the OpenJarvis integration audit](OPENJARVIS_INTEGRATION.md), [the broader open-source integration audit](OPEN_SOURCE_INTEGRATION_AUDIT.md), [MCP integration](MCP_INTEGRATION.md), [local inference](LOCAL_INFERENCE.md) and [semantic memory](SEMANTIC_MEMORY.md).
