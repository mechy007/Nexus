package com.mechy007.nexus.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class NexusCoordinatorIntelligenceTest {
    private class Agents(private val profile: AgentProfile) : AgentRegistry {
        override fun all() = listOf(profile)
        override fun find(id: AgentId) = profile.takeIf { it.id == id }
        override fun register(profile: AgentProfile) = Unit
    }

    private class Sessions : SessionStore {
        private val values = linkedMapOf<SessionId, NexusSession>()
        override suspend fun get(id: SessionId) = values[id]
        override suspend fun save(session: NexusSession) { values[session.id] = session }
        override suspend fun delete(id: SessionId) { values.remove(id) }
        override suspend fun list() = values.values.toList()
    }

    @Test fun intelligencePathPersistsAnswerAndTrace() = runBlocking {
        val profile = AgentProfile(AgentId("nexus"), "NEXUS", "test", "system")
        val sessions = Sessions()
        val traces = InMemoryTraceStore()
        val engine = AIIntelligenceCoordinator(
            RequestUnderstandingEngine {
                AIRequestAnalysis(AIRequestIntent.GENERAL, AIRequestComplexity.SIMPLE, ReasoningDepth.SIMPLE)
            },
            BoundedAIContextBuilder(),
            ReasoningEngine {
                ReasoningExecutionResult(
                    "Integrated answer",
                    listOf(ModelContribution(ModelReasoningRole.SOLVER, ProviderId("model-a"), "answer")),
                    false,
                    false
                )
            }
        )
        val unusedRouter = object : ProviderRouter {
            override suspend fun generate(request: GenerationRequest): GenerationResponse {
                error("Legacy provider route should not be called by the intelligence path.")
            }
        }
        val coordinator = NexusCoordinator(Agents(profile), unusedRouter, sessions, engine, traces = traces)
        val sessionId = SessionId("intelligence-session")
        val result = coordinator.respondWithIntelligence(sessionId, profile.id, "Hello NEXUS")

        assertEquals("Integrated answer", result.answer)
        val stored = requireNotNull(sessions.get(sessionId))
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), stored.messages.map { it.role })
        assertEquals("multi-model-intelligence", stored.messages.last().metadata["aiMode"])
        assertEquals(1, traces.recent().size)
    }
}
