# NEXUS Upstream Capability Integration Roadmap

**Status:** Audit baseline and implementation plan  
**Target:** NEXUS Android + platform-independent Kotlin core  
**Upstream repositories:** OpenJarvis, AIRI, OpenAmika  
**Selection rule:** Integrate capabilities/subsystems assessed as Medium, Medium-High, High, or Very High priority. Exclude Low and Very Low priority items.

## Non-negotiable architecture rules

1. NEXUS remains its own product and owns its runtime, agent lifecycle, permissions, data, UI, and release process.
2. Integrate capabilities and reusable designs, not upstream application shells or entire desktop/server stacks.
3. Preserve Allium as NEXUS's identity and companion; upstream projects contribute capabilities, not alternate resident identities.
4. Prefer native Kotlin/JVM or Android implementations behind NEXUS-owned interfaces. A remote service or embedded runtime is not assumed to be Android-compatible merely because its source is open.
5. Keep integrations modular, replaceable, testable, and independently disableable.
6. Preserve upstream license notices and audit every dependency and asset separately. Code licenses do not automatically grant character, voice, model, or dataset rights.
7. Do not claim a capability is working until its implementation and tests demonstrate it. Device-only behavior also needs real-device verification.
8. Work on isolated PR branches. Do not merge without explicit user approval.

## Repository audit: current NEXUS baseline

The current `main` tree already includes substantial groundwork:

- Agent orchestration and bounded tool execution: `AgentRuntime`, `ToolRegistry`, `AgentLoopGuard`, and `ToolExecutionPolicy`.
- Provider abstraction, capability-aware model routing, health tracking, timeouts, and fallback: `Ports`, `ProviderRouting`, and `IntelligentModelRouting`.
- Multi-model reasoning and request understanding: `AIIntelligence`, `MultiModelReasoning`, and `StructuredJsonDecisionEngine`.
- Conversation context, semantic memory, JSON-backed stores, and memory lifecycle.
- Agent traces and an evaluation-gated learning foundation.
- Skills metadata and trust policy, MCP connector contracts, web research, and page-fetching policy.
- Allium companion state, persona, conversation protocol, and history-window foundations.

Important gaps visible in the current source:
- Some stores are explicitly in-memory or JSON-file implementations; durable Android storage and migrations need verification.
- Learning has a candidate/evaluator contract, but durable configuration, meaningful evaluation datasets, and runtime feedback wiring need verification.
- Skill metadata/trust checks exist, but a full signed catalog, skill package lifecycle, and robust schema validation are not yet established by the inspected code.
- MCP tools are registered as unreviewed by default; safe review/approval and capability-grant UX need end-to-end verification.
- A real sandboxed execution backend is not present in the inspected Android/Kotlin core.
- Scheduled/background operatives, notification delivery, and lifecycle-safe Android scheduling need implementation/verification.
- Android hardware-aware local inference discovery and automatic model installation are not established by the inspected files.

## Priority policy

Priority is assigned to a capability based on its contribution to a dependable personal assistant, Android feasibility, user value, safety, and fit with NEXUS. A feature is not promoted merely because it exists upstream.

- **Very High:** foundational runtime, reliable inference, agent orchestration, memory, safety/permissions, voice interaction, core companion presence.
- **High:** multi-model routing, tool/MCP ecosystem, durable traces, task scheduling, interruption handling, speech pipeline, Live2D state synchronization.
- **Medium-High:** skill catalog and lifecycle, retrieval improvements, observability/telemetry, proactive reminders/operatives, expressive animation/audio controls.
- **Medium:** optional connectors, quality-of-life controls, configurable companion behaviors, additional supported input/output modalities.
- **Low / Very Low:** desktop-only shells, web dashboard-only features, upstream-specific installers, development-only scripts, redundant features, platform-bound integrations with no viable Android adapter, and assets without compatible redistribution rights.

