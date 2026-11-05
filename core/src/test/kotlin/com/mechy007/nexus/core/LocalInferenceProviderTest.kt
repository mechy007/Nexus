package com.mechy007.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.coroutines.startCoroutine

class LocalInferenceProviderTest {
    private class FakeEngine : LiteRtLmEngine {
        var initialized = false
        var closed = false
        var prompt: String? = null
        override suspend fun initialize(spec: LocalModelSpec) { initialized = true }
        override suspend fun generate(prompt: String, maxOutputTokens: Int): String {
            check(initialized && !closed)
            this.prompt = prompt
            return "local answer"
        }
        override fun close() { closed = true }
    }

    @Test fun requiresAbsoluteModelPath() {
        assertFailsWith<IllegalArgumentException> { LocalModelSpec("tiny", "models/tiny.litertlm") }
    }

    @Test fun initializesGeneratesAndClosesEngine() = runSuspend {
        val engine = FakeEngine()
        val provider = LiteRtLmProviderAdapter(LocalModelSpec("gemma-small", "/models/gemma.litertlm"), engine)
        assertFalse(provider.isReady())
        provider.initialize()
        val response = provider.generate(GenerationRequest(
            NexusSession(), AgentProfile(AgentId("lara"), "Lara", "assistant", "prompt"), "hello"
        ))
        assertEquals("local answer", response.text)
        assertEquals("hello", engine.prompt)
        assertTrue(provider.isReady())
        provider.close()
        assertTrue(engine.closed)
        assertFalse(provider.isReady())
    }

    @Test fun rejectsGenerationBeforeInitialization() = runSuspend {
        val provider = LiteRtLmProviderAdapter(LocalModelSpec("tiny", "/models/tiny.litertlm"), FakeEngine())
        assertFailsWith<IllegalStateException> {
            provider.generate(GenerationRequest(
                NexusSession(), AgentProfile(AgentId("lara"), "Lara", "assistant", "prompt"), "hello"
            ))
        }
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
