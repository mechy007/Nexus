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
