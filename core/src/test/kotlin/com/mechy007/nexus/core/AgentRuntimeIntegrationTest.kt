package com.mechy007.nexus.core

import java.nio.file.Path
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class AgentRuntimeIntegrationTest {
    @TempDir lateinit var tempDir: Path

    private class Agents(private val profile: AgentProfile) : AgentRegistry {
        override fun all() = listOf(profile)
        override fun find(id: AgentId) = profile.takeIf { it.id == id }
        override fun register(profile: AgentProfile) = Unit
    }
    private class Sessions : SessionStore {
        private val data = linkedMapOf<SessionId, NexusSession>()
        override suspend fun get(id: SessionId) = data[id]
        override suspend fun save(session: NexusSession) { data[session.id] = session }
        override suspend fun delete(id: SessionId) { data.remove(id) }
        override suspend fun list() = data.values.toList()
    }
    private class Permissions(private val result: GrantDecision) : PermissionManager {
        override suspend fun check(agentId: AgentId, toolId: ToolId) = false
        override suspend fun request(agentId: AgentId, toolId: ToolId, reason: String) = result
    }
    private class Audit : AuditSink {
        val events = mutableListOf<AuditEvent>()
        override suspend fun record(event: AuditEvent) { events += event }
    }
    private class EchoTool : ToolAdapter {
        override val id = ToolId("echo")
        override val description = "Echo input"
        override suspend fun execute(request: ToolRequest) = ToolResult(true, request.arguments["value"].orEmpty())
    }

    @Test fun completesReplyAndPersistsTrace() = runSuspend {
        val traces = InMemoryTraceStore()
        val profile = AgentProfile(AgentId("lara"), "Lara", "test", "You are Lara.")
        val runtime = AgentRuntime(Agents(profile), AgentDecisionEngine { AgentDecision.Reply("Hello") },
            Sessions(), ToolRegistry(), Permissions(GrantDecision.GRANTED), Audit(), traces)
        val result = runtime.run(SessionId("s1"), AgentId("lara"), "Hi")
        assertEquals(AgentTurnStatus.COMPLETED, result.status)
        assertEquals("Hello", result.reply)
        assertEquals(1, traces.recent().size)
    }

    @Test fun executesToolAndFeedsResultIntoNextDecision() = runSuspend {
        var decisions = 0
        val profile = AgentProfile(AgentId("lara"), "Lara", "test", "prompt")
        val registry = ToolRegistry().apply { register(ToolRegistration(EchoTool(), setOf("test:echo"))) }
        val engine = AgentDecisionEngine { context ->
            decisions++
            if (decisions == 1) AgentDecision.CallTool(ToolRequest(
                toolId = ToolId("echo"), requestedBy = context.agent.id,
                sessionId = context.session.id, arguments = mapOf("value" to "tool-result")
            )) else {
                assertTrue(context.session.messages.any { it.role == MessageRole.TOOL && it.content == "tool-result" })
                AgentDecision.Reply("Done")
            }
        }
        val result = AgentRuntime(Agents(profile), engine, Sessions(), registry,
            Permissions(GrantDecision.GRANTED), Audit(), InMemoryTraceStore())
            .run(SessionId("s2"), AgentId("lara"), "Use echo")
        assertEquals(AgentTurnStatus.COMPLETED, result.status)
        assertEquals("Done", result.reply)
    }

    @Test fun waitsForApprovalWithoutExecutingTool() = runSuspend {
        var executed = false
        val tool = object : ToolAdapter {
            override val id = ToolId("danger")
            override val description = "Dangerous action"
            override suspend fun execute(request: ToolRequest): ToolResult {
                executed = true
                return ToolResult(true)
            }
        }
        val profile = AgentProfile(AgentId("lara"), "Lara", "test", "prompt")
        val registry = ToolRegistry().apply { register(ToolRegistration(tool, setOf("device:control"))) }
        val engine = AgentDecisionEngine { c -> AgentDecision.CallTool(ToolRequest(
            toolId = tool.id, requestedBy = c.agent.id, sessionId = c.session.id
        )) }
        val result = AgentRuntime(Agents(profile), engine, Sessions(), registry,
            Permissions(GrantDecision.REQUIRES_USER_ACTION), Audit(), InMemoryTraceStore())
            .run(SessionId("s3"), AgentId("lara"), "Run action")
        assertEquals(AgentTurnStatus.AWAITING_APPROVAL, result.status)
        assertNotNull(result.pendingTool)
        assertEquals(false, executed)
    }

    @Test fun deniesUnreviewedToolsAndRejectsMismatchedSession() = runSuspend {
        val profile = AgentProfile(AgentId("lara"), "Lara", "test", "prompt")
        val registry = ToolRegistry().apply {
            register(ToolRegistration(EchoTool(), setOf("test:echo"), SkillTrustTier.UNREVIEWED))
        }
        val engine = AgentDecisionEngine { c -> AgentDecision.CallTool(ToolRequest(
            toolId = ToolId("echo"), requestedBy = c.agent.id, sessionId = c.session.id
        )) }
        val result = AgentRuntime(Agents(profile), engine, Sessions(), registry,
            Permissions(GrantDecision.GRANTED), Audit(), InMemoryTraceStore())
            .run(SessionId("s4"), AgentId("lara"), "Run")
        assertEquals(AgentTurnStatus.FAILED, result.status)
        assertEquals("Unreviewed tools cannot execute.", result.failureReason)
    }

    @Test fun retrievesMemoryOnceAndReusesItAcrossAgentSteps() = runSuspend {
        var memorySearches = 0
        val memoryStore = object : MemoryStore {
            override suspend fun search(query: String, limit: Int): List<MemoryRecord> {
                memorySearches++
                return listOf(MemoryRecord(id = "memory-1", text = "remembered context"))
            }
            override suspend fun put(record: MemoryRecord) = Unit
            override suspend fun delete(id: String) = Unit
            override suspend fun exportAll() = emptyList<MemoryRecord>()
        }
        var decisions = 0
        val profile = AgentProfile(AgentId("lara"), "Lara", "test", "prompt")
        val registry = ToolRegistry().apply { register(ToolRegistration(EchoTool())) }
        val engine = AgentDecisionEngine { context ->
            decisions++
            assertEquals("remembered context", context.relevantMemories.single().text)
            if (decisions <= 2) AgentDecision.CallTool(ToolRequest(
                toolId = ToolId("echo"), requestedBy = context.agent.id,
                sessionId = context.session.id, arguments = mapOf("value" to "step-$decisions")
            )) else AgentDecision.Reply("Finished")
        }
        val result = AgentRuntime(Agents(profile), engine, Sessions(), registry,
            Permissions(GrantDecision.GRANTED), Audit(), InMemoryTraceStore(), memoryStore)
            .run(SessionId("memory-context"), AgentId("lara"), "Use memory")
        assertEquals(AgentTurnStatus.COMPLETED, result.status)
        assertEquals(3, decisions)
        assertEquals(1, memorySearches)
    }

    @Test fun sessionStoreSurvivesRestartAndDeletes() = runSuspend {
        val dir = tempDir.resolve("sessions").toFile()
        val store = JsonFileSessionStore(dir)
        val session = NexusSession(
            id = SessionId("saved-session"),
            messages = listOf(NexusMessage(role = MessageRole.ASSISTANT, content = "saved",
                agentId = AgentId("lara"), metadata = mapOf("providerId" to "mock")))
        )
        store.save(session)
        val reopened = JsonFileSessionStore(dir)
        assertEquals(session, reopened.get(session.id))
        assertEquals(listOf(session), reopened.list())
        reopened.delete(session.id)
        assertNull(reopened.get(session.id))
    }

    @Test fun traceStoreRetentionKeepsNewestRecords() = runSuspend {
        val file = tempDir.resolve("bounded-traces.json").toFile()
        val store = JsonFileTraceStore(file, maxRecords = 2)
        val first = AgentTrace(
            id = "trace-1", sessionId = SessionId("s"), agentId = AgentId("lara"),
            startedAt = java.time.Instant.parse("2026-01-01T00:00:00Z")
        )
        val second = AgentTrace(
            id = "trace-2", sessionId = SessionId("s"), agentId = AgentId("lara"),
            startedAt = java.time.Instant.parse("2026-01-02T00:00:00Z")
        )
        val third = AgentTrace(
            id = "trace-3", sessionId = SessionId("s"), agentId = AgentId("lara"),
            startedAt = java.time.Instant.parse("2026-01-03T00:00:00Z")
        )
        store.save(first)
        store.save(second)
        store.save(third)
        assertNull(store.find(first.id))
        assertEquals(listOf(third, second), store.recent())
        assertEquals(2, store.recent(limit = 10).size)
    }

    @Test fun traceStoreSurvivesRestartAndDeletes() = runSuspend {
        val file = tempDir.resolve("traces.json").toFile()
        val trace = AgentTrace(sessionId = SessionId("s"), agentId = AgentId("lara"),
            steps = listOf(TraceStep(kind = TraceStepKind.TOOL_CALL, toolId = ToolId("echo"), detail = "success")),
            succeeded = true)
        val store = JsonFileTraceStore(file)
        store.save(trace)
        val reopened = JsonFileTraceStore(file)
        assertEquals(trace, reopened.find(trace.id))
        assertEquals(listOf(trace), reopened.recent())
        reopened.delete(trace.id)
        assertNull(reopened.find(trace.id))
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    val result = AtomicReference<Result<T>>()
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) { result.set(value); latch.countDown() }
    })
    check(latch.await(5, TimeUnit.SECONDS)) { "Test timed out." }
    return result.get().getOrThrow()
}
