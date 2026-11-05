package com.mechy007.nexus.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CancellationException

/** Resolves credentials at call time; implementations must not log or persist returned secrets. */
fun interface ProviderSecretResolver {
    fun resolve(providerId: ProviderId): String?
}

/** Reads secrets from the process environment without copying them into provider profiles. */
class EnvironmentSecretResolver(
    private val environment: (String) -> String? = System::getenv
) : ProviderSecretResolver {
    override fun resolve(providerId: ProviderId): String? {
        val key = when (providerId.value.lowercase()) {
            "openai" -> "OPENAI_API_KEY"
            "gemini" -> "GEMINI_API_KEY"
            "openrouter" -> "OPENROUTER_API_KEY"
            "groq" -> "GROQ_API_KEY"
            else -> return null
        }
        return environment(key)?.takeIf(String::isNotBlank)
    }
}

data class ProviderHttpConfig(
    val model: String,
    val baseUri: URI,
    val timeout: Duration = Duration.ofSeconds(60),
    val priority: Int = 100,
    val enabled: Boolean = true
) {
    init {
        require(model.isNotBlank()) { "Model must not be blank." }
        require(baseUri.scheme == "https" || baseUri.scheme == "http") {
            "Provider base URI must use HTTP or HTTPS."
        }
        require(timeout > Duration.ZERO) { "Timeout must be positive." }
    }
}

data class ProviderHttpRequest(
    val uri: URI,
    val headers: Map<String, String>,
    val body: String,
    val timeout: Duration
)

data class ProviderHttpResponse(val statusCode: Int, val body: String)

fun interface ProviderHttpTransport {
    @Throws(IOException::class, InterruptedException::class)
    fun execute(request: ProviderHttpRequest): ProviderHttpResponse
}

/** Production transport. No request or response content is logged. */
class JdkProviderHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
) : ProviderHttpTransport {
    override fun execute(request: ProviderHttpRequest): ProviderHttpResponse {
        val builder = HttpRequest.newBuilder(request.uri)
            .timeout(request.timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(request.body))
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return ProviderHttpResponse(response.statusCode(), response.body())
    }
}

class ProviderAuthenticationException(providerId: ProviderId) :
    IllegalStateException("No API key configured for provider ${providerId.value}.")

class ProviderHttpException(
    val providerId: ProviderId,
    val statusCode: Int
) : IOException("Provider ${providerId.value} returned HTTP $statusCode.")

class ProviderResponseException(providerId: ProviderId) :
    IOException("Provider ${providerId.value} returned an invalid or empty response.")

abstract class JsonHttpProviderAdapter(
    final override val profile: ProviderProfile,
    protected val config: ProviderHttpConfig,
    private val secrets: ProviderSecretResolver,
    private val transport: ProviderHttpTransport
) : ProviderAdapter {
    final override suspend fun generate(request: GenerationRequest): GenerationResponse {
        val key = secrets.resolve(profile.id)?.takeIf(String::isNotBlank)
            ?: throw ProviderAuthenticationException(profile.id)
        val httpRequest = createHttpRequest(request, key)
        val response = try {
            transport.execute(httpRequest)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("Provider request interrupted.").also { it.initCause(interrupted) }
        }
        if (response.statusCode !in 200..299) {
            // Deliberately omit response bodies: upstream error payloads can echo sensitive data.
            throw ProviderHttpException(profile.id, response.statusCode)
        }
        val text = try {
            parseText(JsonParser.parseString(response.body))
        } catch (failure: RuntimeException) {
            throw ProviderResponseException(profile.id)
        }
        if (text.isBlank()) throw ProviderResponseException(profile.id)
        return GenerationResponse(profile.id, text, config.model)
    }

    protected abstract fun createHttpRequest(request: GenerationRequest, apiKey: String): ProviderHttpRequest
    protected abstract fun parseText(root: com.google.gson.JsonElement): String

    protected fun messages(request: GenerationRequest): JsonArray {
        val messages = JsonArray()
        messages.add(message("system", request.agent.systemPrompt))
        request.session.messages.forEach { item ->
            val role = when (item.role) {
                MessageRole.SYSTEM -> "system"
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.TOOL -> "user"
            }
            messages.add(message(role, item.content))
        }
        messages.add(message("user", request.prompt))
        return messages
    }

    protected fun message(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }

    protected fun endpoint(path: String): URI {
        val base = config.baseUri.toString().trimEnd('/')
        return URI.create("$base/$path")
    }

    protected fun jsonBody(value: JsonObject) = value.toString()
}

private val chatCapabilities = ProviderCapabilities(text = true)

