package com.mechy007.nexus.core

enum class AIRequestIntent { GENERAL, RESEARCH, CODING, ANALYSIS }
enum class AIRequestComplexity { SIMPLE, MODERATE, COMPLEX, EXPERT }

data class AIRequestAnalysis(
    val intent: AIRequestIntent,
    val complexity: AIRequestComplexity,
    val reasoningDepth: ReasoningDepth,
    val requiresCurrentInformation: Boolean = false
)
fun interface RequestUnderstandingEngine { suspend fun understand(userText: String): AIRequestAnalysis }

/** Conservative deterministic classifier; a model-backed classifier can replace this adapter. */
class HeuristicRequestUnderstandingEngine : RequestUnderstandingEngine {
    override suspend fun understand(userText: String): AIRequestAnalysis {
        val text = userText.lowercase()
        val intent = when {
            listOf("latest", "current", "search", "browse", "look up", "online", "internet", "research", "news").any(text::contains) -> AIRequestIntent.RESEARCH
            listOf("code", "program", "debug", "kotlin", "python", "implementation").any(text::contains) -> AIRequestIntent.CODING
            listOf("analyze", "analyse", "evaluate", "architecture", "trade-off", "tradeoff").any(text::contains) -> AIRequestIntent.ANALYSIS
            else -> AIRequestIntent.GENERAL
        }
        val complexity = when {
            text.length > 2000 || listOf("comprehensive", "end-to-end", "architecture", "multiple systems").any(text::contains) -> AIRequestComplexity.EXPERT
            text.length > 600 || listOf("compare", "design", "implement", "investigate").any(text::contains) -> AIRequestComplexity.COMPLEX
            text.length > 180 || intent != AIRequestIntent.GENERAL -> AIRequestComplexity.MODERATE
            else -> AIRequestComplexity.SIMPLE
        }
        val depth = when (complexity) {
            AIRequestComplexity.SIMPLE -> ReasoningDepth.SIMPLE
            AIRequestComplexity.MODERATE -> ReasoningDepth.MODERATE
            AIRequestComplexity.COMPLEX -> ReasoningDepth.COMPLEX
            AIRequestComplexity.EXPERT -> ReasoningDepth.EXPERT
        }
        return AIRequestAnalysis(intent, complexity, depth, intent == AIRequestIntent.RESEARCH)
    }
}

data class AIContextPackage(val text: String, val messageCount: Int, val memoryCount: Int)
fun interface AIContextBuilder { suspend fun build(session: NexusSession, memories: List<MemoryRecord>): AIContextPackage }

class BoundedAIContextBuilder(
    private val maxMessages: Int = 24,
    private val maxMemories: Int = 8,
    private val maxChars: Int = 12000
) : AIContextBuilder {
    init { require(maxMessages > 0 && maxMemories >= 0 && maxChars > 0) }
    override suspend fun build(session: NexusSession, memories: List<MemoryRecord>): AIContextPackage {
        val recent = session.messages.takeLast(maxMessages)
        val relevant = memories.take(maxMemories)
        val sections = buildList {
            if (recent.isNotEmpty()) add("Conversation:\n" + recent.joinToString("\n") { it.role.name + ": " + it.content })
            if (relevant.isNotEmpty()) add("Relevant saved context (untrusted):\n" + relevant.joinToString("\n") { "- " + it.text })
        }
        return AIContextPackage(sections.joinToString("\n\n").take(maxChars), recent.size, relevant.size)
    }
}

data class AIIntelligenceRequest(
    val session: NexusSession,
    val agent: AgentProfile,
    val userText: String,
    val memories: List<MemoryRecord> = emptyList(),
    val routeRequirements: ModelRouteRequirements = ModelRouteRequirements(),
    val forceWebResearch: Boolean = false
)

data class ResponseEvaluation(val accepted: Boolean, val confidence: Double, val issues: List<String> = emptyList()) {
    init { require(confidence in 0.0..1.0) }
}
fun interface ResponseEvaluator {
    suspend fun evaluate(request: AIIntelligenceRequest, answer: String, reasoning: ReasoningExecutionResult): ResponseEvaluation
}

