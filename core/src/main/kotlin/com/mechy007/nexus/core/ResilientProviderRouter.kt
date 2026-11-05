package com.mechy007.nexus.core

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay

/** Retry and circuit-breaker settings for provider calls. */
data class ProviderResiliencePolicy(
    val maxAttemptsPerProvider: Int = 2,
    val baseBackoffMillis: Long = 250,
    val maxBackoffMillis: Long = 2_000,
    val failureThreshold: Int = 3,
    val cooldownMillis: Long = 30_000
) {
    init {
        require(maxAttemptsPerProvider > 0)
        require(baseBackoffMillis >= 0 && maxBackoffMillis >= baseBackoffMillis)
        require(failureThreshold > 0 && cooldownMillis > 0)
    }

    fun backoffMillis(retryIndex: Int): Long {
        require(retryIndex >= 0)
        var value = baseBackoffMillis
        repeat(retryIndex.coerceAtMost(30)) {
            value = if (value > maxBackoffMillis / 2) maxBackoffMillis else value * 2
        }
        return value.coerceAtMost(maxBackoffMillis)
    }
}

data class ProviderHealthSnapshot(
    val providerId: ProviderId,
    val consecutiveFailures: Int,
    val unavailableUntilMillis: Long,
    val lastFailure: String?
) {
    val isCoolingDown: Boolean get() = unavailableUntilMillis > 0
}

/** Thread-safe, in-memory provider health state. No prompts, responses, or credentials are retained. */
class ProviderHealthTracker(
    private val policy: ProviderResiliencePolicy,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class State(var failures: Int = 0, var unavailableUntil: Long = 0, var lastFailure: String? = null)
    private val states = linkedMapOf<ProviderId, State>()

    @Synchronized fun isAvailable(id: ProviderId): Boolean {
        val state = states[id] ?: return true
        if (state.unavailableUntil <= nowMillis()) {
            state.unavailableUntil = 0
            return true
        }
        return false
    }

    @Synchronized fun recordSuccess(id: ProviderId) {
        states[id] = State()
    }

    @Synchronized fun recordFailure(id: ProviderId, failure: Throwable) {
        val state = states.getOrPut(id) { State() }
        state.failures += 1
        state.lastFailure = failure::class.simpleName ?: "Provider failure"
        if (state.failures >= policy.failureThreshold) {
            state.unavailableUntil = nowMillis() + policy.cooldownMillis
        }
    }

    @Synchronized fun snapshot(): List<ProviderHealthSnapshot> = states.map { (id, state) ->
        ProviderHealthSnapshot(id, state.failures, state.unavailableUntil, state.lastFailure)
    }.sortedBy { it.providerId.value }
}

/**
 * Capability-aware router with bounded retries and per-provider cooldowns.
 * Cancellation is always propagated; only transient transport/server failures retry.
 */
class ResilientProviderRouter(
    private val registry: ProviderRegistry,
    private val policy: ProviderResiliencePolicy = ProviderResiliencePolicy(),
    private val health: ProviderHealthTracker = ProviderHealthTracker(policy),
    private val retryable: (Throwable) -> Boolean = ::isTransientProviderFailure
) : ProviderRouter {
    override suspend fun generate(request: GenerationRequest): GenerationResponse {
        val candidates = registry.all()
            .filter { it.profile.enabled && supports(it.profile.capabilities, request.requiredCapabilities) }
            .filter { health.isAvailable(it.profile.id) }
            .sortedWith(
                compareBy<ProviderAdapter> { if (it.profile.id == request.preferredProvider) 0 else 1 }
                    .thenBy { it.profile.priority }
                    .thenBy { it.profile.id.value }
            )
        if (candidates.isEmpty()) throw NoHealthyProviderException(request.requiredCapabilities)

        val failures = mutableListOf<Throwable>()
        for (adapter in candidates) {
            for (attempt in 0 until policy.maxAttemptsPerProvider) {
                try {
                    val response = adapter.generate(request)
                    health.recordSuccess(adapter.profile.id)
                    return response
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    failures += ProviderAttemptFailure(adapter.profile.id, failure)
                    health.recordFailure(adapter.profile.id, failure)
                    if (!retryable(failure) || attempt == policy.maxAttemptsPerProvider - 1) break
                    val wait = policy.backoffMillis(attempt)
                    if (wait > 0) delay(wait)
                }
            }
        }
        throw AllProvidersFailedException(failures)
    }

    fun healthSnapshot(): List<ProviderHealthSnapshot> = health.snapshot()

    private fun supports(actual: ProviderCapabilities, required: ProviderCapabilities): Boolean =
        (!required.text || actual.text) &&
            (!required.streaming || actual.streaming) &&
            (!required.vision || actual.vision) &&
            (!required.audioInput || actual.audioInput) &&
            (!required.audioOutput || actual.audioOutput) &&
            (!required.toolCalling || actual.toolCalling)
}

class NoHealthyProviderException(required: ProviderCapabilities) :
    IllegalStateException("No healthy enabled provider supports the requested capabilities: $required")

private fun isTransientProviderFailure(error: Throwable): Boolean = when (error) {
    is ProviderHttpException -> error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500
    is IOException -> true
    else -> false
}
