package com.mechy007.nexus.core

/** Android-independent turn contract adapted from OpenAmika's streamed conversation protocol. */
enum class AlliumResponseMode { SAY, THINK, SAY_AND_THINK }
enum class AlliumTurnPhase { IDLE, GENERATING, AWAITING_SPEECH, SPEAKING, COMPLETED, INTERRUPTED, FAILED }

data class AlliumTurnState(
    val turnId: String? = null,
    val userText: String = "",
    val phase: AlliumTurnPhase = AlliumTurnPhase.IDLE,
    val mode: AlliumResponseMode? = null,
    val surfaceDraft: String = "",
    val surfaceReply: String = "",
    val deepResponse: String = "",
    val protectedForHistory: Boolean = false,
    /** Short fragment being spoken when interrupted, not the whole transcript. */
    val interruptionMarker: String? = null,
    val errorMessage: String? = null
)

sealed interface AlliumTurnEvent {
    data class Begin(val turnId: String, val userText: String) : AlliumTurnEvent
    data class SurfaceChunk(val turnId: String, val text: String) : AlliumTurnEvent
    data class SurfaceCompleted(val turnId: String, val mode: AlliumResponseMode, val reply: String) : AlliumTurnEvent
    data class DeepChunk(val turnId: String, val text: String) : AlliumTurnEvent
    data class DeepCompleted(val turnId: String) : AlliumTurnEvent
    data class SpeechStarted(val turnId: String) : AlliumTurnEvent
    data class Interrupted(val turnId: String, val currentlySpokenFragment: String) : AlliumTurnEvent
    data class Failed(val turnId: String, val message: String) : AlliumTurnEvent
    data class Completed(val turnId: String) : AlliumTurnEvent
}

/**
 * Deterministic reducer. Late events from older turns are ignored. SpeechStarted protects
 * the turn for history, matching OpenAmika's protect-before-TTS convention.
 */
object AlliumConversationProtocol {
    fun reduce(state: AlliumTurnState, event: AlliumTurnEvent): AlliumTurnState {
        if (event is AlliumTurnEvent.Begin) {
            require(event.turnId.isNotBlank()) { "Turn id cannot be blank." }
            require(event.userText.isNotBlank()) { "User text cannot be blank." }
            return AlliumTurnState(turnId = event.turnId, userText = event.userText, phase = AlliumTurnPhase.GENERATING)
        }
        if (event.turnIdOrNull() != state.turnId || state.turnId == null) return state

        return when (event) {
            is AlliumTurnEvent.Begin -> state
            is AlliumTurnEvent.SurfaceChunk -> state.copy(
                phase = AlliumTurnPhase.GENERATING,
                surfaceDraft = state.surfaceDraft + event.text
            )
            is AlliumTurnEvent.SurfaceCompleted -> state.copy(
                mode = event.mode,
                surfaceReply = event.reply,
                phase = if (event.mode == AlliumResponseMode.SAY) AlliumTurnPhase.AWAITING_SPEECH else AlliumTurnPhase.GENERATING
            )
            is AlliumTurnEvent.DeepChunk -> state.copy(
                phase = AlliumTurnPhase.GENERATING,
                deepResponse = state.deepResponse + event.text
            )
            is AlliumTurnEvent.DeepCompleted -> state.copy(phase = AlliumTurnPhase.AWAITING_SPEECH)
            is AlliumTurnEvent.SpeechStarted -> state.copy(
                phase = AlliumTurnPhase.SPEAKING,
                protectedForHistory = true
            )
            is AlliumTurnEvent.Interrupted -> state.copy(
                phase = AlliumTurnPhase.INTERRUPTED,
                protectedForHistory = true,
                interruptionMarker = event.currentlySpokenFragment.trim().take(160)
            )
            is AlliumTurnEvent.Failed -> state.copy(
                phase = AlliumTurnPhase.FAILED,
                errorMessage = event.message.take(500)
            )
            is AlliumTurnEvent.Completed -> state.copy(phase = AlliumTurnPhase.COMPLETED)
        }
    }

    private fun AlliumTurnEvent.turnIdOrNull(): String = when (this) {
        is AlliumTurnEvent.Begin -> turnId
        is AlliumTurnEvent.SurfaceChunk -> turnId
        is AlliumTurnEvent.SurfaceCompleted -> turnId
        is AlliumTurnEvent.DeepChunk -> turnId
        is AlliumTurnEvent.DeepCompleted -> turnId
        is AlliumTurnEvent.SpeechStarted -> turnId
        is AlliumTurnEvent.Interrupted -> turnId
        is AlliumTurnEvent.Failed -> turnId
        is AlliumTurnEvent.Completed -> turnId
    }
}
