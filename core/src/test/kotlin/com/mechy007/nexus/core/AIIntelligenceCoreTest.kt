package com.mechy007.nexus.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AIIntelligenceCoreTest {
    private class FakeProvider(
        override val profile: ProviderProfile,
        private val response: (GenerationRequest) -> String = { "ok" }
    ) : ProviderAdapter {
        val prompts = mutableListOf<String>()
        override suspend fun generate(request: GenerationRequest): GenerationResponse {
            prompts += request.prompt
            return GenerationResponse(profile.id, response(request), profile.displayName)
        }
    }

    private fun profile(id: String, priority: Int = 10, vision: Boolean = false) =
        ProviderProfile(ProviderId(id), id, ProviderCapabilities(text = true, vision = vision), priority)

    @Test fun localOnlyRoutingNeverSelectsCloudOrUnknownProviders() = runBlocking {
        val local = FakeProvider(profile("local"))
        val cloud = FakeProvider(profile("cloud"))
        val registry = InMemoryProviderRegistry().apply { register(cloud); register(local) }
        val router = IntelligentModelRouter(
            registry,
            mapOf(ProviderId("local") to ModelRoutingMetadata(local = true, qualityScore = 0.7))
        )
        val agent = AgentProfile(AgentId("nexus"), "NEXUS", "test", "system")
        val request = GenerationRequest(NexusSession(), agent, "hello")
        val result = router.generate(request, ModelRouteRequirements(privacy = DataPrivacyRequirement.LOCAL_ONLY))
        assertEquals(ProviderId("local"), result.providerId)
        assertEquals(0, cloud.prompts.size)
    }

    @Test fun routingFiltersMissingCapabilitiesBeforeScoring() {
        val text = FakeProvider(profile("text"))
        val vision = FakeProvider(profile("vision", vision = true))
        val registry = InMemoryProviderRegistry().apply { register(text); register(vision) }
        val router = IntelligentModelRouter(registry)
        val decisions = router.plan(ModelRouteRequirements(
            requiredCapabilities = ProviderCapabilities(text = true, vision = true)
        ))
        assertEquals(listOf(ProviderId("vision")), decisions.map { it.providerId })
    }

    @Test fun expertReasoningUsesDistinctPlannerSolverCriticAndSynthesizerModels() = runBlocking {
        val registry = InMemoryProviderRegistry()
        listOf("a", "b", "c", "d").forEach { id ->
            registry.register(FakeProvider(profile(id)) { request ->
                when {
                    request.prompt.contains("Role: PLANNER") -> "Plan"
                    request.prompt.contains("Role: SOLVER") -> "Proposed solution"
                    request.prompt.contains("Role: CRITIC") -> "One caveat"
                    request.prompt.contains("Role: SYNTHESIZER") -> "Final verified answer"
                    else -> "Unexpected role"
                }
            })
        }
        val router = IntelligentModelRouter(registry)
        val engine = MultiModelReasoningEngine(router)
        val input = ReasoningInput(
            NexusSession(), AgentProfile(AgentId("nexus"), "NEXUS", "test", "system"),
            "Design a complete architecture", ReasoningDepth.EXPERT
        )
        val result = engine.reason(input)
        assertEquals("Final verified answer", result.answer)
        assertEquals(
            listOf(ModelReasoningRole.PLANNER, ModelReasoningRole.SOLVER, ModelReasoningRole.CRITIC, ModelReasoningRole.SYNTHESIZER),
            result.contributions.map { it.role }
        )
        assertEquals(4, result.contributions.map { it.providerId }.distinct().size)
        assertTrue(result.collaborationUsed)
        assertTrue(result.verificationRequested)
    }

    @Test fun simpleReasoningUsesOneModel() = runBlocking {
        val provider = FakeProvider(profile("single"))
        val registry = InMemoryProviderRegistry().apply { register(provider) }
        val result = MultiModelReasoningEngine(IntelligentModelRouter(registry)).reason(
            ReasoningInput(
                NexusSession(), AgentProfile(AgentId("nexus"), "NEXUS", "test", "system"),
                "Hi", ReasoningDepth.SIMPLE
            )
        )
        assertEquals(1, result.contributions.size)
        assertFalse(result.collaborationUsed)
    }

    @Test fun requestUnderstandingIdentifiesCurrentResearchAndComplexity() = runBlocking {
        val result = HeuristicRequestUnderstandingEngine().understand(
            "Research and compare the latest AI agent architectures and their trade-offs."
        )
        assertEquals(AIRequestIntent.RESEARCH, result.intent)
        assertTrue(result.requiresCurrentInformation)
        assertTrue(result.reasoningDepth >= ReasoningDepth.COMPLEX)
    }

    @Test fun automaticLearningPromotesValidatedQualityImprovement() = runBlocking {
        val store = InMemoryLearningConfigurationStore()
        val evaluator = LearningCandidateEvaluator { config ->
            if (config.values["quality"] == "0.8") LearningMetrics(0.8, latencyMillis = 105, costUnits = 10.5)
            else LearningMetrics(0.5, latencyMillis = 100, costUnits = 10.0)
        }
        val service = AutomaticLearningService(store, evaluator)
        val decision = service.evaluateAndPromote(
            LearningCandidate("candidate-1", LearningTarget.ROUTING_WEIGHTS, mapOf("quality" to "0.8"), "Measured improvement")
        )
        assertTrue(decision.promoted)
        assertEquals(1, store.current(LearningTarget.ROUTING_WEIGHTS).version)
    }

    @Test fun automaticLearningRejectsSafetyRegression() = runBlocking {
        val store = InMemoryLearningConfigurationStore()
        val evaluator = LearningCandidateEvaluator { config ->
            if (config.values["candidate"] == "unsafe") LearningMetrics(0.9, safetyRegressionCount = 1)
            else LearningMetrics(0.5)
        }
        val decision = AutomaticLearningService(store, evaluator).evaluateAndPromote(
            LearningCandidate("unsafe", LearningTarget.PROMPT_TEMPLATE, mapOf("candidate" to "unsafe"), "Test rejection")
        )
        assertFalse(decision.promoted)
        assertEquals(0, store.current(LearningTarget.PROMPT_TEMPLATE).version)
    }

    @Test fun webUrlPolicyRejectsNonHttpsAndLocalHosts() {
        assertFailsWith<IllegalArgumentException> { PublicWebUrlPolicy.validate("http://example.com") }
        assertFailsWith<IllegalArgumentException> { PublicWebUrlPolicy.validate("https://localhost/private") }
        assertFailsWith<IllegalArgumentException> { PublicWebUrlPolicy.validate("https://router.local/") }
        assertEquals("https://example.com/path", PublicWebUrlPolicy.validate("https://example.com/path"))
    }
}
