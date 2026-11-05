# Roadmap

The objective is to add broad capability coverage, then verify each feature. A roadmap checkbox means code exists; "working" additionally requires successful tests or an integration check.

## Foundation
- [x] Repository and module structure
- [x] Domain models for sessions, messages, agents, providers, memory, and tools
- [x] Ports for provider, storage, permission, tool, and audit adapters
- [x] Platform-independent coordinator boundary
- [ ] CI build and unit-test verification

## Intelligence
- [ ] Built-in Lara, JARVIS, FRIDAY, and EDITH profiles
- [ ] Provider registry and capability-aware routing
- [ ] Streaming, cancellation, timeouts, retries, and safe fallback
- [ ] Context budgeting and summarization
- [ ] Structured tool-call planning and validation

## Memory
- [ ] Local database implementation
- [ ] Session persistence and migrations
- [ ] Searchable long-term memory
- [ ] User review, export, and deletion

## Tools
- [ ] Permission manager and user-confirmation flow
- [ ] Tool registry and audit log
- [ ] Search and file connectors
- [ ] Automation connector
- [ ] Execution history and cancellation

## Voice and avatar
- [ ] Speech-to-text and text-to-speech adapters
- [ ] Wake-word feasibility, privacy, and battery evaluation
- [ ] Live2D renderer
- [ ] Validate Allium model and Cubism SDK compatibility
- [ ] Expressions, motion mapping, and lip-sync

## Native delivery
- [ ] Native Android host and interface
- [ ] Android device-capability adapters
- [ ] Background-execution and permission review
- [ ] End-to-end tests on the target phone
- [ ] APK packaging, signing, upgrade, and data-preservation tests
