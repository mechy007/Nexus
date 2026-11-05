package com.mechy007.nexus.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Bounded HTTPS page reader. Redirects are revalidated and response bytes are
 * streamed under a hard cap. Hosts should also enforce egress rules against DNS rebinding.
 */
class KtorWebPageFetcher(
    private val timeoutMillis: Long = 15000,
    private val maxResponseBytes: Int = 1_000_000,
    private val maxRedirects: Int = 3
) : WebPageFetcher, AutoCloseable {
    private val client = HttpClient(CIO) {
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutMillis
            connectTimeoutMillis = timeoutMillis
            socketTimeoutMillis = timeoutMillis
        }
    }

    init { require(timeoutMillis > 0 && maxResponseBytes > 0 && maxRedirects >= 0) }

    override suspend fun fetch(url: String): WebPage {
        var current = PublicWebUrlPolicy.validate(url)
        repeat(maxRedirects + 1) {
            PublicWebUrlPolicy.validateAndResolve(current)
            val response = client.get(current)
            if (response.status.value in 300..399) {
                val location = response.headers[HttpHeaders.Location]
                    ?: throw IllegalArgumentException("Redirect response did not include a location.")
                current = PublicWebUrlPolicy.validate(URI(current).resolve(location).toString())
                return@repeat
            }
            require(response.status.isSuccess()) { "Web page returned HTTP ${response.status.value}." }
            val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
            require(contentType.contains("text/html", true) ||
                contentType.contains("application/xhtml+xml", true) ||
                contentType.contains("text/plain", true)) { "Only HTML and plain-text pages are supported." }
            val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            require(declaredLength == null || declaredLength <= maxResponseBytes) {
                "Web page exceeds the configured response-size limit."
            }
            val channel = response.bodyAsChannel()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = channel.readAvailable(buffer, 0, minOf(buffer.size, maxResponseBytes + 1 - total))
                if (read < 0) break
                if (read == 0) continue
                total += read
                require(total <= maxResponseBytes) { "Web page exceeds the configured response-size limit." }
                output.write(buffer, 0, read)
            }
            val raw = String(output.toByteArray(), StandardCharsets.UTF_8)
            val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(raw)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            val text = if (contentType.contains("text/plain", true)) raw else extractReadableText(raw)
            return WebPage(current, title.take(500), text.take(maxResponseBytes), contentType)
        }
        throw IllegalStateException("Web page exceeded the redirect limit.")
    }

    override fun close() = client.close()

    private fun extractReadableText(html: String): String = html
        .replace(Regex("(?is)<(script|style|noscript|svg|iframe)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?is)<(nav|footer|header)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</(p|div|article|section|h[1-6]|li)>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"")
        .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
        .replace(Regex("\\n\\s*"), "\n")
        .trim()
}
