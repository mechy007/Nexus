# Agent runtime, tools, and persistence integration

This milestone combines the next three planned changes into one reviewable pull request.

## NEXUS-owned runtime
- A model-facing `AgentDecisionEngine` proposes either a final reply or a structured tool request.
- `AgentRuntime` owns the loop, agent/session identity checks, step budget, permission decisions, tool execution, session updates, and trace creation.
- Existing `ProviderRouter`, `AgentLoopGuard`, `SkillPolicy`, `PermissionManager`, `SessionStore`, and `TraceStore` contracts remain authoritative.
- `TextOnlyDecisionEngine` connects existing text-only providers as a safe first runtime adapter. It does not claim provider-native function calling.

## Unified tool registry
- `ToolRegistry` supports native and future external adapters through the existing `ToolAdapter` contract.
- Registration does not grant permission.
- Unreviewed tools are denied; capability declarations are checked; the permission manager remains the final execution gate.
- Tool requests must match the active agent and session.
- Every execution decision is audited and represented in privacy-conscious traces.
- MCP transport and server discovery are deliberately not included in this milestone.

## Persistence
- `JsonFileSessionStore` persists sessions and message metadata using a versioned JSON format and atomic replacement.
- `JsonFileTraceStore` persists operational trace metadata using a versioned JSON format and atomic replacement.
- Both are local-file adapters; Android hosts must supply app-private storage locations.
- Session persistence survives process restarts, but in-flight tool execution is not automatically resumed. Durable approval checkpoints and resume orchestration remain follow-up work.

## Open-source references
- OpenJarvis: existing NEXUS adaptations for traces, skill trust, and loop protection.
- JetBrains Koog: persistence-provider and checkpoint design patterns; no Koog runtime dependency.
- Android Agent Runtime / Google ADK: agent lifecycle and tool-boundary research; no competing runtime dependency.
- MCP Kotlin SDK: future protocol adapter candidate; no MCP transport dependency in this milestone.

## Verification boundary
CI must pass before this milestone is considered verified. Unit tests use fake agents, tools, permission managers, and local temporary files. No live model calls, credentials, Android APIs, or external MCP servers are required.
