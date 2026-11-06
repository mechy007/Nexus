# Structured agent decisions

`StructuredJsonDecisionEngine` connects the existing provider router to the NEXUS-owned `AgentRuntime` decision contract.

## Response contract

The provider must return exactly one JSON object:

- Reply: `{"type":"reply","text":"..."}`
- Tool proposal: `{"type":"tool_call","tool":"tool-name","arguments":{"key":"string-value"}}`

Only enabled registered tools are included in the prompt. Unknown tools, malformed JSON, non-string argument values, and oversized responses are rejected. The parser creates a `ToolRequest` using the active session and agent IDs; it does not execute tools.

## Security boundary

This adapter is not a permission grant. `AgentRuntime` still validates the request identity, trust tier, capabilities, approval state, loop budget, execution limits, and audit trail before a tool can run. Tool outputs return to the model through the existing runtime loop.

## Verification boundary

Unit tests use a fake provider router. They cover replies, tool proposals, request identity binding, unknown tools, invalid argument types, malformed JSON, and response size limits. No live provider credentials or actual tool execution are required.

## Context and resource guardrails

The decision adapter also bounds the context it sends to a provider:

- At most 64 enabled tools, 24 recent conversation messages, and 16,000 characters of saved memory by default.
- Conversation content, memory entries, and tool metadata are serialized as JSON data rather than interpolated as free-form instruction text.
- The prompt explicitly treats conversation and memory as untrusted data and is capped at 96,000 characters.
- Tool proposals are limited to 64 arguments, 128 characters per argument name, 16,384 characters per value, and 32,768 aggregate argument characters by default.
- Reply/tool decision objects reject unsupported top-level fields.

These limits are configurable through the adapter constructor. Exceeding a limit rejects the decision before provider invocation or tool execution; it does not silently truncate security-relevant context.
