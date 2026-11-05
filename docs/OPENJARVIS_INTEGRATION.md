# OpenJarvis integration audit

Reviewed upstream: [open-jarvis/OpenJarvis](https://github.com/open-jarvis/OpenJarvis)
Licence: Apache-2.0; see [LICENSE-OPENJARVIS](../LICENSE-OPENJARVIS).

## Selected adaptations

| Upstream source | NEXUS adaptation | Fit |
| --- | --- | --- |
| `src/openjarvis/traces/collector.py`, trace store | `AgentTraces.kt` | Trace lifecycle, operational metrics and replaceable storage contract. |
| `src/openjarvis/skills/types.py` | `Skills.kt` | Declarative reusable skills and explicit capability requirements. |
| `src/openjarvis/skills/security.py` | `Skills.kt` | Provenance/trust tiers and least-privilege checks. |
| `rust/crates/openjarvis-agents/src/loop_guard.rs` | `AgentLoopGuard.kt` | Repeated-call, ping-pong and runaway-call protection. |

The Kotlin components adapt the upstream designs/algorithms rather than copying
Python or Rust modules verbatim. Attribution and the upstream licence are retained.

## Deliberately not copied

- Python CLI, Python agent implementations, frontend and desktop shell: they do
  not fit NEXUS's platform-independent Kotlin/JVM core.
- Rust inference backends: NEXUS already has provider contracts and HTTP
  adapters; introducing a second core language adds packaging complexity.
- Hardware-specific energy telemetry: it is not portable to every host and is
  not needed for this core milestone.
- OpenJarvis memory implementation: NEXUS already defines a `MemoryStore` port.
  A durable adapter should be implemented against that contract with explicit
  inspection, export, deletion and migration tests.
- Full skill execution/import: manifests are metadata only in this PR. No skill
  can execute a tool until a permission-gated executor and confirmation flow exist.

## Limitations

- `InMemoryTraceStore` is for tests/development only; it is not durable.
- No model, Ollama server, remote provider or external skill source is invoked.
- A future executor must instantiate a loop guard per turn and check it before
  every tool call.
- The host remains responsible for presenting and collecting permission decisions.
