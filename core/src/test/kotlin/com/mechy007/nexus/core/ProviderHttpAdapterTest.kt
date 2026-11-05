package com.mechy007.nexus.core

import com.google.gson.JsonParser
import java.net.URI
import java.time.Duration
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderHttpAdapterTest {
    private val request = GenerationRequest(
        session = NexusSession(),
        agent = AgentProfile(AgentId("lara"), "Lara", "assistant", "Be helpful."),
        prompt = "Hello"
    )

    @Test
    fun openAiCompatibleAdaptersUseBearerAuthAndParseChatCompletion() {
        val adapters = listOf(
            OpenAiProviderAdapter(secrets = secret, transport = successfulChatTransport()),
            OpenRouterProviderAdapter(secrets = secret, transport = successfulChatTransport()),
            GroqProviderAdapter(secrets = secret, transport = successfulChatTransport())
        )
        adapters.forEach { adapter ->
            val result = runSuspend { adapter.generate(request) }
            assertEquals("Hello from provider", result.text)
            assertEquals(adapter.profile.id, result.providerId)
        }
    }

    @Test
    fun geminiUsesItsOwnEndpointHeaderAndResponseShape() {
        var captured: ProviderHttpRequest? = null
        val transport = ProviderHttpTransport {
            captured = it
            ProviderHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"Hello"},{"text":" Gemini"}]}}]}""")
        }
        val adapter = GeminiProviderAdapter(secrets = secret, transport = transport)

        val result = runSuspend { adapter.generate(request) }
        val sent = requireNotNull(captured)

        assertEquals("Hello Gemini", result.text)
        assertTrue(sent.uri.toString().contains(":generateContent"))
        assertEquals("test-secret", sent.headers["x-goog-api-key"])
        assertFalse(sent.headers.containsKey("Authorization"))
    }

    @Test
    fun missingCredentialsFailBeforeNetworkCall() {
        var called = false
        val adapter = OpenAiProviderAdapter(
            secrets = ProviderSecretResolver { null },
            transport = ProviderHttpTransport { called = true; ProviderHttpResponse(200, "{}") }
        )
        assertFailsWith<ProviderAuthenticationException> { runSuspend { adapter.generate(request) } }
        assertFalse(called)
    }

    @Test
    fun httpErrorsExposeStatusButNeverEchoResponseBody() {
        val adapter = OpenAiProviderAdapter(
            secrets = secret,
            transport = ProviderHttpTransport {
                ProviderHttpResponse(401, """{"error":"test-secret must not leak"}""")
            }
        )
        val error = assertFailsWith<ProviderHttpException> {
            runSuspend { adapter.generate(request) }
        }
        assertEquals(401, error.statusCode)
        assertFalse(error.message.orEmpty().contains("test-secret"))
    }

    @Test
    fun malformedResponsesAreReportedAsProviderResponseErrors() {
        val adapter = OpenAiProviderAdapter(
            secrets = secret,
            transport = ProviderHttpTransport { ProviderHttpResponse(200, "not-json") }
        )
        assertFailsWith<ProviderResponseException> { runSuspend { adapter.generate(request) } }
    }

    @Test
    fun environmentResolverMapsOnlyKnownProviderIds() {
        val resolver = EnvironmentSecretResolver { name -> if (name == "GROQ_API_KEY") "groq-key" else null }
        assertEquals("groq-key", resolver.resolve(ProviderId("groq")))
        assertEquals(null, resolver.resolve(ProviderId("unknown")))
    }

    private val secret = ProviderSecretResolver { "test-secret" }

    private fun successfulChatTransport() = ProviderHttpTransport { sent ->
        assertEquals("Bearer test-secret", sent.headers["Authorization"])
        assertTrue(sent.uri.toString().endsWith("/chat/completions"))
        val body = JsonParser.parseString(sent.body).asJsonObject
        assertTrue(body.has("model"))
        assertTrue(body.has("messages"))
        assertEquals(Duration.ofSeconds(60), sent.timeout)
        ProviderHttpResponse(200, """{"choices":[{"message":{"content":"Hello from provider"}}]}""")
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return requireNotNull(result) { "Expected synchronous fake transport completion." }.getOrThrow()
    }
}
