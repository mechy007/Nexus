# MCP integration boundary

NEXUS maps remote MCP tools into its existing ToolAdapter registry. The bridge is transport-agnostic: an implementation of `McpConnector` wraps the official Kotlin MCP SDK client and its Streamable HTTP transport.

## Security defaults
- Remote endpoints require HTTPS. Plain HTTP is allowed only for loopback development endpoints.
- Credentials are not accepted in endpoint URLs or persisted in configuration; the host injects them through its connector.
- Tool names can be allowlisted per server.
- Discovered tools enter the registry as `UNREVIEWED`; they cannot execute until reviewed and explicitly re-registered with an approved trust tier.
- NEXUS AgentRuntime remains responsible for capability checks, user permission checks, loop protection, audit, and trace recording.
- Remote output is capped and remains untrusted model context, never trusted instructions.
- Disconnect removes the server's tools from the registry and closes the transport session.

## Scope
This PR adds the core bridge and fake-session tests. It does not yet ship a concrete SDK network connector, OAuth flow, STDIO process launcher, resources/prompts browser, or server management UI. These belong in the optional MCP adapter/Android host, where transport lifecycle and credential storage can be tested against real endpoints.
