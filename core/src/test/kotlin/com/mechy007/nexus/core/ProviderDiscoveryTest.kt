package com.mechy007.nexus.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderDiscoveryTest {
    @Test
    fun discoversHealthyUnhealthyDisabledAndUnsupportedProvidersIndependently() = runBlocking {
        val registry = InMemoryProviderRegistry().apply {
            register(FakeProvider("healthy", enabled = true, result = true))
            register(FakeProvider("unhealthy", enabled = true, result = false))
            register(FakeProvider("disabled", enabled = false, result = true))
            register(PlainProvider("unsupported"))
        }

        val report = ProviderDiscoveryService(registry).discover()

        assertEquals(
            listOf("disabled", "healthy", "unsupported", "unhealthy"),
            report.entries.map { it.providerId.value }
        )
        assertEquals(ProviderDiscoveryStatus.DISABLED, report.entries[0].status)
        assertEquals(ProviderDiscoveryStatus.AVAILABLE, report.entries[1].status)
        assertEquals(ProviderDiscoveryStatus.NOT_CHECKABLE, report.entries[2].status)
        assertEquals(ProviderDiscoveryStatus.UNAVAILABLE, report.entries[3].status)
        assertEquals("UNHEALTHY", report.entries[3].failureCode)
        assertEquals(listOf(ProviderId("healthy")), report.available)
        assertEquals(listOf(ProviderId("unhealthy")), report.unavailable)
    }

    @Test
    fun probeExceptionsBecomeSanitizedFailureCategories() = runBlocking {
        val registry = InMemoryProviderRegistry().apply {
            register(FakeProvider("broken", enabled = true, error = IllegalStateException("secret response body")))
        }

        val entry = ProviderDiscoveryService(registry).discover().entries.single()

        assertEquals(ProviderDiscoveryStatus.UNAVAILABLE, entry.status)
        assertEquals("IllegalStateException", entry.failureCode)
        assertTrue(entry.failureCode.orEmpty().contains("secret").not())
    }

    @Test
    fun timeoutIsReportedWithoutFailingTheWholeDiscoveryPass() = runBlocking {
        val registry = InMemoryProviderRegistry().apply {
            register(FakeProvider("slow", enabled = true, waitMillis = 100))
            register(FakeProvider("fast", enabled = true, result = true))
        }

        val report = ProviderDiscoveryService(registry, timeoutMillis = 10).discover()

        assertEquals(ProviderDiscoveryStatus.UNAVAILABLE, report.entries.first { it.providerId.value == "slow" }.status)
        assertEquals("TIMEOUT", report.entries.first { it.providerId.value == "slow" }.failureCode)
        assertEquals(ProviderDiscoveryStatus.AVAILABLE, report.entries.first { it.providerId.value == "fast" }.status)
    }

    private class FakeProvider(
        id: String,
        enabled: Boolean,
        private val result: Boolean = true,
        private val error: Exception? = null,
        private val waitMillis: Long = 0
    ) : HealthCheckableProviderAdapter {
        override val profile = ProviderProfile(ProviderId(id), id, ProviderCapabilities(text = true), enabled = enabled)
        override suspend fun healthCheck(): Boolean {
            if (waitMillis > 0) delay(waitMillis)
            error?.let { throw it }
            return result
        }
        override suspend fun generate(request: GenerationRequest) = GenerationResponse(profile.id, "ok")
    }

    private class PlainProvider(id: String) : ProviderAdapter {
        override val profile = ProviderProfile(ProviderId(id), id, ProviderCapabilities(text = true))
        override suspend fun generate(request: GenerationRequest) = GenerationResponse(profile.id, "ok")
    }

}
