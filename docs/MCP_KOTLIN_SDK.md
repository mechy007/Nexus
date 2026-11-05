# Official Kotlin MCP SDK connector

NEXUS now has a concrete Streamable HTTP connector backed by the official
`io.modelcontextprotocol:kotlin-sdk-client` library.

## Usage

Create `KotlinSdkMcpConnector`, then pass it to the existing
`McpConnectionManager`. The manager continues to apply configured tool
allowlists and registers every discovered remote tool as
`SkillTrustTier.UNREVIEWED`. Registration does not grant execution permission;
the AgentRuntime continues to deny unreviewed tools.

## Scope and limitations

- Implements the MCP Streamable HTTP transport only.
- Uses the SDK for initialization, tool discovery, invocation, and protocol framing.
- HTTP endpoint validation remains in `McpServerConfig`.
- Credentials/OAuth, STDIO process launch, resource/prompt browsing, and Android UI
  are not included in this change.
- The default HTTP client is CIO; hosts may inject a configured Ktor client factory.

Remote tool results are capped by the existing 32,000-character adapter limit.
