package com.mechy007.nexus.core

import java.time.Instant

/** Coordinates a single agent turn without depending on a UI or operating system. */
class NexusCoordinator(
    private val agents: AgentRegistry,
    private val router: ProviderRouter,
    private val sessions: SessionStore,
    private val intelligence: AIIntelligenceCoordinator? = null,
    private val memory: MemoryStore? = null,
    private val traces: TraceStore? = null
) {
    suspend fun respond(
        sessionId: SessionId,
        agentId: AgentId,
        userText: String,
        preferredProvider: ProviderId? = null
    ): GenerationResponse {
        require(userText.isNotBlank()) { "Message cannot be blank." }

        val agent = requireNotNull(agents.find(agentId)) { "Unknown agent: " + agentId.value }
        require(agent.enabled) { "Agent is disabled: " + agent.displayName }

        val current = sessions.get(sessionId) ?: NexusSession(id = sessionId)
        val withUserMessage = current.copy(
            messages = current.messages + NexusMessage(role = MessageRole.USER, content = userText),
            updatedAt = Instant.now()
        )
        sessions.save(withUserMessage)

        val response = router.generate(
            GenerationRequest(
                session = withUserMessage,
                agent = agent,
                prompt = userText,
                preferredProvider = preferredProvider
            )
        )

        sessions.save(
            withUserMessage.copy(
                messages = withUserMessage.messages + NexusMessage(
                    role = MessageRole.ASSISTANT,
                    content = response.text,
                    agentId = agent.id,
                    metadata = mapOf("providerId" to response.providerId.value)
                ),
                updatedAt = Instant.now()
            )
        )
        return response
    }

    /**
     * Uses the new intelligence coordinator while retaining NEXUS-owned session persistence.
     * Hosts opt in by injecting an AIIntelligenceCoordinator; tool execution remains in AgentRuntime.
     */
    suspend fun respondWithIntelligence(
        sessionId: SessionId,
        agentId: AgentId,
        userText: String,
        routeRequirements: ModelRouteRequirements = ModelRouteRequirements(),
        forceWebResearch: Boolean = false
    ): AIIntelligenceResult {
        require(userText.isNotBlank()) { "Message cannot be blank." }
        val engine = requireNotNull(intelligence) { "AI intelligence coordinator is not configured." }
        val agent = requireNotNull(agents.find(agentId)) { "Unknown agent: " + agentId.value }
        require(agent.enabled) { "Agent is disabled: " + agent.displayName }

        val current = sessions.get(sessionId) ?: NexusSession(id = sessionId)
        val withUserMessage = current.copy(
            messages = current.messages + NexusMessage(role = MessageRole.USER, content = userText),
            updatedAt = Instant.now()
        )
        sessions.save(withUserMessage)

        val result = engine.execute(
            AIIntelligenceRequest(
                session = withUserMessage,
                agent = agent,
                userText = userText,
                memories = memory?.search(userText, 8).orEmpty(),
                routeRequirements = routeRequirements,
                forceWebResearch = forceWebResearch
            )
        )
        val metadata = buildMap {
            put("aiMode", "multi-model-intelligence")
            put("providers", result.reasoning.contributions.map { it.providerId.value }.distinct().joinToString(","))
            put("reasoningRoles", result.reasoning.contributions.map { it.role.name }.joinToString(","))
            result.webResearch?.let {
                put("webSearchProvider", it.provider)
                put("webSourceCount", it.citations.size.toString())
            }
        }
        sessions.save(
            withUserMessage.copy(
                messages = withUserMessage.messages + NexusMessage(
                    role = MessageRole.ASSISTANT,
                    content = result.answer,
                    agentId = agent.id,
                    metadata = metadata
                ),
                updatedAt = Instant.now()
            )
        )
        traces?.save(result.toAgentTrace(sessionId, agentId))
        return result
    }
}
