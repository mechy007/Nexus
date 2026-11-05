package com.mechy007.nexus.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Production Streamable HTTP connector backed by the official Kotlin MCP SDK.
 *
 * The core bridge continues to apply allowlists and UNREVIEWED trust by default.
 * Authentication/header injection is intentionally left to a future credential
 * provider rather than accepting secrets in endpoint URLs.
 */
class KotlinSdkMcpConnector(
    private val httpClientFactory: () -> HttpClient = {
        HttpClient(CIO) { install(SSE) }
    },
    private val clientName: String = "nexus",
    private val clientVersion: String = "0.1.0"
) : McpConnector {
    override suspend fun connect(config: McpServerConfig): McpClientSession {
        require(config.enabled) { "MCP server is disabled." }
        val httpClient = httpClientFactory()
        val client = Client(Implementation(name = clientName, version = clientVersion))
        try {
            client.connect(StreamableHttpClientTransport(httpClient, config.endpoint))
            return KotlinSdkMcpClientSession(client, httpClient)
        } catch (error: Exception) {
            httpClient.close()
            throw error
        }
    }
}

private class KotlinSdkMcpClientSession(
    private val client: Client,
    private val httpClient: HttpClient
) : McpClientSession {
    override suspend fun listTools(): List<McpToolDefinition> =
        client.listTools().tools.map { tool ->
            McpToolDefinition(
                name = tool.name,
                description = tool.description?.takeIf { it.isNotBlank() } ?: "Remote MCP tool",
                inputSchemaJson = Json.encodeToString(tool.inputSchema)
            )
        }

    override suspend fun callTool(name: String, arguments: Map<String, String>): McpCallResult {
        val result = client.callTool(name, arguments)
        val output = result.content.joinToString("\n") { block ->
            if (block is TextContent) block.text else block.toString()
        }.take(McpRemoteToolAdapter.MAX_OUTPUT_CHARS)
        return McpCallResult(result.isError == true, output)
    }

    override fun close() {
        try {
            runBlocking { client.close() }
        } finally {
            httpClient.close()
        }
    }
}
