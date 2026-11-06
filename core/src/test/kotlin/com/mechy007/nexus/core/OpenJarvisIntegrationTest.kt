package com.mechy007.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OpenJarvisIntegrationTest {
    @Test
    fun traceStoreSavesFindsListsAndDeletes() = runSuspend {
        val store = InMemoryTraceStore()
        val trace = AgentTrace(
            sessionId = SessionId("session"),
            agentId = AgentId("lara"),
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            completedAt = Instant.parse("2026-01-01T00:00:01Z"),
            steps = listOf(
                TraceStep(kind = TraceStepKind.GENERATION, durationMillis = 900, inputTokens = 10, outputTokens = 20),
                TraceStep(kind = TraceStepKind.RESPONSE, durationMillis = 100)
            ),
            succeeded = true
        )
        store.save(trace)
        assertEquals(trace, store.find(trace.id))
        assertEquals(1000L, trace.totalDurationMillis)
        assertEquals(10, trace.totalInputTokens)
        assertEquals(20, trace.totalOutputTokens)
        assertEquals(listOf(trace), store.recent())
        store.delete(trace.id)
        assertEquals(null, store.find(trace.id))
    }

    @Test
    fun skillManifestRejectsMalformedOrUnboundedMetadata() {
        assertFailsWith<IllegalArgumentException> { SkillManifest("valid", version = "latest") }
        assertFailsWith<IllegalArgumentException> {
            SkillManifest("valid", requiredCapabilities = setOf("filesystem write"))
        }
        assertFailsWith<IllegalArgumentException> { SkillManifest("valid", tags = setOf("../escape")) }
        assertFailsWith<IllegalArgumentException> {
            SkillManifest("valid", steps = List(SkillManifest.MAX_STEPS + 1) { SkillStep(ToolId("tool-$it")) })
        }
        assertFailsWith<IllegalArgumentException> { SkillStep(ToolId("bad tool id")) }
    }

    @Test
    fun skillManifestAcceptsSemverAndNamespacedCapabilities() {
        val manifest = SkillManifest(
            name = "web-research",
            version = "2.1.0-beta.1+build.7",
            description = "A bounded research workflow",
            steps = listOf(SkillStep(ToolId("web.search"), "Search approved sources")),
            requiredCapabilities = setOf("network:read", "filesystem:read"),
            tags = setOf("research", "web")
        )
        assertEquals("2.1.0-beta.1+build.7", manifest.version)
        assertEquals(2, manifest.requiredCapabilities.size)
    }

    @Test
    fun unreviewedSkillCannotBeAuthorizedEvenWithBroadGrant() {
        val skill = SkillManifest("file-cleanup", requiredCapabilities = setOf("filesystem:write", "filesystem:read"))
        val result = SkillPolicy.authorize(skill, skill.requiredCapabilities, SkillTrustTier.UNREVIEWED)
        assertFalse(result.allowed)
        assertTrue(result.missingCapabilities.isEmpty())
        assertEquals(setOf("filesystem:write"), result.dangerousCapabilities)
    }

    @Test
    fun reviewedSkillMustHaveEveryCapability() {
        val skill = SkillManifest("research", requiredCapabilities = setOf("network:read", "filesystem:read"))
        val result = SkillPolicy.authorize(skill, setOf("network:read"), SkillTrustTier.SIGNED_CATALOG)
        assertFalse(result.allowed)
        assertEquals(setOf("filesystem:read"), result.missingCapabilities)
    }

    @Test
    fun trustClassificationRequiresVerifiedProvenance() {
        assertEquals(SkillTrustTier.BUNDLED, SkillTrust.classify(bundled = true))
        assertEquals(
            SkillTrustTier.SIGNED_CATALOG,
            SkillTrust.classify(signatureVerified = true, catalogEntryVerified = true)
        )
        assertEquals(SkillTrustTier.UNREVIEWED, SkillTrust.classify())
    }

    @Test
    fun loopGuardDetectsRepeatedCallAndCanReset() {
        val guard = AgentLoopGuard(maxIdenticalCalls = 1)
        assertIs<LoopGuardDecision.Allowed>(guard.check("search", """{"q":"nexus"}"""))
        assertIs<LoopGuardDecision.Blocked>(guard.check("search", """{"q":"nexus"}"""))
        guard.reset()
        assertIs<LoopGuardDecision.Allowed>(guard.check("search", """{"q":"nexus"}"""))
    }

    @Test
    fun loopGuardDetectsPingPongAndCallBudget() {
        val guard = AgentLoopGuard(maxCalls = 20)
        assertIs<LoopGuardDecision.Allowed>(guard.check("a", "1"))
        assertIs<LoopGuardDecision.Allowed>(guard.check("b", "2"))
        assertIs<LoopGuardDecision.Allowed>(guard.check("a", "3"))
        assertIs<LoopGuardDecision.Blocked>(guard.check("b", "4"))

        val budget = AgentLoopGuard(maxCalls = 1)
        assertIs<LoopGuardDecision.Allowed>(budget.check("one", "1"))
        assertIs<LoopGuardDecision.Blocked>(budget.check("two", "2"))
    }
}


private fun <T> runSuspend(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    val result = AtomicReference<Result<T>>()
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) {
            result.set(value)
            latch.countDown()
        }
    })
    check(latch.await(5, TimeUnit.SECONDS)) { "Suspending test timed out." }
    return result.get().getOrThrow()
}