## Workstream 1 — OpenJarvis first: intelligence and agent foundation

OpenJarvis is the first implementation source because its composable intelligence, engine, agent, memory, tool, trace, and learning architecture maps to NEXUS's core.

### OJ-1 — Capability and provider runtime parity (Very High)
- Audit all OpenJarvis engine interfaces, model catalog/discovery, health checks, streaming, cancellation, and fallback behavior.
- Compare each item with NEXUS `ProviderAdapter`, `ProviderRegistry`, `IntelligentModelRouter`, and `ResilientProviderRouter`.
- Implement only missing contracts and Android-feasible adapters in Kotlin.
- Keep provider credentials in Android-secure storage; never copy upstream environment-file patterns into the app.
- Acceptance: deterministic routing tests, unavailable-provider fallback tests, cancellation/timeout tests, and no credential leakage in logs.

### OJ-2 — Agent orchestration and tool execution (Very High)
- Audit OpenJarvis orchestrator/ReAct/operative patterns against NEXUS `AgentRuntime`.
- Extend the existing runtime for robust multi-step plans, tool result handling, structured decisions, stop conditions, and resumable work where appropriate.
- Retain NEXUS permission checks, loop guards, audit events, and explicit approval for sensitive actions.
- Do not import arbitrary shell/code execution into the Android app.
- Acceptance: multi-step integration tests, denial tests, loop/ping-pong tests, cancellation tests, and trace completeness.

### OJ-3 — Persistent memory and retrieval (Very High)
- Compare SQLite/FTS5, BM25, hybrid retrieval, ingestion/chunking, and context attribution with NEXUS memory interfaces.
- Add a durable Android-compatible store (Room/SQLite or a justified equivalent), migrations, retention controls, and export/delete behavior.
- Add lexical + semantic/hybrid retrieval only where embedding runtime and device budget make it viable.
- Treat retrieved content as untrusted data and preserve source attribution.
- Acceptance: persistence across process restart, migration tests, deletion/retention tests, retrieval relevance tests, and bounded-context tests.

### OJ-4 — Traces, telemetry, and evaluation (High)
- Extend existing `AgentTrace` with privacy-safe operational metrics: route choice, provider latency, token/cost estimates when available, tool outcomes, and failure categories.
- Add durable trace/telemetry storage and user-visible diagnostics with opt-in controls.
- Never persist raw prompts, secrets, or tool arguments by default.
- Acceptance: durable trace round-trip, redaction tests, opt-in behavior, and metrics correctness.

### OJ-5 — Skills and tool ecosystem (High / Medium-High)
- Audit OpenJarvis skill metadata, discovery, import, versioning, and invocation flow.
- Build a NEXUS-owned manifest format compatible with useful open standards; validate schemas and provenance.
- Keep third-party skills disabled until reviewed, signed/verified where possible, and granted the minimum required capabilities.
- Add tool adapters for high-value Android-safe capabilities; defer host-shell and desktop-only tools.
- Acceptance: malformed/untrusted skill rejection, signature/provenance tests, least-privilege tests, and explicit user approval for dangerous capabilities.

### OJ-6 — Scheduling and long-running operatives (High)
- Adapt scheduled/monitor agent concepts to Android WorkManager/foreground-service rules, battery constraints, and user-visible notifications.
- Add pause/resume/cancel, execution history, retry policy, and permission-aware actions.
- Do not promise always-on execution when Android background restrictions prevent it.
- Acceptance: lifecycle/reboot behavior tests, cancellation, duplicate-work prevention, and notification permission handling.

### OJ-7 — Security, sandbox, and channels (High where Android-feasible)
- Audit OpenJarvis guardrails, audit logging, file policies, and sandbox boundaries.
- Strengthen NEXUS tool policy and audit coverage; use Android app sandbox and scoped storage.
- Treat container-based sandboxing and Node/Python bridges as non-portable unless a safe, practical Android implementation is demonstrated.
- Evaluate messaging channels individually; use official APIs/intents and user consent, never unofficial protocol automation by default.
- Acceptance: security regression suite, path/URL policy tests, permission denial tests, and documented platform limitations.

