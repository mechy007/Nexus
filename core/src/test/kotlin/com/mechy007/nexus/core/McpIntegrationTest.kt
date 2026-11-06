package com.mechy007.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.coroutines.startCoroutine

class McpIntegrationTest {
    private class FakeSession : McpClientSession {
        var closed = false
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun listTools() = listOf(McpToolDefinition("search", "Search external sources"), McpToolDefinition("danger", "Potentially dangerous action"))
        override suspend fun callTool(name: String, arguments: Map<String, String>): McpCallResult {
            calls += name to arguments
            return McpCallResult(false, "untrusted remote result")
        }
        override fun close() { closed = true }
    }
    @Test fun rejectsMalformedOrOversizedToolSchemas() {
        assertFailsWith<IllegalArgumentException> {
            McpToolDefinition("search", "Search", inputSchemaJson = "{not-json")
        }
        assertFailsWith<IllegalArgumentException> {
            McpToolDefinition("search", "Search", inputSchemaJson = "[]")
        }
        assertFailsWith<IllegalArgumentException> {
            McpToolDefinition("search", "Search", inputSchemaJson = """{"type":"executable"}""")
        }
        assertFailsWith<IllegalArgumentException> {
            McpToolDefinition("search", "Search", inputSchemaJson = " ".repeat(McpToolDefinition.MAX_SCHEMA_CHARS + 1))
        }
    }

    @Test fun acceptsBoundedJsonSchemaTypesAndTypeUnions() {
        McpToolDefinition("search", "Search", inputSchemaJson =
            """{"type":"object","properties":{"query":{"type":"string"},"limit":{"type":["integer","null"]}}}"""
        )
    }

    @Test fun rejectsInsecureRemoteEndpoints() {
        assertFailsWith<IllegalArgumentException> { McpServerConfig("remote", "http://example.com/mcp") }
        assertFailsWith<IllegalArgumentException> { McpServerConfig("remote", "https://user:pass@example.com/mcp") }
        assertTrue(McpServerConfig("local", "http://127.0.0.1:8080/mcp").enabled)
    }
    @Test fun exposesAllowlistedToolsAsUnreviewedAndDisconnectsCleanly() = runSuspend {
        val session = FakeSession()
        val registry = ToolRegistry()
        val manager = McpConnectionManager(McpConnector { session }, registry)
        val ids = manager.connect(McpServerConfig("docs", "https://mcp.example.com/mcp", allowedToolNames = setOf("search")))
        assertEquals(listOf(ToolId("mcp:docs:search")), ids)
        assertEquals(SkillTrustTier.UNREVIEWED, registry.find(ids.single())?.trustTier)
        assertTrue(registry.find(ToolId("mcp:docs:danger")) == null)
        assertTrue(manager.disconnect("docs"))
        assertTrue(session.closed)
        assertTrue(registry.all().isEmpty())
    }
    @Test fun failedConnectionClosesSessionAndReleasesServerReservation() = runSuspend {
        var attempts = 0
        var failedSessionClosed = false
        val workingSession = FakeSession()
        val connector = McpConnector {
            attempts++
            if (attempts == 1) {
                object : McpClientSession {
                    override suspend fun listTools(): List<McpToolDefinition> {
                        error("remote listing failed")
                    }
                    override suspend fun callTool(
                        name: String,
                        arguments: Map<String, String>
                    ) = McpCallResult(false, "")
                    override fun close() { failedSessionClosed = true }
                }
            } else {
                workingSession
            }
        }
        val registry = ToolRegistry()
        val manager = McpConnectionManager(connector, registry)
        val config = McpServerConfig("retryable", "https://mcp.example.com/mcp")

        assertFailsWith<IllegalStateException> { manager.connect(config) }
        assertTrue(failedSessionClosed)
        assertTrue(registry.all().isEmpty())
        assertTrue(manager.connectedServers().isEmpty())

        assertEquals(listOf(ToolId("mcp:retryable:search"), ToolId("mcp:retryable:danger")),
            manager.connect(config))
        assertTrue(manager.disconnect("retryable"))
    }

    @Test fun rejectsConcurrentConnectForSameServerId() {
        val connectorEntered = java.util.concurrent.CountDownLatch(1)
        val releaseConnector = java.util.concurrent.CountDownLatch(1)
        val session = FakeSession()
        val manager = McpConnectionManager(
            McpConnector {
                connectorEntered.countDown()
                check(releaseConnector.await(5, java.util.concurrent.TimeUnit.SECONDS))
                session
            },
            ToolRegistry()
        )
        val config = McpServerConfig("concurrent", "https://mcp.example.com/mcp")
        var firstResult: Result<List<ToolId>>? = null
        val thread = Thread {
            connectOnThread(manager, config) { firstResult = it }
        }
        thread.start()
        assertTrue(connectorEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertFailsWith<IllegalArgumentException> { runSuspend { manager.connect(config) } }
        releaseConnector.countDown()
        thread.join(5_000)
        assertTrue(!thread.isAlive)
        assertEquals(
            listOf(ToolId("mcp:concurrent:search"), ToolId("mcp:concurrent:danger")),
            requireNotNull(firstResult).getOrThrow()
        )
        assertTrue(manager.disconnect("concurrent"))
    }

    private fun connectOnThread(
        manager: McpConnectionManager,
        config: McpServerConfig,
        onComplete: (Result<List<ToolId>>) -> Unit
    ) {
        val connection: suspend () -> List<ToolId> = { manager.connect(config) }
        connection.startCoroutine(object : kotlin.coroutines.Continuation<List<ToolId>> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<List<ToolId>>) = onComplete(result)
        })
    }

    @Test fun remoteAdapterPassesArguments() = runSuspend {
        val session = FakeSession()
        val adapter = McpRemoteToolAdapter("docs", McpToolDefinition("search", "Search"), session)
        val result = adapter.execute(ToolRequest(toolId = adapter.id, requestedBy = AgentId("lara"),
            sessionId = SessionId("s"), arguments = mapOf("query" to "Kotlin")))
        assertTrue(result.success)
        assertEquals("untrusted remote result", result.output)
        assertEquals("Kotlin", session.calls.single().second["query"])
    }
}
private fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    val latch = java.util.concurrent.CountDownLatch(1)
    block.startCoroutine(object : kotlin.coroutines.Continuation<T> {
        override val context = kotlin.coroutines.EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) { result = value; latch.countDown() }
    })
    check(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
    return requireNotNull(result).getOrThrow()
}
