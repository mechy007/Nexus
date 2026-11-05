# Tool execution guardrails

The AgentRuntime applies a configurable `ToolExecutionPolicy` immediately before
calling a registered adapter.

Defaults:
- 30-second execution timeout
- 64 arguments per request
- 128 characters per argument name
- 16,384 characters per argument value
- 32,768 characters total argument payload
- 32,000 characters returned output

Timeouts become a failed tool result with `TOOL_TIMEOUT`. Coroutine cancellation
initiated by the caller is propagated. Input-limit violations are caught by the
runtime's existing tool exception boundary. Raw argument values are not written
to audit events.

These limits do not replace trust-tier checks, capability policy, permission
checks, or explicit user approval.
