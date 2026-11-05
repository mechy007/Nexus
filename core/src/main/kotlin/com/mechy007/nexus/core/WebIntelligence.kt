package com.mechy007.nexus.core

import java.util.concurrent.CancellationException

data class WebSearchRequest(val query: String, val limit: Int = 5) {
    init { require(query.isNotBlank() && limit in 1..20) }
}
data class WebSearchHit(val title: String, val url: String, val snippet: String, val publishedAt: String? = null)
data class WebSearchResponse(val provider: String, val hits: List<WebSearchHit>)
data class WebPage(val url: String, val title: String, val text: String, val contentType: String)
data class WebCitation(val title: String, val url: String, val snippet: String)

interface WebSearchProvider {
    val id: String
    suspend fun search(request: WebSearchRequest): WebSearchResponse
}
interface WebPageFetcher { suspend fun fetch(url: String): WebPage }

data class WebResearchResult(
    val query: String,
    val provider: String,
    val pages: List<WebPage>,
    val citations: List<WebCitation>
)

/** Search, open and cite sources; page content is evidence, never agent instructions. */
class WebIntelligenceCoordinator(
    private val searchProvider: WebSearchProvider,
    private val pageFetcher: WebPageFetcher,
    private val maxPagesToRead: Int = 3
) {
    init { require(maxPagesToRead in 0..10) }

    suspend fun research(query: String, resultLimit: Int = 8): WebResearchResult {
        val response = searchProvider.search(WebSearchRequest(query, resultLimit))
        val safeHits = response.hits.filter { hit ->
            runCatching { PublicWebUrlPolicy.validate(hit.url) }.isSuccess
        }
        val pages = safeHits.take(maxPagesToRead).mapNotNull { hit ->
            try {
                pageFetcher.fetch(hit.url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        return WebResearchResult(
            query, response.provider, pages,
            safeHits.map { WebCitation(it.title, it.url, it.snippet) }
        )
    }
}

/** Browser automation is a separate host adapter and must be registered behind tool permissions. */
interface BrowserAutomationPort { suspend fun execute(action: BrowserAction): BrowserActionResult }
data class BrowserAction(val sessionId: String, val operation: String, val target: String, val value: String? = null)
data class BrowserActionResult(val success: Boolean, val description: String, val requiresUserConfirmation: Boolean = false)
