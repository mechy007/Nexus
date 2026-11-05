package com.mechy007.nexus.core

interface AgentRegistry {
    fun all(): List<AgentProfile>
    fun find(id: AgentId): AgentProfile?
    fun register(profile: AgentProfile)
}

data class GenerationRequest(
    val session: NexusSession,
    val agent: AgentProfile,
    val prompt: String,
    val preferredProvider: ProviderId? = null,
    val requiredCapabilities: ProviderCapabilities = ProviderCapabilities()
)

data class GenerationResponse(
    val providerId: ProviderId,
    val text: String,
    val model: String? = null
)

interface ProviderAdapter {
    val profile: ProviderProfile
    suspend fun generate(request: GenerationRequest): GenerationResponse
}

interface ProviderRegistry {
    fun all(): List<ProviderAdapter>
    fun find(id: ProviderId): ProviderAdapter?
    fun register(adapter: ProviderAdapter)
    fun remove(id: ProviderId): Boolean
}

interface ProviderRouter {
    suspend fun generate(request: GenerationRequest): GenerationResponse
}

interface SessionStore {
    suspend fun get(id: SessionId): NexusSession?
    suspend fun save(session: NexusSession)
    suspend fun delete(id: SessionId)
    suspend fun list(): List<NexusSession>
}

interface MemoryStore {
    suspend fun search(query: String, limit: Int = 10): List<MemoryRecord>
    suspend fun put(record: MemoryRecord)
    suspend fun delete(id: String)
    suspend fun exportAll(): List<MemoryRecord>
}

enum class GrantDecision { GRANTED, DENIED, REQUIRES_USER_ACTION }

interface PermissionManager {
    suspend fun check(agentId: AgentId, toolId: ToolId): Boolean
    suspend fun request(agentId: AgentId, toolId: ToolId, reason: String): GrantDecision
}

interface ToolAdapter {
    val id: ToolId
    val description: String
    suspend fun execute(request: ToolRequest): ToolResult
}

data class AuditEvent(
    val actor: String,
    val action: String,
    val target: String,
    val outcome: String,
    val occurredAt: java.time.Instant = java.time.Instant.now()
)

interface AuditSink {
    suspend fun record(event: AuditEvent)
}
