package com.mechy007.nexus.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mechy007.nexus.core.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AndroidProviderSettings(
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val model: String
)

/** Stores provider credentials encrypted with a non-exportable Android Keystore AES key. */
class AndroidProviderSecretStore(context: Context) : ProviderSecretResolver {
    private val preferences = context.applicationContext.getSharedPreferences("nexus_provider_secrets", Context.MODE_PRIVATE)
    private val alias = "nexus.provider.credentials.v1"

    override fun resolve(providerId: ProviderId): String? {
        val encoded = preferences.getString(providerId.value, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > 12) { "Encrypted credential is invalid." }
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    fun save(providerId: String, secret: String) {
        require(secret.isNotBlank()) { "API key cannot be blank." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(providerId, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not save encrypted provider credentials."
        }
    }

    fun delete(providerId: String) {
        check(preferences.edit().remove(providerId).commit()) { "Could not remove provider credentials." }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

/** Android transport for OpenAI-compatible chat-completions providers. */
private class AndroidChatProviderAdapter(
    private val settings: AndroidProviderSettings,
    private val secrets: ProviderSecretResolver
) : ProviderAdapter {
    override val profile = ProviderProfile(
        id = ProviderId(settings.providerId),
        displayName = settings.displayName,
        capabilities = ProviderCapabilities(text = true),
        priority = 10
    )

    override suspend fun generate(request: GenerationRequest): GenerationResponse = withContext(Dispatchers.IO) {
        val apiKey = secrets.resolve(profile.id)?.takeIf(String::isNotBlank)
            ?: throw ProviderAuthenticationException(profile.id)
        val base = settings.baseUrl.trimEnd('/')
        val endpoint = URL("$base/chat/completions")
        val body = JsonObject().apply {
            addProperty("model", settings.model)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", request.agent.systemPrompt)
                })
                val history = request.session.messages.let { messages ->
                    if (messages.lastOrNull()?.role == MessageRole.USER &&
                        messages.lastOrNull()?.content == request.prompt
                    ) messages.dropLast(1) else messages
                }
                history.forEach { message ->
                    val role = when (message.role) {
                        MessageRole.SYSTEM -> "system"
                        MessageRole.USER, MessageRole.TOOL -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                    }
                    add(JsonObject().apply {
                        addProperty("role", role)
                        addProperty("content", message.content)
                    })
                }
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", request.prompt)
                })
            })
        }
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw ProviderHttpException(profile.id, status)
            }
            val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JsonParser.parseString(responseBody).asJsonObject
            val text = root.getAsJsonArray("choices")
                ?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")
                ?.get("content")?.asString.orEmpty()
            if (text.isBlank()) throw ProviderResponseException(profile.id)
            GenerationResponse(profile.id, text, settings.model)
        } finally {
            connection.disconnect()
        }
    }
}

/** Android composition root: provider adapter -> NEXUS router -> coordinator -> durable sessions. */
object AndroidNexusRuntime {
    private val agents = object : AgentRegistry {
        private val allium = AgentProfile(
            id = AgentId("allium"),
            displayName = "Allium",
            description = "NEXUS primary personal companion",
            systemPrompt = "You are Allium, the warm, capable personal AI companion inside NEXUS. Be clear, practical, honest about limitations, and never claim an action was completed unless it was verified. Ask before sensitive or irreversible actions."
        )
        override fun all() = listOf(allium)
        override fun find(id: AgentId) = allium.takeIf { it.id == id }
        override fun register(profile: AgentProfile) = Unit
    }

    private var registry: InMemoryProviderRegistry? = null
    private var coordinator: NexusCoordinator? = null
    private var settings: AndroidProviderSettings? = null

    @Synchronized
    fun configure(context: Context, providerSettings: AndroidProviderSettings, apiKey: String) {
        val app = context.applicationContext
        val secrets = AndroidProviderSecretStore(app)
        secrets.save(providerSettings.providerId, apiKey.trim())
        val providers = InMemoryProviderRegistry().apply {
            register(AndroidChatProviderAdapter(providerSettings, secrets))
        }
        registry = providers
        settings = providerSettings
        coordinator = NexusCoordinator(
            agents = agents,
            router = CapabilityAwareProviderRouter(providers),
            sessions = JsonFileSessionStore(app.getDir("nexus_sessions", Context.MODE_PRIVATE))
        )
        app.getSharedPreferences("nexus_provider_config", Context.MODE_PRIVATE).edit()
            .putString("provider_id", providerSettings.providerId)
            .putString("display_name", providerSettings.displayName)
            .putString("base_url", providerSettings.baseUrl)
            .putString("model", providerSettings.model)
            .apply()
    }

    @Synchronized
    fun restore(context: Context): Boolean {
        if (coordinator != null) return true
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("nexus_provider_config", Context.MODE_PRIVATE)
        val id = prefs.getString("provider_id", null) ?: return false
        val providerSettings = AndroidProviderSettings(
            providerId = id,
            displayName = prefs.getString("display_name", id) ?: id,
            baseUrl = prefs.getString("base_url", "") ?: "",
            model = prefs.getString("model", "") ?: ""
        )
        val keyStore = AndroidProviderSecretStore(app)
        if (keyStore.resolve(ProviderId(id)).isNullOrBlank()) return false
        val providers = InMemoryProviderRegistry().apply {
            register(AndroidChatProviderAdapter(providerSettings, keyStore))
        }
        registry = providers
        settings = providerSettings
        coordinator = NexusCoordinator(
            agents = agents,
            router = CapabilityAwareProviderRouter(providers),
            sessions = JsonFileSessionStore(app.getDir("nexus_sessions", Context.MODE_PRIVATE))
        )
        return true
    }

    fun isConfigured(context: Context): Boolean = restore(context)

    fun currentSettings(): AndroidProviderSettings? = settings

    suspend fun respond(message: String): String {
        val active = requireNotNull(coordinator) { "Configure an AI provider in Settings first." }
        return active.respond(
            sessionId = SessionId("nexus-primary-conversation"),
            agentId = AgentId("allium"),
            userText = message
        ).text
    }

    fun disconnect(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("nexus_provider_config", Context.MODE_PRIVATE)
        val id = prefs.getString("provider_id", null)
        if (id != null) AndroidProviderSecretStore(context).delete(id)
        prefs.edit().clear().apply()
        synchronized(this) {
            coordinator = null
            registry = null
            settings = null
        }
    }
}
