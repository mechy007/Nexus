package com.mechy007.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException

class ProviderRoutingTest {
    private val agent = AgentProfile(AgentId("lara"), "Lara", "test", "test")
    private val request = GenerationRequest(
        session = NexusSession(),
        agent = agent,
        prompt = "Hello"
    )

    @Test
    fun registryReplacesAndRemovesProvidersById() {
        val registry = InMemoryProviderRegistry()
        val first = FakeProvider("test", priority = 20)
        val replacement = FakeProvider("test", priority = 10)

        registry.register(first)
        registry.register(replacement)

        assertEquals(1, registry.all().size)
        assertEquals(10, registry.find(ProviderId("test"))?.profile?.priority)
        assertTrue(registry.remove(ProviderId("test")))
        assertEquals(0, registry.all().size)
    }

    @Test
    fun routerFallsBackAfterProviderFailure() {
        val registry = InMemoryProviderRegistry()
        val calls = mutableListOf<String>()
        registry.register(FakeProvider("primary", 1, calls, fail = true))
        registry.register(FakeProvider("backup", 2, calls))

        val result = runSuspend { CapabilityAwareProviderRouter(registry).generate(request) }

        assertEquals(listOf("primary", "backup"), calls)
        assertEquals("backup", result.providerId.value)
        assertEquals("ok", result.text)
    }

    @Test
    fun preferredProviderIsTriedBeforeHigherPriorityProvider() {
        val registry = InMemoryProviderRegistry()
        val calls = mutableListOf<String>()
        registry.register(FakeProvider("fast", 1, calls))
        registry.register(FakeProvider("preferred", 50, calls))

        runSuspend {
            CapabilityAwareProviderRouter(registry).generate(
                request.copy(preferredProvider = ProviderId("preferred"))
            )
        }

        assertEquals("preferred", calls.first())
    }

    @Test
    fun routerSkipsProvidersMissingRequiredCapabilities() {
        val registry = InMemoryProviderRegistry()
        val calls = mutableListOf<String>()
        registry.register(FakeProvider("text-only", 1, calls))
        registry.register(
            FakeProvider(
                "vision",
                2,
                calls,
                capabilities = ProviderCapabilities(text = true, vision = true)
            )
        )

        val result = runSuspend {
            CapabilityAwareProviderRouter(registry).generate(
                request.copy(requiredCapabilities = ProviderCapabilities(text = true, vision = true))
            )
        }

        assertEquals("vision", result.providerId.value)
        assertEquals(listOf("vision"), calls)
    }

    @Test
    fun routerReportsWhenNoProviderCanMeetRequirements() {
        val registry = InMemoryProviderRegistry()
        registry.register(FakeProvider("text-only", 1))

        assertFailsWith<NoCompatibleProviderException> {
            runSuspend {
                CapabilityAwareProviderRouter(registry).generate(
                    request.copy(requiredCapabilities = ProviderCapabilities(text = true, vision = true))
                )
            }
        }
    }

    @Test
    fun cancellationIsNeverTreatedAsAProviderFailure() {
        val registry = InMemoryProviderRegistry()
        registry.register(FakeProvider("cancel", 1, failure = CancellationException("cancelled")))

        assertFailsWith<CancellationException> {
            runSuspend { CapabilityAwareProviderRouter(registry).generate(request) }
        }
    }

    private class FakeProvider(
        id: String,
        priority: Int,
        private val calls: MutableList<String> = mutableListOf(),
        private val fail: Boolean = false,
        private val capabilities: ProviderCapabilities = ProviderCapabilities(),
        private val failure: Exception? = if (fail) IllegalStateException("offline") else null
    ) : ProviderAdapter {
        override val profile = ProviderProfile(ProviderId(id), id, capabilities, priority)

        override suspend fun generate(request: GenerationRequest): GenerationResponse {
            calls += profile.id.value
            failure?.let { throw it }
            return GenerationResponse(profile.id, "ok", "fake-model")
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var value: Result<T>? = null
        block.startCoroutine(object : kotlin.coroutines.Continuation<T> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                value = result
            }
        })
        return requireNotNull(value) { "Test coroutine did not complete synchronously." }.getOrThrow()
    }
}
