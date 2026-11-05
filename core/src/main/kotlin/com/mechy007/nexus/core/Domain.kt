package com.mechy007.nexus.core

import java.time.Instant
import java.util.UUID

@JvmInline value class SessionId(val value: String) {
    companion object { fun new() = SessionId(UUID.randomUUID().toString()) }
}
@JvmInline value class AgentId(val value: String)
@JvmInline value class ProviderId(val value: String)
@JvmInline value class ToolId(val value: String)

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

data class NexusMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val createdAt: Instant = Instant.now(),
    val agentId: AgentId? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class NexusSession(
    val id: SessionId = SessionId.new(),
    val title: String = "New conversation",
    val messages: List<NexusMessage> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt
)

data class AgentProfile(
    val id: AgentId,
    val displayName: String,
    val description: String,
    val systemPrompt: String,
    val enabled: Boolean = true
)

data class ProviderCapabilities(
    val text: Boolean = true,
    val streaming: Boolean = false,
    val vision: Boolean = false,
    val audioInput: Boolean = false,
    val audioOutput: Boolean = false,
    val toolCalling: Boolean = false
)

data class ProviderProfile(
    val id: ProviderId,
    val displayName: String,
    val capabilities: ProviderCapabilities,
    val priority: Int = 100,
    val enabled: Boolean = true
)

data class MemoryRecord(
    val id: String,
    val text: String,
    val tags: Set<String> = emptySet(),
    val createdAt: Instant = Instant.now(),
    val sourceSessionId: SessionId? = null
)

data class ToolRequest(
    val id: String = UUID.randomUUID().toString(),
    val toolId: ToolId,
    val requestedBy: AgentId,
    val sessionId: SessionId,
    val arguments: Map<String, String> = emptyMap()
)

data class ToolResult(
    val success: Boolean,
    val output: String = "",
    val errorCode: String? = null
)
