package com.mechy007.nexus.core

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResilientProviderRouterTest {
    private class Registry(vararg adapters: ProviderAdapter) : ProviderRegistry {
        private val values = adapters.associateBy { it.profile.id }.toMutableMap()
        override fun all() = values.values.toList()
        override fun find(id: ProviderId) = values[id]
        override fun register(adapter: ProviderAdapter) { values[adapter.profile.id] = adapter }
        override fun remove(id: ProviderId) = values.remove(id) != null
    }

    private class FakeProvider(
        override val profile: ProviderProfile,
        private val action: suspend () -> GenerationResponse
    ) : ProviderAdapter {
        var calls = 0
        override suspend fun generate(request: GenerationRequest): GenerationResponse {
            calls++
            return action()
        }
    }

    private fun profile(id: String, priority: Int = 1, enabled: Boolean = true) =
        ProviderProfile(ProviderId(id), id, ProviderCapabilities(text = true), priority, enabled)

    private fun request() = GenerationRequest(
        NexusSession(id = SessionId("s")),
        AgentProfile(AgentId("lara"), "Lara", "assistant", "prompt"),
        "hello"
    )

    @Test fun retriesTransientFailureThenSucceeds() = runSuspend {
        var attempts = 0
        val provider = FakeProvider(profile("first")) {
            attempts++
            if (attempts == 1) throw IOException("temporary")
            GenerationResponse(ProviderId("first"), "ok")
        }
        val policy = ProviderResiliencePolicy(maxAttemptsPerProvider = 2, baseBackoffMillis = 0, maxBackoffMillis = 0)
        val router = ResilientProviderRouter(Registry(provider), policy)
        assertEquals("ok", router.generate(request()).text)
        assertEquals(2, provider.calls)
        assertEquals(0, router.healthSnapshot().first().consecutiveFailures)
    }

    @Test fun fallsBackToNextProviderAfterRetryBudget() = runSuspend {
        val broken = FakeProvider(profile("broken", 1)) { throw IOException("offline") }
        val healthy = FakeProvider(profile("healthy", 2)) { GenerationResponse(ProviderId("healthy"), "fallback") }
        val policy = ProviderResiliencePolicy(maxAttemptsPerProvider = 2, baseBackoffMillis = 0, maxBackoffMillis = 0)
        val result = ResilientProviderRouter(Registry(broken, healthy), policy).generate(request())
        assertEquals("fallback", result.text)
        assertEquals(2, broken.calls)
        assertEquals(1, healthy.calls)
    }

    @Test fun circuitCooldownSkipsUnhealthyProvider() = runSuspend {
        var now = 1_000L
        val policy = ProviderResiliencePolicy(
            maxAttemptsPerProvider = 1, baseBackoffMillis = 0, maxBackoffMillis = 0,
            failureThreshold = 1, cooldownMillis = 500
        )
        val health = ProviderHealthTracker(policy) { now }
        health.recordFailure(ProviderId("offline"), IOException("offline"))
        val offline = FakeProvider(profile("offline")) { GenerationResponse(ProviderId("offline"), "bad") }
        val healthy = FakeProvider(profile("healthy", 2)) { GenerationResponse(ProviderId("healthy"), "ok") }
        val router = ResilientProviderRouter(Registry(offline, healthy), policy, health)
        assertEquals("ok", router.generate(request()).text)
        assertEquals(0, offline.calls)
        now += 501
        assertEquals(true, health.isAvailable(ProviderId("offline")))
    }

    @Test fun propagatesCancellationWithoutRetry() = runSuspend {
        val provider = FakeProvider(profile("cancel")) { throw CancellationException("cancelled") }
        val policy = ProviderResiliencePolicy(maxAttemptsPerProvider = 3, baseBackoffMillis = 0, maxBackoffMillis = 0)
        assertFailsWith<CancellationException> {
            ResilientProviderRouter(Registry(provider), policy).generate(request())
        }
        assertEquals(1, provider.calls)
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
