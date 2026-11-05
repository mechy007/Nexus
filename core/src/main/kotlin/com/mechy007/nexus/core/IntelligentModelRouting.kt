package com.mechy007.nexus.core

import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class DataPrivacyRequirement { ANY, PREFER_LOCAL, LOCAL_ONLY }

data class ModelRoutingMetadata(
    val local: Boolean = false,
    val qualityScore: Double? = null,
    val reliabilityScore: Double? = null,
    val estimatedLatencyMillis: Long? = null,
    val estimatedCostPerThousandTokens: Double? = null
) {
    init {
        require(qualityScore == null || qualityScore in 0.0..1.0)
        require(reliabilityScore == null || reliabilityScore in 0.0..1.0)
        require(estimatedLatencyMillis == null || estimatedLatencyMillis >= 0)
        require(estimatedCostPerThousandTokens == null || estimatedCostPerThousandTokens >= 0.0)
    }
}

data class ModelRouteRequirements(
    val requiredCapabilities: ProviderCapabilities = ProviderCapabilities(text = true),
    val privacy: DataPrivacyRequirement = DataPrivacyRequirement.ANY,
    val maximumCostPerThousandTokens: Double? = null,
    val preferredProvider: ProviderId? = null,
    val excludedProviders: Set<ProviderId> = emptySet()
) {
    init { require(maximumCostPerThousandTokens == null || maximumCostPerThousandTokens >= 0.0) }
}

data class ModelRouteDecision(val providerId: ProviderId, val score: Double, val local: Boolean, val reason: String)

/** Capability-first routing; unknown providers are conservatively treated as cloud providers. */
class IntelligentModelRouter(
    private val registry: ProviderRegistry,
    private val metadata: Map<ProviderId, ModelRoutingMetadata> = emptyMap(),
    private val health: ProviderHealthTracker? = null,
    private val timeoutMillis: Long = 60000
) {
    init { require(timeoutMillis > 0) }

    fun plan(requirements: ModelRouteRequirements): List<ModelRouteDecision> =
        registry.all().asSequence()
            .filter { it.profile.enabled && it.profile.id !in requirements.excludedProviders }
            .filter { supports(it.profile.capabilities, requirements.requiredCapabilities) }
            .filter { health?.isAvailable(it.profile.id) != false }
            .mapNotNull { adapter ->
                val meta = metadata[adapter.profile.id] ?: ModelRoutingMetadata()
                if (requirements.privacy == DataPrivacyRequirement.LOCAL_ONLY && !meta.local) return@mapNotNull null
                val cost = meta.estimatedCostPerThousandTokens
                if (requirements.maximumCostPerThousandTokens != null &&
                    (cost == null || cost > requirements.maximumCostPerThousandTokens)) return@mapNotNull null
                val quality = meta.qualityScore ?: 0.5
                val reliability = meta.reliabilityScore ?: 0.5
                val latency = meta.estimatedLatencyMillis?.let { 1.0 - (it / 30000.0).coerceIn(0.0, 1.0) } ?: 0.5
                val costScore = cost?.let { 1.0 / (1.0 + it) } ?: 0.5
                val privacyBoost = if (requirements.privacy == DataPrivacyRequirement.PREFER_LOCAL && meta.local) 0.15 else 0.0
                val preferredBoost = if (adapter.profile.id == requirements.preferredProvider) 0.10 else 0.0
                val score = quality * 0.35 + reliability * 0.30 + latency * 0.15 + costScore * 0.20 +
                    privacyBoost + preferredBoost + (100 - adapter.profile.priority.coerceIn(0, 100)) / 10000.0
                ModelRouteDecision(adapter.profile.id, score, meta.local, "Capabilities satisfied")
            }
            .sortedWith(compareByDescending<ModelRouteDecision> { it.score }.thenBy { it.providerId.value })
            .toList()

    suspend fun generate(
        request: GenerationRequest,
        requirements: ModelRouteRequirements = ModelRouteRequirements(
            request.requiredCapabilities, preferredProvider = request.preferredProvider
        )
    ): GenerationResponse {
        val candidates = plan(requirements)
        if (candidates.isEmpty()) throw NoCompatibleProviderException(requirements.requiredCapabilities)
        val failures = mutableListOf<Throwable>()
        for (candidate in candidates) {
            val adapter = registry.find(candidate.providerId) ?: continue
            try {
                val result = withTimeout(timeoutMillis) {
                    adapter.generate(request.copy(
                        preferredProvider = candidate.providerId,
                        requiredCapabilities = requirements.requiredCapabilities
                    ))
                }
                health?.recordSuccess(candidate.providerId)
                return result
            } catch (failure: TimeoutCancellationException) {
                health?.recordFailure(candidate.providerId, failure)
                failures += ProviderAttemptFailure(candidate.providerId, failure)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                health?.recordFailure(candidate.providerId, failure)
                failures += ProviderAttemptFailure(candidate.providerId, failure)
            }
        }
        throw AllProvidersFailedException(failures)
    }

    private fun supports(actual: ProviderCapabilities, required: ProviderCapabilities): Boolean =
        (!required.text || actual.text) && (!required.streaming || actual.streaming) &&
            (!required.vision || actual.vision) && (!required.audioInput || actual.audioInput) &&
            (!required.audioOutput || actual.audioOutput) && (!required.toolCalling || actual.toolCalling)
}
