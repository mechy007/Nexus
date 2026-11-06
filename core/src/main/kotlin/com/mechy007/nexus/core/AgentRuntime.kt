package com.mechy007.nexus.core

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.UUID
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

sealed interface AgentDecision {
    data class Reply(val text: String) : AgentDecision {
        init { require(text.isNotBlank()) }
    }
    data class CallTool(val request: ToolRequest) : AgentDecision
}

data class AgentTurnContext(
    val session: NexusSession,
    val agent: AgentProfile,
    val relevantMemories: List<MemoryRecord> = emptyList(),
    val stepIndex: Int = 0
)

fun interface AgentDecisionEngine {
    suspend fun decide(context: AgentTurnContext): AgentDecision
}

data class ToolRegistration(
    val adapter: ToolAdapter,
    val requiredCapabilities: Set<String> = emptySet(),
    val trustTier: SkillTrustTier = SkillTrustTier.BUNDLED,
    val enabled: Boolean = true
) {
    init { require(requiredCapabilities.none { it.isBlank() }) }
}

/** Registration is separate from authorization: adding a tool never grants permission. */
class ToolRegistry {
    private val entries = ConcurrentHashMap<ToolId, ToolRegistration>()
    fun register(registration: ToolRegistration) {
        require(registration.adapter.description.isNotBlank())
        entries[registration.adapter.id] = registration
    }
    fun find(id: ToolId): ToolRegistration? = entries[id]
    fun all(): List<ToolRegistration> = entries.values.sortedBy { it.adapter.id.value }
    fun remove(id: ToolId): Boolean = entries.remove(id) != null
}

enum class AgentTurnStatus { COMPLETED, AWAITING_APPROVAL, FAILED }

data class AgentTurnResult(
    val status: AgentTurnStatus,
    val session: NexusSession,
    val reply: String? = null,
    val pendingTool: ToolRequest? = null,
    val failureReason: String? = null,
    val trace: AgentTrace
)

sealed interface ToolApprovalResult {
    data class Executed(val result: ToolResult) : ToolApprovalResult
    data object NotFound : ToolApprovalResult
    data object Expired : ToolApprovalResult
    data object Unavailable : ToolApprovalResult
}

/** A one-shot approval is bound to the exact request and adapter that produced it. */
private data class PendingToolExecution(
    val request: ToolRequest,
    val agent: AgentProfile,
    val session: NexusSession,
    val registration: ToolRegistration,
    val createdAt: Instant = Instant.now()
)

/**
 * NEXUS-owned orchestration loop. The decision engine proposes actions; this class
 * alone validates identity, checks trust and permissions, executes tools and records traces.
 */