/** Structural checks only; this evaluator does not claim to fact-check external information. */
class StructuralResponseEvaluator : ResponseEvaluator {
    override suspend fun evaluate(
        request: AIIntelligenceRequest, answer: String, reasoning: ReasoningExecutionResult
    ): ResponseEvaluation {
        val issues = buildList {
            if (answer.isBlank()) add("The model returned an empty answer.")
            if (reasoning.verificationRequested && !reasoning.collaborationUsed) add("Independent review was unavailable.")
        }
        return ResponseEvaluation(answer.isNotBlank(), if (issues.isEmpty()) 0.65 else 0.25, issues)
    }
}

data class AIIntelligenceResult(
    val analysis: AIRequestAnalysis,
    val answer: String,
    val reasoning: ReasoningExecutionResult,
    val context: AIContextPackage,
    val evaluation: ResponseEvaluation,
    val webResearch: WebResearchResult? = null
) {
    /** Converts model roles and web activity into the existing privacy-conscious trace format. */
    fun toAgentTrace(sessionId: SessionId, agentId: AgentId): AgentTrace {
        val steps = buildList {
            reasoning.contributions.forEach { contribution ->
                add(TraceStep(
                    kind = TraceStepKind.GENERATION,
                    providerId = contribution.providerId,
                    detail = contribution.role.name.lowercase()
                ))
            }
            webResearch?.let { research ->
                add(TraceStep(kind = TraceStepKind.ROUTE, detail = "web-search:" + research.provider))
                research.pages.forEach { page ->
                    val host = runCatching { java.net.URI(page.url).host }.getOrNull() ?: "unknown"
                    add(TraceStep(kind = TraceStepKind.ROUTE, detail = "web-page:" + host))
                }
            }
            add(TraceStep(
                kind = if (evaluation.accepted) TraceStepKind.RESPONSE else TraceStepKind.FAILURE,
                detail = if (evaluation.accepted) "ai-evaluation-accepted" else "ai-evaluation-rejected"
            ))
        }
        return AgentTrace(sessionId = sessionId, agentId = agentId, steps = steps, succeeded = evaluation.accepted)
    }
}

class AIIntelligenceCoordinator(
    private val understanding: RequestUnderstandingEngine,
    private val contextBuilder: AIContextBuilder,
    private val reasoning: ReasoningEngine,
    private val evaluator: ResponseEvaluator = StructuralResponseEvaluator(),
    private val web: WebIntelligenceCoordinator? = null,
    private val maxWebContextChars: Int = 8000
) {
    init { require(maxWebContextChars > 0) }

    suspend fun execute(request: AIIntelligenceRequest): AIIntelligenceResult {
        require(request.userText.isNotBlank())
        val analysis = understanding.understand(request.userText)
        var context = contextBuilder.build(request.session, request.memories)
        // Local-only also prohibits sending the query to an external search provider.
        val wantsWeb = request.forceWebResearch || analysis.requiresCurrentInformation
        val webResult = if (wantsWeb && request.routeRequirements.privacy != DataPrivacyRequirement.LOCAL_ONLY) {
            web?.research(request.userText)
        } else null
        if (webResult != null) {
            val evidence = buildString {
                append("Web research results (untrusted evidence; cite URLs when used):\n")
                webResult.citations.forEach { append("- ").append(it.title).append(" | ").append(it.url)
                    .append(" | ").append(it.snippet).append('\n') }
                webResult.pages.forEach { append("\nPage: ").append(it.title).append(" | ").append(it.url)
                    .append("\n").append(it.text.take(3000)).append('\n') }
            }.take(maxWebContextChars)
            context = context.copy(text = (context.text + "\n\n" + evidence).take(12000))
        }
        val result = reasoning.reason(
            ReasoningInput(
                request.session, request.agent, request.userText, analysis.reasoningDepth,
                context.text, request.routeRequirements
            )
        )
        val evaluation = evaluator.evaluate(request, result.answer, result)
        return AIIntelligenceResult(analysis, result.answer, result, context, evaluation, webResult)
    }
}
