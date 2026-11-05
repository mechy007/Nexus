package com.mechy007.nexus.core

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WebIntelligenceTest {
    private class FakeSearch : WebSearchProvider {
        override val id = "fake-search"
        var calls = 0
        override suspend fun search(request: WebSearchRequest): WebSearchResponse {
            calls++
            return WebSearchResponse(id, listOf(
                WebSearchHit("Trusted page", "https://example.org/article", "A useful snippet"),
                WebSearchHit("Blocked page", "http://127.0.0.1/admin", "Must not be opened")
            ))
        }
    }

    private class FakeFetcher : WebPageFetcher {
        val opened = mutableListOf<String>()
        override suspend fun fetch(url: String): WebPage {
            opened += url
            return WebPage(url, "Article", "Extracted evidence", "text/html")
        }
    }

    @Test fun researchFiltersUnsafeUrlsAndReturnsCitations() = runBlocking {
        val search = FakeSearch()
        val fetcher = FakeFetcher()
        val result = WebIntelligenceCoordinator(search, fetcher).research("AI research")
        assertEquals(1, result.citations.size)
        assertEquals(1, result.pages.size)
        assertEquals(listOf("https://example.org/article"), fetcher.opened)
    }

    @Test fun webResearchPropagatesCancellation() = runBlocking {
        val fetcher = object : WebPageFetcher {
            override suspend fun fetch(url: String): WebPage = throw CancellationException("cancelled")
        }
        assertFailsWith<CancellationException> {
            WebIntelligenceCoordinator(FakeSearch(), fetcher).research("AI research")
        }
    }

    @Test fun localOnlyRequestDoesNotSendQueryToExternalSearch() = runBlocking {
        val search = FakeSearch()
        val web = WebIntelligenceCoordinator(search, FakeFetcher())
        val reasoning = ReasoningEngine {
            ReasoningExecutionResult("Local answer", emptyList(), false, false)
        }
        val coordinator = AIIntelligenceCoordinator(
            RequestUnderstandingEngine {
                AIRequestAnalysis(AIRequestIntent.RESEARCH, AIRequestComplexity.MODERATE, ReasoningDepth.MODERATE, true)
            },
            BoundedAIContextBuilder(), reasoning, web = web
        )
        val request = AIIntelligenceRequest(
            NexusSession(), AgentProfile(AgentId("nexus"), "NEXUS", "test", "system"),
            "Find the latest information", routeRequirements = ModelRouteRequirements(privacy = DataPrivacyRequirement.LOCAL_ONLY)
        )
        val result = coordinator.execute(request)
        assertEquals(0, search.calls)
        assertNull(result.webResearch)
    }
}
