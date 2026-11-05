package com.mechy007.nexus.core

import java.time.Instant
import java.util.UUID

/** Privacy-conscious operational metadata for one agent turn. Raw prompts,
 * credentials and tool arguments must not be recorded by default. */
enum class TraceStepKind { ROUTE, MEMORY_RETRIEVAL, GENERATION, TOOL_CALL, RESPONSE, FAILURE }

data class TraceStep(
    val id: String = UUID.randomUUID().toString(),
    val kind: TraceStepKind,
    val occurredAt: Instant = Instant.now(),
    val durationMillis: Long = 0,
    val providerId: ProviderId? = null,
    val toolId: ToolId? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val detail: String? = null
) {
    init {
        require(durationMillis >= 0)
        require(inputTokens == null || inputTokens >= 0)
        require(outputTokens == null || outputTokens >= 0)
    }
}

data class AgentTrace(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: SessionId,
    val agentId: AgentId,
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val steps: List<TraceStep> = emptyList(),
    val succeeded: Boolean? = null
) {
    init { require(completedAt == null || !completedAt.isBefore(startedAt)) }
    val totalDurationMillis: Long get() = steps.sumOf { it.durationMillis }
    val totalInputTokens: Int get() = steps.sumOf { it.inputTokens ?: 0 }
    val totalOutputTokens: Int get() = steps.sumOf { it.outputTokens ?: 0 }
}

/** Replaceable storage contract; hosts provide durable implementations. */
interface TraceStore {
    suspend fun save(trace: AgentTrace)
    suspend fun find(id: String): AgentTrace?
    suspend fun recent(limit: Int = 50): List<AgentTrace>
    suspend fun delete(id: String)
}

/** Development/test implementation only; data is lost when the process exits. */
class InMemoryTraceStore : TraceStore {
    private val lock = Any()
    private val traces = linkedMapOf<String, AgentTrace>()

    override suspend fun save(trace: AgentTrace) {
        synchronized(lock) { traces[trace.id] = trace }
    }

    override suspend fun find(id: String): AgentTrace? =
        synchronized(lock) { traces[id] }

    override suspend fun recent(limit: Int): List<AgentTrace> {
        require(limit >= 0)
        return synchronized(lock) {
            traces.values.sortedByDescending { it.startedAt }.take(limit)
        }
    }

    override suspend fun delete(id: String) {
        synchronized(lock) { traces.remove(id) }
    }
}
