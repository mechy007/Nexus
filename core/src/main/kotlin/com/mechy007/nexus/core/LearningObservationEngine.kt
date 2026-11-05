package com.mechy007.nexus.core

import java.time.Instant

data class LearningObservation(
    val id: String,
    val target: LearningTarget,
    val outcome: String,
    val qualityScore: Double,
    val latencyMillis: Long,
    val costUnits: Double,
    val occurredAt: Instant = Instant.now()
) {
    init {
        require(id.isNotBlank() && outcome.isNotBlank())
        require(qualityScore in 0.0..1.0 && latencyMillis >= 0 && costUnits >= 0.0)
    }
}

interface LearningObservationStore {
    suspend fun append(observation: LearningObservation)
    suspend fun recent(target: LearningTarget, limit: Int): List<LearningObservation>
}

class InMemoryLearningObservationStore : LearningObservationStore {
    private val lock = Any()
    private val observations = mutableListOf<LearningObservation>()
    override suspend fun append(observation: LearningObservation) {
        synchronized(lock) {
            observations.removeAll { it.id == observation.id }
            observations += observation
        }
    }
    override suspend fun recent(target: LearningTarget, limit: Int): List<LearningObservation> {
        require(limit >= 0)
        return synchronized(lock) {
            observations.filter { it.target == target }.sortedByDescending { it.occurredAt }.take(limit)
        }
    }
}

fun interface LearningCandidateProposer {
    suspend fun propose(
        target: LearningTarget,
        current: LearningConfiguration,
        observations: List<LearningObservation>
    ): LearningCandidate?
}

/** Turns recorded outcomes into candidate changes, then delegates all promotion to the evaluation gate. */
class AutomaticLearningEngine(
    private val observations: LearningObservationStore,
    private val proposer: LearningCandidateProposer,
    private val service: AutomaticLearningService,
    private val minimumObservations: Int = 20,
    private val observationWindow: Int = 200,
    private val configurationStore: LearningConfigurationStore
) {
    init { require(minimumObservations > 0 && observationWindow >= minimumObservations) }

    suspend fun observe(observation: LearningObservation): LearningDecision? {
        observations.append(observation)
        val recent = observations.recent(observation.target, observationWindow)
        if (recent.size < minimumObservations) return null
        val current = configurationStore.current(observation.target)
        val candidate = proposer.propose(observation.target, current, recent) ?: return null
        require(candidate.target == observation.target) { "Candidate target must match its observation target." }
        return service.evaluateAndPromote(candidate)
    }
}