## Workstream 2 — AIRI: real-time character interaction and multimodal I/O

After the OpenJarvis core foundation, audit AIRI's stage/character runtime, audio input/output pipeline, streaming speech, interruption/barge-in, lip-sync, emotion/expression mapping, and plugin boundaries.

Integrate Medium-to-Very-High capabilities through Android-native adapters:
- audio capture and playback with explicit runtime permissions and lifecycle handling;
- streaming speech state and interruption coordination;
- lip-sync viseme/volume signals mapped to Allium/Cubism parameters;
- emotion and expression events mapped to NEXUS-owned Allium state;
- reconnect/error states and configurable audio-device behavior.

Do not import AIRI's desktop/web shell, Electron dependencies, or unrelated development infrastructure. Character/model/voice assets require separate license and provenance review.

## Workstream 3 — OpenAmika: companion behavior and relationship layer

Audit OpenAmika's conversation protocol, persona/context assembly, memory/recall behavior, reflection, interruption handling, voice turn management, and companion state model.

Adapt qualifying capabilities into Allium without importing a second character identity:
- richer contextual persona assembly owned by Allium;
- reliable turn state, interruption and partial-response preservation;
- explicit, bounded memory/recall and user-controlled memory management;
- companion emotion/presence events that drive Allium's existing state reducer;
- configurable reflection/proactive behavior with transparent controls.

Do not import its Windows/Python/NVIDIA runtime, installer scripts, desktop assumptions, or character/voice assets without separate rights confirmation.

## Proposed PR sequence

| PR | Source | Scope | Priority |
|---|---|---|---|
| 1 | OpenJarvis | Provider/model discovery, routing gaps, engine adapter contracts | Very High |
| 2 | OpenJarvis | Agent orchestration parity and robust tool lifecycle | Very High |
| 3 | OpenJarvis | Durable memory, ingestion, hybrid retrieval | Very High |
| 4 | OpenJarvis | Trace persistence, telemetry, evaluation loop | High |
| 5 | OpenJarvis | Skills catalog, provenance, capability grants | High / Medium-High |
| 6 | OpenJarvis | Android-safe scheduler and monitor operatives | High |
| 7 | OpenJarvis | Security/guardrails audit and safe tool adapters | High |
| 8 | AIRI | Android audio streaming, interruption, lip-sync and expression adapters | Very High / High |
| 9 | AIRI | Stage/character interaction patterns and resilient lifecycle | High |
| 10 | OpenAmika | Allium companion protocol, memory/recall and contextual persona improvements | High / Medium-High |
| 11 | All three | Cross-project integration, duplication removal, end-to-end regression | High |

PRs may be split further when a subsystem is too broad to review or test safely. The sequence is a plan, not a promise that every upstream feature is technically portable.

## License and provenance

- OpenJarvis repository code is Apache-2.0. Preserve its license and applicable NOTICE/attribution requirements for any copied or adapted code.
- OpenAmika repository code is MIT. Preserve copyright and permission notices for copied or adapted code. Its README describes character, voice, and model assets under separate terms; those assets are not included by this roadmap.
- AIRI's repository license and per-asset/dependency terms must be checked against the exact files selected before copying or adapting them.
- Review transitive dependency licenses and Android compatibility before adding dependencies. Do not copy upstream binaries, model weights, voices, or artwork based only on the repository's code license.

## Current execution checkpoint

This PR records the audit and implementation sequence only. It does not claim that the planned capabilities have already been implemented. The first code PR should begin with OJ-1 and include tests against NEXUS's existing provider interfaces. The existing open Allium Live2D lifecycle fix remains separate and should not be mixed into upstream capability work.
