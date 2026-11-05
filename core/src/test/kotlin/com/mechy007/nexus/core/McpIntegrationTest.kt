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
