# NEXUS

A modular, multi-agent personal AI system. **Build the engine first; add the native Android host later.**

NEXUS owns its agent runtime and intelligence lifecycle. AI providers, search services, browser backends, storage and device capabilities are replaceable adapters.

## Core principles
- Lara, JARVIS, FRIDAY, and EDITH are resident agents in one system—not separate apps.
- Agents share sessions and approved memory, but have distinct profiles and policies.
- Multi-model reasoning is bounded and used when task complexity justifies it.
- Hybrid routing checks capabilities, privacy, quality, latency, cost and provider health.
- Web research returns source links and treats page content as untrusted evidence.
- Automatic learning promotes only candidates that pass evaluation; security and permission policies are protected.
- Tools run only through explicit permission checks and are auditable.
- A capability is not considered working until its implementation is tested.

## AI capabilities
- Multi-model planner, solver, critic and synthesizer roles.
- Capability-first local/cloud model routing with cost and privacy constraints.
- Bounded conversation and memory context construction.
- Search and page-reading adapters, including Brave Search and an HTTPS page fetcher.
- Citation-bearing web research integrated into the AI request context.
- Evaluation-gated automatic learning with versioned configuration promotion.

## Project layout
- `core/` — platform-independent models, orchestration and adapter contracts.
- `docs/` — architecture, implementation and security notes.
- Future `adapters/` — durable storage, additional search providers, remote browser automation, voice and device integrations.
- Future `app-android/` — native Android host, intentionally deferred.

## Integration status
The core contains the orchestration and adapter implementations. A host must still configure real AI provider adapters and model metadata, resolve the Brave Search API key securely, supply durable learning/trace stores and candidate evaluators, and provide an isolated browser automation backend. No local model is bundled, and no Android UI or interactive browser automation backend is claimed as complete.

See [AI Intelligence Core](docs/AI_INTELLIGENCE_CORE.md), [Web Intelligence](docs/WEB_INTELLIGENCE.md), [Automatic Learning](docs/AUTOMATIC_LEARNING.md), [Architecture](docs/ARCHITECTURE.md), and [Security](docs/SECURITY.md).
