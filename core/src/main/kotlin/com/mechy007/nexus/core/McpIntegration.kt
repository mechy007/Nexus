package com.mechy007.nexus.core

import java.net.URI
import java.util.concurrent.CancellationException
import com.google.gson.JsonElement
import com.google.gson.JsonParser
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
        require(description.isNotBlank() && description.length <= MAX_DESCRIPTION_CHARS)
        require(inputSchemaJson.length <= MAX_SCHEMA_CHARS) { "MCP input schema is too large." }
        val schema = try {
            JsonParser.parseString(inputSchemaJson)
        } catch (_: Exception) {
            throw IllegalArgumentException("MCP input schema must be valid JSON.")
        }
        require(schema.isJsonObject) { "MCP input schema must be a JSON object." }
        require(isBoundedJson(schema, 0, intArrayOf(0))) {
            "MCP input schema exceeds structural limits."
        }
        val type = schema.asJsonObject.get("type")
        if (type != null) {
            val supported = setOf("object", "array", "string", "number", "integer", "boolean", "null")
            val types = when {
                type.isJsonPrimitive && type.asJsonPrimitive.isString -> listOf(type.asString)
                type.isJsonArray -> type.asJsonArray.map { element ->
                    require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                        "MCP schema type arrays must contain strings."
                    }
                    element.asString
                }
                else -> throw IllegalArgumentException("MCP schema type must be a string or string array.")
            }
            require(types.isNotEmpty() && types.all { it in supported }) {
                "MCP input schema contains an unsupported JSON type."
            }
        }
    }

    companion object {
        const val MAX_DESCRIPTION_CHARS = 4_096
        const val MAX_SCHEMA_CHARS = 65_536
        const val MAX_SCHEMA_DEPTH = 16
        const val MAX_SCHEMA_NODES = 2_048

        private fun isBoundedJson(element: JsonElement, depth: Int, nodes: IntArray): Boolean {
            nodes[0]++
            if (depth > MAX_SCHEMA_DEPTH || nodes[0] > MAX_SCHEMA_NODES) return false
            return when {
                element.isJsonObject -> element.asJsonObject.entrySet().all { (_, value) ->
                    isBoundedJson(value, depth + 1, nodes)
                }
                element.isJsonArray -> element.asJsonArray.all { value ->
                    isBoundedJson(value, depth + 1, nodes)
                }
                else -> true
            }
        }
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
        } catch (cancelled: CancellationException) {
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
    // Reserve the server ID before any suspending connector work. This prevents two
    // concurrent connect calls from registering the same remote tool IDs.
    private val connecting = ConcurrentHashMap.newKeySet<String>()

    suspend fun connect(config: McpServerConfig): List<ToolId> {
        require(config.enabled) { "MCP server is disabled." }
        require(connecting.add(config.id)) { "MCP server is already connected or connecting." }

        var session: McpClientSession? = null
        val registeredToolIds = linkedSetOf<ToolId>()
        try {
            require(!active.containsKey(config.id)) { "MCP server is already connected." }
            session = connector.connect(config)
            val listed = session.listTools()
            require(listed.size <= MAX_TOOLS_PER_SERVER) { "MCP server exposed too many tools." }
            val definitions = listed.filter {
                config.allowedToolNames == null || it.name in config.allowedToolNames
            }
            val adapters = definitions.map { McpRemoteToolAdapter(config.id, it, session) }
            adapters.forEach { adapter ->
                registry.register(ToolRegistration(adapter, config.requiredCapabilities, SkillTrustTier.UNREVIEWED))
                registeredToolIds += adapter.id
            }

            val connected = Active(session, registeredToolIds.toSet())
            check(active.putIfAbsent(config.id, connected) == null) {
                "MCP server is already connected."
            }
            return connected.toolIds.toList()
        } catch (failure: Throwable) {
            // A failed/cancelled connection must not leave callable tools behind.
            registeredToolIds.forEach(registry::remove)
            try {
                session?.close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        } finally {
            connecting.remove(config.id)
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

    companion object {
        const val MAX_TOOLS_PER_SERVER = 256
    }
}
