# Third-party notices

## OpenJarvis

NEXUS includes Kotlin adaptations of selected designs and algorithms from
[OpenJarvis](https://github.com/open-jarvis/OpenJarvis), Copyright 2025 The
OpenJarvis Authors.

Adapted areas:
- Agent trace records/store contract, informed by `src/openjarvis/traces/collector.py` and its trace storage design.
- Skill manifests, trust tiers and capability validation, informed by `src/openjarvis/skills/types.py` and `src/openjarvis/skills/security.py`.
- Repeated-call and alternating-tool loop detection, informed by `rust/crates/openjarvis-agents/src/loop_guard.rs`.

These are Kotlin adaptations, not verbatim copies. The upstream Apache License 2.0 text is included in [LICENSE-OPENJARVIS](LICENSE-OPENJARVIS).

Upstream project: https://github.com/open-jarvis/OpenJarvis
Upstream licence: Apache-2.0

## OpenAmika

NEXUS includes Kotlin adaptations of selected companion interaction patterns from
[OpenAmika](https://github.com/zeekk0/OpenAmika), Copyright (c) 2026 MekaHime Inc.

Adapted areas:
- Persona identity/context separation, informed by `docs/PERSONALITY.md`.
- Turn IDs, streamed response phases, protect-before-speech and interruption markers, informed by `brain/PROTOCOL.md`.
- Step-based recent-history selection and recall-intent cues, informed by `brain/f_bridge.py`.

These are Kotlin adaptations for NEXUS/Android, not verbatim copies of the OpenAmika application. The upstream MIT licence text is included in [LICENSE-OPENAMIKA](LICENSE-OPENAMIKA). OpenAmika's separate third-party dependencies, character assets, voice and Live2D model are not included.

Upstream project: https://github.com/zeekk0/OpenAmika
Upstream licence: MIT

## Additional upstream projects reviewed

The following projects were reviewed as architectural and testing references for NEXUS. No upstream source files from these projects are copied verbatim in the memory foundation change; the Kotlin implementation is NEXUS-authored.

- [Mem0](https://github.com/mem0ai/mem0) — Apache-2.0. Adapted memory scoping, lifecycle, and retrieval design concepts. No Python runtime or source file copied.
- [JetBrains Koog](https://github.com/JetBrains/koog) — Apache-2.0. Reviewed storage-provider, checkpoint, and history-compression patterns. No Koog runtime code copied.
- [LangChain4j](https://github.com/langchain4j/LangChain4j) — Apache-2.0. Reviewed JVM integration patterns. No source code copied.
- [AgentDojo](https://github.com/ethz-spylab/agentdojo) — MIT. Reviewed as a source of adversarial tool-use test scenarios. No source code copied.

See `docs/OPEN_SOURCE_INTEGRATION_AUDIT.md` for the selected upstream files, adaptation boundaries, and future candidates.
