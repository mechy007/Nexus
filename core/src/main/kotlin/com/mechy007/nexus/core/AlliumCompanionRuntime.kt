package com.mechy007.nexus.core

/**
 * Platform-independent interaction state for Allium.
 *
 * Android audio, Live2D rendering and NEXUS intelligence adapters translate their
 * events into this model. This class deliberately performs no I/O and grants no
 * device permissions.
 */
enum class AlliumPresence {
    IDLE, LISTENING, THINKING, SPEAKING, INTERRUPTED, ERROR
}

enum class AlliumEmotion {
    NEUTRAL, HAPPY, CURIOUS, THINKING, SAD, SURPRISED, CONFIDENT, CONCERNED
}

data class AlliumCompanionState(
    val presence: AlliumPresence = AlliumPresence.IDLE,
    val emotion: AlliumEmotion = AlliumEmotion.NEUTRAL,
    val transcript: String = "",
    val responseText: String = "",
    /** Normalized mouth opening for Live2D adapters; always in 0.0..1.0. */
    val mouthOpen: Float = 0f,
    val errorMessage: String? = null
)

sealed interface AlliumCompanionEvent {
    data object ListeningStarted : AlliumCompanionEvent
    data class TranscriptUpdated(val text: String) : AlliumCompanionEvent
    data object ThinkingStarted : AlliumCompanionEvent
    data class EmotionChanged(val emotion: AlliumEmotion) : AlliumCompanionEvent
    data class ResponseStarted(val initialText: String = "") : AlliumCompanionEvent
    data class ResponseChunk(val text: String) : AlliumCompanionEvent
    data class SpeechMouthLevel(val level: Float) : AlliumCompanionEvent
    data object ResponseCompleted : AlliumCompanionEvent
    data object Interrupted : AlliumCompanionEvent
    data class Failed(val message: String) : AlliumCompanionEvent
    data object Reset : AlliumCompanionEvent
}

/**
 * Deterministic reducer shared by the Android host and tests.
 * It describes what Allium is doing; it does not pretend to perform AI or audio work.
 */
object AlliumCompanionRuntime {
    fun reduce(
        state: AlliumCompanionState,
        event: AlliumCompanionEvent
    ): AlliumCompanionState = when (event) {
        AlliumCompanionEvent.ListeningStarted -> state.copy(
            presence = AlliumPresence.LISTENING,
            emotion = AlliumEmotion.NEUTRAL,
            transcript = "",
            responseText = "",
            mouthOpen = 0f,
            errorMessage = null
        )
        is AlliumCompanionEvent.TranscriptUpdated -> state.copy(
            transcript = event.text,
            presence = AlliumPresence.LISTENING
        )
        AlliumCompanionEvent.ThinkingStarted -> state.copy(
            presence = AlliumPresence.THINKING,
            emotion = AlliumEmotion.THINKING,
            mouthOpen = 0f,
            errorMessage = null
        )
        is AlliumCompanionEvent.EmotionChanged -> state.copy(emotion = event.emotion)
        is AlliumCompanionEvent.ResponseStarted -> state.copy(
            presence = AlliumPresence.SPEAKING,
            responseText = event.initialText,
            mouthOpen = 0f,
            errorMessage = null
        )
        is AlliumCompanionEvent.ResponseChunk -> state.copy(
            presence = AlliumPresence.SPEAKING,
            responseText = state.responseText + event.text
        )
        is AlliumCompanionEvent.SpeechMouthLevel -> state.copy(
            presence = if (state.presence == AlliumPresence.ERROR) state.presence else AlliumPresence.SPEAKING,
            mouthOpen = event.level.coerceIn(0f, 1f)
        )
        AlliumCompanionEvent.ResponseCompleted -> state.copy(
            presence = AlliumPresence.IDLE,
            mouthOpen = 0f,
            emotion = AlliumEmotion.NEUTRAL
        )
        AlliumCompanionEvent.Interrupted -> state.copy(
            presence = AlliumPresence.INTERRUPTED,
            mouthOpen = 0f
        )
        is AlliumCompanionEvent.Failed -> state.copy(
            presence = AlliumPresence.ERROR,
            mouthOpen = 0f,
            errorMessage = event.message
        )
        AlliumCompanionEvent.Reset -> AlliumCompanionState()
    }
}
