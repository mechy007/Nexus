package com.mechy007.nexus.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StructuredJsonDecisionEngineTest {
    private class Router(private val output: String) : ProviderRouter {
        var lastRequest: GenerationRequest? = null
        override suspend fun generate(request: GenerationRequest): GenerationResponse {
            lastRequest = request
            return GenerationResponse(ProviderId("fake"), output)
        }
    }

    private class Echo : ToolAdapter {
        override val id = ToolId("echo")
        override val description = "Echo a value"
        override suspend fun execute(request: ToolRequest) = ToolResult(true)
    }

    private fun context() = AgentTurnContext(
        session = NexusSession(id = SessionId("session-1")),
        agent = AgentProfile(AgentId("lara"), "Lara", "assistant", "Be helpful.")
    )

    @Test fun parsesReplyDecision() = runSuspend {
        val router = Router("""{"type":"reply","text":"Hello"}""")
        val engine = StructuredJsonDecisionEngine(router, ToolRegistry())
        assertEquals(AgentDecision.Reply("Hello"), engine.decide(context()))
        assertEquals(ProviderCapabilities(text = true), router.lastRequest?.requiredCapabilities)
    }

    @Test fun parsesToolCallWithoutExecutingIt() = runSuspend {
        val registry = ToolRegistry().apply { register(ToolRegistration(Echo())) }
        val engine = StructuredJsonDecisionEngine(
            Router("""{"type":"tool_call","tool":"echo","arguments":{"value":"hello"}}"""),
            registry
        )
        val decision = engine.decide(context()) as AgentDecision.CallTool
        assertEquals(ToolId("echo"), decision.request.toolId)
        assertEquals(AgentId("lara"), decision.request.requestedBy)
        assertEquals(SessionId("session-1"), decision.request.sessionId)
        assertEquals(mapOf("value" to "hello"), decision.request.arguments)
    }

    @Test fun rejectsUnknownToolsAndNonStringArguments() = runSuspend {
        val registry = ToolRegistry().apply { register(ToolRegistration(Echo())) }
        val unknown = StructuredJsonDecisionEngine(
            Router("""{"type":"tool_call","tool":"missing","arguments":{}}"""), registry
        )
        assertFailsWith<IllegalArgumentException> { unknown.decide(context()) }
        val invalid = StructuredJsonDecisionEngine(
            Router("""{"type":"tool_call","tool":"echo","arguments":{"count":3}}"""), registry
        )
        assertFailsWith<IllegalArgumentException> { invalid.decide(context()) }
    }

    @Test fun rejectsMalformedAndOversizedResponses() = runSuspend {
        assertFailsWith<IllegalArgumentException> {
            StructuredJsonDecisionEngine(Router("not json"), ToolRegistry()).decide(context())
        }
        assertFailsWith<IllegalArgumentException> {
            StructuredJsonDecisionEngine(Router("""{"type":"reply","text":"too long"}"""),
                ToolRegistry(), maxResponseChars = 5).decide(context())
        }
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
