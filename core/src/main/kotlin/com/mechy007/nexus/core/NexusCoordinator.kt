package com.mechy007.nexus.core

import java.time.Instant

/** Coordinates a single agent turn without depending on a UI or operating system. */
class NexusCoordinator(
    private val agents: AgentRegistry,
    private val router: ProviderRouter,
    private val sessions: SessionStore
) {
    suspend fun respond(
        sessionId: SessionId,
        agentId: AgentId,
        userText: String,
        preferredProvider: ProviderId? = null
    ): GenerationResponse {
        require(userText.isNotBlank()) { "Message cannot be blank." }

        val agent = requireNotNull(agents.find(agentId)) { "Unknown agent: ${agentId.value}" }
        require(agent.enabled) { "Agent is disabled: ${agent.displayName}" }

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
}