class OpenAiProviderAdapter(
    config: ProviderHttpConfig = ProviderHttpConfig(
        "gpt-4o-mini", URI.create("https://api.openai.com/v1")
    ),
    secrets: ProviderSecretResolver,
    transport: ProviderHttpTransport = JdkProviderHttpTransport()
) : JsonHttpProviderAdapter(
    ProviderProfile(ProviderId("openai"), "OpenAI", chatCapabilities, config.priority, config.enabled),
    config, secrets, transport
) {
    override fun createHttpRequest(request: GenerationRequest, apiKey: String): ProviderHttpRequest {
        val body = JsonObject().apply {
            addProperty("model", config.model)
            add("messages", messages(request))
        }
        return ProviderHttpRequest(endpoint("chat/completions"), mapOf("Authorization" to "Bearer $apiKey"), jsonBody(body), config.timeout)
    }

    override fun parseText(root: com.google.gson.JsonElement): String =
        root.asJsonObject.getAsJsonArray("choices")[0].asJsonObject
            .getAsJsonObject("message").get("content").asString
}

class OpenRouterProviderAdapter(
    config: ProviderHttpConfig = ProviderHttpConfig(
        "openai/gpt-4o-mini", URI.create("https://openrouter.ai/api/v1")
    ),
    secrets: ProviderSecretResolver,
    transport: ProviderHttpTransport = JdkProviderHttpTransport()
) : JsonHttpProviderAdapter(
    ProviderProfile(ProviderId("openrouter"), "OpenRouter", chatCapabilities, config.priority, config.enabled),
    config, secrets, transport
) {
    override fun createHttpRequest(request: GenerationRequest, apiKey: String): ProviderHttpRequest {
        val body = JsonObject().apply {
            addProperty("model", config.model)
            add("messages", messages(request))
        }
        return ProviderHttpRequest(endpoint("chat/completions"), mapOf("Authorization" to "Bearer $apiKey"), jsonBody(body), config.timeout)
    }

    override fun parseText(root: com.google.gson.JsonElement): String =
        root.asJsonObject.getAsJsonArray("choices")[0].asJsonObject
            .getAsJsonObject("message").get("content").asString
}

class GroqProviderAdapter(
    config: ProviderHttpConfig = ProviderHttpConfig(
        "llama-3.3-70b-versatile", URI.create("https://api.groq.com/openai/v1")
    ),
    secrets: ProviderSecretResolver,
    transport: ProviderHttpTransport = JdkProviderHttpTransport()
) : JsonHttpProviderAdapter(
    ProviderProfile(ProviderId("groq"), "Groq", chatCapabilities, config.priority, config.enabled),
    config, secrets, transport
) {
    override fun createHttpRequest(request: GenerationRequest, apiKey: String): ProviderHttpRequest {
        val body = JsonObject().apply {
            addProperty("model", config.model)
            add("messages", messages(request))
        }
        return ProviderHttpRequest(endpoint("chat/completions"), mapOf("Authorization" to "Bearer $apiKey"), jsonBody(body), config.timeout)
    }

    override fun parseText(root: com.google.gson.JsonElement): String =
        root.asJsonObject.getAsJsonArray("choices")[0].asJsonObject
            .getAsJsonObject("message").get("content").asString
}

class GeminiProviderAdapter(
    config: ProviderHttpConfig = ProviderHttpConfig(
        "gemini-2.5-flash", URI.create("https://generativelanguage.googleapis.com/v1beta")
    ),
    secrets: ProviderSecretResolver,
    transport: ProviderHttpTransport = JdkProviderHttpTransport()
) : JsonHttpProviderAdapter(
    ProviderProfile(ProviderId("gemini"), "Google Gemini", chatCapabilities, config.priority, config.enabled),
    config, secrets, transport
) {
    override fun createHttpRequest(request: GenerationRequest, apiKey: String): ProviderHttpRequest {
        val contents = JsonArray()
        request.session.messages.filter { it.role != MessageRole.SYSTEM }.forEach { item ->
            val role = if (item.role == MessageRole.ASSISTANT) "model" else "user"
            contents.add(JsonObject().apply {
                addProperty("role", role)
                add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", item.content) }) })
            })
        }
        contents.add(JsonObject().apply {
            addProperty("role", "user")
            add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", request.prompt) }) })
        })
        val body = JsonObject().apply {
            add("systemInstruction", JsonObject().apply {
                add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", request.agent.systemPrompt) }) })
            })
            add("contents", contents)
        }
        return ProviderHttpRequest(
            endpoint("models/${config.model}:generateContent"),
            mapOf("x-goog-api-key" to apiKey),
            jsonBody(body),
            config.timeout
        )
    }

    override fun parseText(root: com.google.gson.JsonElement): String =
        root.asJsonObject.getAsJsonArray("candidates")[0].asJsonObject
            .getAsJsonObject("content").getAsJsonArray("parts")
            .joinToString("") { it.asJsonObject.get("text").asString }
}
