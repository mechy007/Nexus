package com.mechy007.nexus.core

import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout

/**
 * Optional capability for providers that can actively report whether their
 * configured endpoint is reachable. Providers without this capability remain
 * registered and usable; discovery reports them as NOT_CHECKABLE.
 */
interface HealthCheckableProviderAdapter : ProviderAdapter {
    suspend fun healthCheck(): Boolean
}

enum class ProviderDiscoveryStatus { AVAILABLE, UNAVAILABLE, NOT_CHECKABLE, DISABLED }

data class ProviderDiscoveryEntry(
    val providerId: ProviderId,
    val status: ProviderDiscoveryStatus,
    val latencyMillis: Long? = null,
    /** Stable category only; never retain raw provider error bodies or credentials. */
    val failureCode: String? = null
) {
    init { require(latencyMillis == null || latencyMillis >= 0) }
}

data class ProviderDiscoveryReport(
    val entries: List<ProviderDiscoveryEntry>
) {
    val available: List<ProviderId>
        get() = entries.filter { it.status == ProviderDiscoveryStatus.AVAILABLE }.map { it.providerId }
    val unavailable: List<ProviderId>
        get() = entries.filter { it.status == ProviderDiscoveryStatus.UNAVAILABLE }.map { it.providerId }
}

/**
 * OpenJarvis-inspired active engine discovery, adapted to NEXUS's provider
 * contracts. Probes run concurrently and fail independently: one offline
 * endpoint cannot prevent other providers from being discovered.
 *
 * Discovery is observational only. It never changes provider configuration,
 * enables a provider, or bypasses the normal router's capability/permission
 * checks.
 */
class ProviderDiscoveryService(
    private val registry: ProviderRegistry,
    private val timeoutMillis: Long = 5_000,
    private val nowNanos: () -> Long = System::nanoTime
) {
    init { require(timeoutMillis > 0) }

    suspend fun discover(): ProviderDiscoveryReport = supervisorScope {
        val entries = registry.all().map { adapter ->
            async { inspect(adapter) }
        }.map { it.await() }
        ProviderDiscoveryReport(entries.sortedBy { it.providerId.value })
    }

    private suspend fun inspect(adapter: ProviderAdapter): ProviderDiscoveryEntry {
        val id = adapter.profile.id
        if (!adapter.profile.enabled) {
            return ProviderDiscoveryEntry(id, ProviderDiscoveryStatus.DISABLED)
        }
        val checkable = adapter as? HealthCheckableProviderAdapter
            ?: return ProviderDiscoveryEntry(id, ProviderDiscoveryStatus.NOT_CHECKABLE)

        val started = nowNanos()
        return try {
            val healthy = withTimeout(timeoutMillis) { checkable.healthCheck() }
            val elapsed = ((nowNanos() - started).coerceAtLeast(0L)) / 1_000_000L
            if (healthy) {
                ProviderDiscoveryEntry(id, ProviderDiscoveryStatus.AVAILABLE, elapsed)
            } else {
                ProviderDiscoveryEntry(id, ProviderDiscoveryStatus.UNAVAILABLE, elapsed, "UNHEALTHY")
            }
        } catch (_: TimeoutCancellationException) {
            ProviderDiscoveryEntry(id, ProviderDiscoveryStatus.UNAVAILABLE, timeoutMillis, "TIMEOUT")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ProviderDiscoveryEntry(
                id,
                ProviderDiscoveryStatus.UNAVAILABLE,
                ((nowNanos() - started).coerceAtLeast(0L)) / 1_000_000L,
                failure::class.simpleName ?: "PROBE_FAILED"
            )
        }
    }
}
