package com.mechy007.nexus.core

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class McpServerConfig(
    val id: String,
    val endpoint: String,
    val enabled: Boolean = true,
    val allowedToolNames: Set<String>? = null,
    val requiredCapabilities: Set<String> = emptySet()
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) { "Invalid MCP server id." }
        val uri = URI(endpoint)
        require(uri.scheme == "https" || (uri.scheme == "http" && isLoopback(uri.host))) {
            "MCP endpoints must use HTTPS; HTTP is allowed only for loopback."
        }
        require(uri.host != null && uri.userInfo == null && uri.fragment == null) {
            "MCP endpoint must be an absolute URL without embedded credentials or fragments."
        }
        require(requiredCapabilities.none(String::isBlank))
        require(allowedToolNames == null || allowedToolNames.none(String::isBlank))
    }
    private fun isLoopback(host: String?): Boolean =
        host.equals("localhost", true) || host == "127.0.0.1" || host == "::1"
}

data class McpToolDefinition(val name: String, val description: String, val inputSchemaJson: String = "{}") {
    init {
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")))
        require(description.isNotBlank())
    }
}
data class McpCallResult(val isError: Boolean, val text: String)
interface McpClientSession : AutoCloseable {
    suspend fun listTools(): List<McpToolDefinition>
    suspend fun callTool(name: String, arguments: Map<String, String>): McpCallResult
}
fun interface McpConnector { suspend fun connect(config: McpServerConfig): McpClientSession }

class McpRemoteToolAdapter(
    private val serverId: String,
    private val definition: McpToolDefinition,
    private val session: McpClientSession
) : ToolAdapter {
    override val id = ToolId("mcp:$serverId:${definition.name}")
    override val description = "MCP [$serverId] ${definition.description}"

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (request.toolId != id) return ToolResult(false, errorCode = "MCP_TOOL_ID_MISMATCH")
        return try {
            val result = session.callTool(definition.name, request.arguments)
            ToolResult(!result.isError, result.text.take(MAX_OUTPUT_CHARS),
                if (result.isError) "MCP_REMOTE_ERROR" else null)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ToolResult(false, errorCode = "MCP_TRANSPORT_ERROR")
        }
    }
    companion object { const val MAX_OUTPUT_CHARS = 32_000 }
}

class McpConnectionManager(
    private val connector: McpConnector,
    private val registry: ToolRegistry
) : AutoCloseable {
    private data class Active(val session: McpClientSession, val toolIds: Set<ToolId>)
    private val active = ConcurrentHashMap<String, Active>()

    suspend fun connect(config: McpServerConfig): List<ToolId> {
        require(config.enabled) { "MCP server is disabled." }
        require(!active.containsKey(config.id)) { "MCP server is already connected." }
        val session = connector.connect(config)
        try {
            val definitions = session.listTools().filter {
                config.allowedToolNames == null || it.name in config.allowedToolNames
            }
            val adapters = definitions.map { McpRemoteToolAdapter(config.id, it, session) }
            adapters.forEach { adapter ->
                registry.register(ToolRegistration(adapter, config.requiredCapabilities, SkillTrustTier.UNREVIEWED))
            }
            val connected = Active(session, adapters.map { it.id }.toSet())
            if (active.putIfAbsent(config.id, connected) != null) {
                adapters.forEach { registry.remove(it.id) }
                error("MCP server is already connected.")
            }
            return connected.toolIds.toList()
        } catch (error: Exception) {
            session.close()
            throw error
        }
    }
    fun disconnect(serverId: String): Boolean {
        val connection = active.remove(serverId) ?: return false
        connection.toolIds.forEach(registry::remove)
        connection.session.close()
        return true
    }
    fun connectedServers(): Set<String> = active.keys.toSet()
    override fun close() { active.keys.toList().forEach(::disconnect) }
}
