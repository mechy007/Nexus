package com.mechy007.nexus.core

import java.util.concurrent.CancellationException

/**
 * Thread-safe in-memory registry. Registering an existing ID replaces its adapter,
 * which makes provider configuration reloads deterministic.
 */
class InMemoryProviderRegistry : ProviderRegistry {
    private val adapters = linkedMapOf<ProviderId, ProviderAdapter>()

    @Synchronized
    override fun register(adapter: ProviderAdapter) {
        adapters[adapter.profile.id] = adapter
    }

    @Synchronized
    override fun find(id: ProviderId): ProviderAdapter? = adapters[id]

    @Synchronized
    override fun all(): List<ProviderAdapter> = adapters.values.toList()

    @Synchronized
    override fun remove(id: ProviderId): Boolean = adapters.remove(id) != null
}

/**
 * Tries enabled providers that satisfy every requested capability.
 * A preferred provider is tried first, but never bypasses capability checks.
 */
class CapabilityAwareProviderRouter(
    private val registry: ProviderRegistry
) : ProviderRouter {
    override suspend fun generate(request: GenerationRequest): GenerationResponse {
        val candidates = registry.all()
            .filter { it.profile.enabled && it.profile.capabilities.supports(request.requiredCapabilities) }
            .sortedWith(
                compareBy<ProviderAdapter> { if (it.profile.id == request.preferredProvider) 0 else 1 }
                    .thenBy { it.profile.priority }
                    .thenBy { it.profile.id.value }
            )

        if (candidates.isEmpty()) {
            throw NoCompatibleProviderException(request.requiredCapabilities)
        }

        val failures = mutableListOf<Throwable>()
        for (adapter in candidates) {
            try {
                return adapter.generate(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failures += ProviderAttemptFailure(adapter.profile.id, failure)
            }
        }

        throw AllProvidersFailedException(failures)
    }
}

private fun ProviderCapabilities.supports(required: ProviderCapabilities): Boolean =
    (!required.text || text) &&
        (!required.streaming || streaming) &&
        (!required.vision || vision) &&
        (!required.audioInput || audioInput) &&
        (!required.audioOutput || audioOutput) &&
        (!required.toolCalling || toolCalling)

class NoCompatibleProviderException(
    val requiredCapabilities: ProviderCapabilities
) : IllegalStateException("No enabled provider supports the required capabilities.")

class ProviderAttemptFailure(
    val providerId: ProviderId,
    cause: Throwable
) : RuntimeException("Provider ${providerId.value} failed: ${cause.message}", cause)

class AllProvidersFailedException(
    val attempts: List<Throwable>
) : IllegalStateException(
    "All compatible providers failed (${attempts.size} attempt(s)).",
    attempts.firstOrNull()
) {
    init {
        attempts.drop(1).forEach(::addSuppressed)
    }
}