class AgentRuntime(
    private val agents: AgentRegistry,
    private val decisionEngine: AgentDecisionEngine,
    private val sessions: SessionStore,
    private val tools: ToolRegistry,
    private val permissions: PermissionManager,
    private val audit: AuditSink,
    private val traces: TraceStore,
    private val memory: MemoryStore? = null,
    private val maxSteps: Int = 12,
    private val toolExecutionPolicy: ToolExecutionPolicy = ToolExecutionPolicy()
) {
    private val pendingApprovals = ConcurrentHashMap<String, PendingToolExecution>()

    init { require(maxSteps > 0) }

    /** Executes only the exact, in-memory request that was previously shown for approval. */
    suspend fun approvePending(requestId: String): ToolApprovalResult {
        val pending = pendingApprovals.remove(requestId) ?: return ToolApprovalResult.NotFound
        if (Instant.now().isAfter(pending.createdAt.plusSeconds(300))) return ToolApprovalResult.Expired
        val current = tools.find(pending.request.toolId) ?: return ToolApprovalResult.Unavailable
        if (current != pending.registration) {
            return ToolApprovalResult.Unavailable
        }
        val result = try {
            toolExecutionPolicy.validate(pending.request)
            toolExecutionPolicy.bound(withTimeout(toolExecutionPolicy.timeoutMillis) {
                current.adapter.execute(pending.request)
            })
        } catch (_: TimeoutCancellationException) {
            ToolResult(false, errorCode = "TOOL_TIMEOUT")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ToolResult(false, errorCode = "TOOL_EXCEPTION")
        }
        audit.record(AuditEvent(
            pending.agent.id.value, "tool.execute", pending.request.toolId.value,
            if (result.success) "approved_success" else "approved_failure"
        ))
        val updated = pending.session.copy(
            messages = pending.session.messages + NexusMessage(
                role = MessageRole.TOOL,
                content = if (result.success) result.output else "Tool failed: " + (result.errorCode ?: "UNKNOWN"),
                metadata = mapOf("toolId" to pending.request.toolId.value, "requestId" to pending.request.id)
            ),
            updatedAt = Instant.now()
        )
        sessions.save(updated)
        return ToolApprovalResult.Executed(result)
    }

    /** Rejects a pending request without invoking its adapter. */
    suspend fun rejectPending(requestId: String): Boolean {
        val pending = pendingApprovals.remove(requestId) ?: return false
        audit.record(AuditEvent(
            pending.agent.id.value, "tool.execute", pending.request.toolId.value, "user_rejected"
        ))
        return true
    }

    suspend fun run(sessionId: SessionId, agentId: AgentId, userText: String): AgentTurnResult {
        require(userText.isNotBlank()) { "Message cannot be blank." }
        val agent = requireNotNull(agents.find(agentId)) { "Unknown agent: " + agentId.value }
        require(agent.enabled) { "Agent is disabled: " + agent.displayName }

        val stored = sessions.get(sessionId) ?: NexusSession(id = sessionId)
        var session = stored.copy(
            messages = stored.messages + NexusMessage(role = MessageRole.USER, content = userText),
            updatedAt = Instant.now()
        )
        sessions.save(session)

        val traceId = UUID.randomUUID().toString()
        val startedAt = Instant.now()
        val steps = mutableListOf<TraceStep>()
        val guard = AgentLoopGuard(maxCalls = maxSteps)
        val relevantMemories = if (memory == null) {
            emptyList()
        } else {
            val memoryStartedAt = System.nanoTime()
            try {
                memory.search(userText, 8).also {
                    steps += TraceStep(
                        kind = TraceStepKind.MEMORY_RETRIEVAL,
                        durationMillis = ((System.nanoTime() - memoryStartedAt) / 1_000_000).coerceAtLeast(0),
                        detail = "retrieved:${it.size}"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                steps += TraceStep(
                    kind = TraceStepKind.MEMORY_RETRIEVAL,
                    durationMillis = ((System.nanoTime() - memoryStartedAt) / 1_000_000).coerceAtLeast(0),
                    detail = "unavailable"
                )
                emptyList()
            }
        }

        suspend fun finish(status: AgentTurnStatus, reply: String? = null,
                           pending: ToolRequest? = null, failure: String? = null): AgentTurnResult {
            val trace = AgentTrace(
                id = traceId, sessionId = sessionId, agentId = agentId,
                startedAt = startedAt, completedAt = Instant.now(),
                steps = steps.toList(), succeeded = status == AgentTurnStatus.COMPLETED
            )
            traces.save(trace)
            return AgentTurnResult(status, session, reply, pending, failure, trace)
        }

        for (index in 0 until maxSteps) {
            val decision = try {
                val before = System.nanoTime()
                val result = decisionEngine.decide(
                    AgentTurnContext(session, agent, relevantMemories, index)
                )
                steps += TraceStep(kind = TraceStepKind.GENERATION,
                    durationMillis = ((System.nanoTime() - before) / 1_000_000).coerceAtLeast(0))
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                steps += TraceStep(kind = TraceStepKind.FAILURE, detail = "Decision engine failed")
                return finish(AgentTurnStatus.FAILED, failure = "Decision engine failed.")
            }

            when (decision) {
                is AgentDecision.Reply -> {
                    session = session.copy(
                        messages = session.messages + NexusMessage(
                            role = MessageRole.ASSISTANT, content = decision.text, agentId = agentId
                        ),
                        updatedAt = Instant.now()
                    )
                    sessions.save(session)
                    steps += TraceStep(kind = TraceStepKind.RESPONSE)
                    return finish(AgentTurnStatus.COMPLETED, reply = decision.text)
                }
                is AgentDecision.CallTool -> {
                    val request = decision.request
                    if (request.sessionId != sessionId || request.requestedBy != agentId) {
                        steps += TraceStep(kind = TraceStepKind.FAILURE, detail = "Tool request identity mismatch")
                        return finish(AgentTurnStatus.FAILED, failure = "Tool request identity mismatch.")
                    }
                    val registration = tools.find(request.toolId)
                    if (registration == null || !registration.enabled) {
                        steps += TraceStep(kind = TraceStepKind.FAILURE, toolId = request.toolId, detail = "Tool unavailable")
                        return finish(AgentTurnStatus.FAILED, failure = "Requested tool is unavailable.")
                    }
                    if (registration.trustTier == SkillTrustTier.UNREVIEWED) {
                        steps += TraceStep(kind = TraceStepKind.FAILURE, toolId = request.toolId, detail = "Unreviewed tool denied")
                        return finish(AgentTurnStatus.FAILED, failure = "Unreviewed tools cannot execute.")
                    }
                    val policy = SkillPolicy.authorize(
                        SkillManifest("tool-registration", requiredCapabilities = registration.requiredCapabilities),
                        registration.requiredCapabilities, registration.trustTier
                    )
                    if (!policy.allowed) {
                        return finish(AgentTurnStatus.FAILED, failure = "Tool capability policy denied execution.")
                    }

                    val grant = if (permissions.check(agentId, request.toolId)) GrantDecision.GRANTED
                        else permissions.request(agentId, request.toolId, registration.adapter.description)
                    when (grant) {
                        GrantDecision.DENIED -> {
                            audit.record(AuditEvent(agentId.value, "tool.execute", request.toolId.value, "denied"))
                            return finish(AgentTurnStatus.FAILED, failure = "Permission denied for tool.")
                        }
                        GrantDecision.REQUIRES_USER_ACTION -> {
                            val now = Instant.now()
                            pendingApprovals.entries.removeIf { it.value.createdAt.plusSeconds(300).isBefore(now) }
                            if (pendingApprovals.size >= 32) {
                                audit.record(AuditEvent(agentId.value, "tool.execute", request.toolId.value, "approval_queue_full"))
                                return finish(AgentTurnStatus.FAILED, failure = "Too many pending approvals. Resolve an existing request first.")
                            }
                            pendingApprovals[request.id] = PendingToolExecution(
                                request = request,
                                agent = agent,
                                session = session,
                                registration = registration,
                                createdAt = now
                            )
                            audit.record(AuditEvent(agentId.value, "tool.execute", request.toolId.value, "awaiting_approval"))
                            return finish(AgentTurnStatus.AWAITING_APPROVAL, pending = request)
                        }
                        GrantDecision.GRANTED -> Unit
                    }

                    val loop = guard.check(request.toolId.value,
                        request.arguments.toSortedMap().entries.joinToString("|") { it.key + "=" + it.value })
                    if (loop is LoopGuardDecision.Blocked) {
                        audit.record(AuditEvent(agentId.value, "tool.execute", request.toolId.value, "loop_blocked"))
                        return finish(AgentTurnStatus.FAILED, failure = loop.reason)
                    }

                    val before = System.nanoTime()
                    val toolResult = try {
                        toolExecutionPolicy.validate(request)
                        toolExecutionPolicy.bound(withTimeout(toolExecutionPolicy.timeoutMillis) {
                            registration.adapter.execute(request)
                        })
                    } catch (_: TimeoutCancellationException) {
                        ToolResult(false, errorCode = "TOOL_TIMEOUT")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        ToolResult(false, errorCode = "TOOL_EXCEPTION")
                    }
                    val duration = ((System.nanoTime() - before) / 1_000_000).coerceAtLeast(0)
                    audit.record(AuditEvent(agentId.value, "tool.execute", request.toolId.value,
                        if (toolResult.success) "success" else "failure"))
                    session = session.copy(
                        messages = session.messages + NexusMessage(
                            role = MessageRole.TOOL,
                            content = if (toolResult.success) toolResult.output else
                                "Tool failed: " + (toolResult.errorCode ?: "UNKNOWN"),
                            metadata = mapOf("toolId" to request.toolId.value, "requestId" to request.id)
                        ),
                        updatedAt = Instant.now()
                    )
                    sessions.save(session)
                    steps += TraceStep(kind = TraceStepKind.TOOL_CALL, toolId = request.toolId,
                        durationMillis = duration,
                        detail = if (toolResult.success) "success" else (toolResult.errorCode ?: "failure"))
                }
            }
        }
        steps += TraceStep(kind = TraceStepKind.FAILURE, detail = "Agent step budget exceeded")
        return finish(AgentTurnStatus.FAILED, failure = "Agent step budget exceeded.")
    }
}

/** Bridges existing text-only providers; tool calling is enabled by a future structured adapter. */
class TextOnlyDecisionEngine(
    private val router: ProviderRouter,
    private val preferredProvider: ProviderId? = null
) : AgentDecisionEngine {
    override suspend fun decide(context: AgentTurnContext): AgentDecision {
        val prompt = buildString {
            append(context.agent.systemPrompt)
            if (context.relevantMemories.isNotEmpty()) {
                append("\n\nRelevant saved context:\n")
                context.relevantMemories.forEach { append("- ").append(it.text).append('\n') }
            }
            append("\n\nConversation:\n")
            context.session.messages.takeLast(20).forEach {
                append(it.role.name).append(": ").append(it.content).append('\n')
            }
            append("\nRespond to the latest user message.")
        }
        val response = router.generate(GenerationRequest(
            session = context.session, agent = context.agent, prompt = prompt,
            preferredProvider = preferredProvider
        ))
        return AgentDecision.Reply(response.text)
    }
}
