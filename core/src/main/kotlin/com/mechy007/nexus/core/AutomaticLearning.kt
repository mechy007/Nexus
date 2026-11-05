package com.mechy007.nexus.core

enum class LearningTarget { ROUTING_WEIGHTS, PROMPT_TEMPLATE, CONTEXT_RETRIEVAL_WEIGHTS, REASONING_POLICY }

data class LearningMetrics(
    val qualityScore: Double,
    val safetyRegressionCount: Int = 0,
    val latencyMillis: Long = 0,
    val costUnits: Double = 0.0
) {
    init {
        require(qualityScore in 0.0..1.0)
        require(safetyRegressionCount >= 0 && latencyMillis >= 0 && costUnits >= 0.0)
    }
}

data class LearningConfiguration(val version: Long = 0, val values: Map<String, String> = emptyMap())
data class LearningCandidate(val id: String, val target: LearningTarget, val values: Map<String, String>, val rationale: String) {
    init { require(id.isNotBlank() && values.isNotEmpty() && rationale.isNotBlank() && values.keys.none { it.isBlank() }) }
}
data class LearningDecision(
    val promoted: Boolean, val reason: String, val baseline: LearningMetrics,
    val candidate: LearningMetrics, val resultingVersion: Long
)

fun interface LearningCandidateEvaluator { suspend fun evaluate(configuration: LearningConfiguration): LearningMetrics }

interface LearningConfigurationStore {
    fun current(target: LearningTarget): LearningConfiguration
    fun promote(target: LearningTarget, expectedVersion: Long, values: Map<String, String>): LearningConfiguration?
}

/** In-memory default; Android hosts should inject durable, versioned storage. */
class InMemoryLearningConfigurationStore : LearningConfigurationStore {
    private val values = LearningTarget.entries.associateWith { LearningConfiguration() }.toMutableMap()
    @Synchronized override fun current(target: LearningTarget): LearningConfiguration = values.getValue(target)
    @Synchronized override fun promote(
        target: LearningTarget, expectedVersion: Long, values: Map<String, String>
    ): LearningConfiguration? {
        val current = this.values.getValue(target)
        if (current.version != expectedVersion) return null
        return LearningConfiguration(current.version + 1, values.toMap()).also { this.values[target] = it }
    }
}

/**
 * Automatically promotes validated strategy changes only. Security rules, permissions,
 * identity and retention policies are intentionally not representable as learning targets.
 */
class AutomaticLearningService(
    private val store: LearningConfigurationStore,
    private val evaluator: LearningCandidateEvaluator,
    private val minimumQualityGain: Double = 0.01,
    private val maximumLatencyRegressionRatio: Double = 0.15,
    private val maximumCostRegressionRatio: Double = 0.15
) {
    init {
        require(minimumQualityGain >= 0.0)
        require(maximumLatencyRegressionRatio >= 0.0 && maximumCostRegressionRatio >= 0.0)
    }

    suspend fun evaluateAndPromote(candidate: LearningCandidate): LearningDecision {
        val baselineConfig = store.current(candidate.target)
        val baseline = evaluator.evaluate(baselineConfig)
        val proposed = evaluator.evaluate(LearningConfiguration(baselineConfig.version, candidate.values.toMap()))
        val latencyLimit = baseline.latencyMillis * (1.0 + maximumLatencyRegressionRatio)
        val costLimit = baseline.costUnits * (1.0 + maximumCostRegressionRatio)
        val rejection = when {
            proposed.safetyRegressionCount > baseline.safetyRegressionCount -> "Candidate introduced safety regressions."
            proposed.qualityScore < baseline.qualityScore + minimumQualityGain -> "Candidate did not meet the quality improvement threshold."
            proposed.latencyMillis > latencyLimit -> "Candidate exceeded the latency regression limit."
            proposed.costUnits > costLimit -> "Candidate exceeded the cost regression limit."
            else -> null
        }
        if (rejection != null) return LearningDecision(false, rejection, baseline, proposed, baselineConfig.version)
        val promoted = store.promote(candidate.target, baselineConfig.version, candidate.values)
            ?: return LearningDecision(
                false, "Configuration changed during evaluation; candidate was not promoted.",
                baseline, proposed, store.current(candidate.target).version
            )
        return LearningDecision(true, "Candidate passed evaluation and was promoted.", baseline, proposed, promoted.version)
    }
}
