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
- [x] Runtime environment-based API-key resolution
- [ ] Provider health checks and configurable routing policies
- [ ] Streaming, request timeouts, and retry/backoff policies
- [ ] Context budgeting and summarization
- [ ] Structured tool-call planning and validation

## Memory and observability
- [x] Platform-independent agent trace model and replaceable trace-store contract
- [ ] Durable trace adapter and user-visible trace inspection/deletion
- [ ] Local database implementation for sessions and memory
- [ ] Session persistence and migrations
- [ ] Searchable long-term memory
- [ ] User review, export, and deletion

## Skills and tools
- [x] Declarative skill manifests and capability/trust policy
- [x] Per-turn repeated-call, ping-pong and call-budget guard
- [ ] Permission manager and user-confirmation flow
- [ ] Permission-gated skill/tool executor
- [ ] Tool registry and audit log
- [ ] Search and file connectors
- [ ] Automation connector
- [ ] Execution history and cancellation

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

See [the OpenJarvis integration audit](OPENJARVIS_INTEGRATION.md) for upstream source selection and adaptation notes.
