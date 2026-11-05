package com.mechy007.nexus.core

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class LocalModelSpec(
    val id: String,
    val modelPath: String,
    val displayName: String = id,
    val preferredBackend: LocalInferenceBackend = LocalInferenceBackend.AUTO,
    val maxOutputTokens: Int = 1024
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))
        require(File(modelPath).isAbsolute) { "Local model path must be absolute." }
        require(displayName.isNotBlank())
        require(maxOutputTokens in 1..8192)
    }
}

enum class LocalInferenceBackend { AUTO, CPU, GPU, NPU }

/** Platform adapter implemented with Google's LiteRT-LM Engine/Conversation API. */
interface LiteRtLmEngine : AutoCloseable {
    suspend fun initialize(spec: LocalModelSpec)
    suspend fun generate(prompt: String, maxOutputTokens: Int): String
}

/**
 * Local provider adapter. The Android host supplies a LiteRT-LM engine implementation;
 * NEXUS core remains free of Android classes and native runtime dependencies.
 */
class LiteRtLmProviderAdapter(
    private val model: LocalModelSpec,
    private val engine: LiteRtLmEngine,
    priority: Int = 10
) : ProviderAdapter {
    override val profile = ProviderProfile(
        id = ProviderId("litert-local:${model.id}"),
        displayName = "On-device · ${model.displayName}",
        capabilities = ProviderCapabilities(text = true),
        priority = priority
    )
    private val initialized = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    suspend fun initialize() {
        check(!closed.get()) { "Local inference provider is closed." }
        if (initialized.compareAndSet(false, true)) {
            try {
                engine.initialize(model)
            } catch (error: Exception) {
                initialized.set(false)
                throw error
            }
        }
    }

    override suspend fun generate(request: GenerationRequest): GenerationResponse {
        check(initialized.get() && !closed.get()) { "Local inference provider is not ready." }
        val text = engine.generate(request.prompt, model.maxOutputTokens)
        require(text.isNotBlank()) { "Local model returned an empty response." }
        return GenerationResponse(profile.id, text, model.id)
    }

    override fun close() {
        if (closed.compareAndSet(false, true) && initialized.get()) engine.close()
    }

    fun isReady(): Boolean = initialized.get() && !closed.get()
}
