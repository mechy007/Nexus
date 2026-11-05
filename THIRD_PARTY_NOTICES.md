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
