package com.mechy007.nexus.core

import com.google.gson.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

fun interface WebCredentialResolver { suspend fun resolve(secretName: String): String? }

/** Brave Search adapter. The API key is resolved at request time and is never stored here. */
class BraveWebSearchProvider(
    private val client: HttpClient,
    private val credentials: WebCredentialResolver,
    private val endpoint: String = "https://api.search.brave.com/res/v1/web/search"
) : WebSearchProvider {
    override val id: String = "brave-search"

    init { require(endpoint.startsWith("https://", ignoreCase = true)) { "Search endpoint must use HTTPS." } }

    override suspend fun search(request: WebSearchRequest): WebSearchResponse {
        val key = credentials.resolve("BRAVE_SEARCH_API_KEY")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Brave Search API credential is not configured.")
        val response = client.get(endpoint) {
            header("X-Subscription-Token", key)
            parameter("q", request.query)
            parameter("count", request.limit)
        }
        require(response.status.isSuccess()) { "Web search provider returned HTTP ${response.status.value}." }
        val root = JsonParser.parseString(response.bodyAsText()).asJsonObject
        val results = root.getAsJsonObject("web")?.getAsJsonArray("results")
            ?: return WebSearchResponse(id, emptyList())
        val hits = results.take(request.limit).mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val value = item.asJsonObject
            val title = value.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            val url = value.get("url")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            val snippet = value.get("description")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val published = value.get("page_age")?.takeIf { it.isJsonPrimitive }?.asString
            WebSearchHit(title.take(500), url, snippet.take(2000), published)
        }
        return WebSearchResponse(id, hits)
    }
}
