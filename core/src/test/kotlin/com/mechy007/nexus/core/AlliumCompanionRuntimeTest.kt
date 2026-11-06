package com.mechy007.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlliumCompanionRuntimeTest {
    @Test
    fun listeningAndTranscriptAreRepresentedWithoutInventingAResponse() {
        val listening = AlliumCompanionRuntime.reduce(
            AlliumCompanionState(),
            AlliumCompanionEvent.ListeningStarted
        )
        val updated = AlliumCompanionRuntime.reduce(
            listening,
            AlliumCompanionEvent.TranscriptUpdated("Hello Allium")
        )

        assertEquals(AlliumPresence.LISTENING, updated.presence)
        assertEquals("Hello Allium", updated.transcript)
        assertEquals("", updated.responseText)
    }

    @Test
    fun responseChunksAccumulateAndCompletionReturnsToIdle() {
        val speaking = AlliumCompanionRuntime.reduce(
            AlliumCompanionState(),
            AlliumCompanionEvent.ResponseStarted("Hello")
        )
        val continued = AlliumCompanionRuntime.reduce(
            speaking,
            AlliumCompanionEvent.ResponseChunk(", there.")
        )
        val completed = AlliumCompanionRuntime.reduce(
            continued,
            AlliumCompanionEvent.ResponseCompleted
        )

        assertEquals("Hello, there.", continued.responseText)
        assertEquals(AlliumPresence.SPEAKING, continued.presence)
        assertEquals(AlliumPresence.IDLE, completed.presence)
        assertEquals(0f, completed.mouthOpen)
    }

    @Test
    fun mouthLevelIsClampedForRendererSafety() {
        val tooHigh = AlliumCompanionRuntime.reduce(
            AlliumCompanionState(presence = AlliumPresence.SPEAKING),
            AlliumCompanionEvent.SpeechMouthLevel(1.8f)
        )
        val tooLow = AlliumCompanionRuntime.reduce(
            tooHigh,
            AlliumCompanionEvent.SpeechMouthLevel(-0.4f)
        )

        assertEquals(1f, tooHigh.mouthOpen)
        assertEquals(0f, tooLow.mouthOpen)
    }

    @Test
    fun interruptionStopsMouthMovementAndErrorsRemainExplicit() {
        val interrupted = AlliumCompanionRuntime.reduce(
            AlliumCompanionState(presence = AlliumPresence.SPEAKING, mouthOpen = 0.7f),
            AlliumCompanionEvent.Interrupted
        )
        val failed = AlliumCompanionRuntime.reduce(
            interrupted,
            AlliumCompanionEvent.Failed("Speech service unavailable")
        )

        assertEquals(AlliumPresence.INTERRUPTED, interrupted.presence)
        assertEquals(0f, interrupted.mouthOpen)
        assertEquals(AlliumPresence.ERROR, failed.presence)
        assertEquals("Speech service unavailable", failed.errorMessage)
    }

    @Test
    fun resetRestoresTheNeutralState() {
        val failed = AlliumCompanionRuntime.reduce(
            AlliumCompanionState(errorMessage = "failure"),
            AlliumCompanionEvent.Reset
        )

        assertEquals(AlliumCompanionState(), failed)
        assertNull(failed.errorMessage)
    }
}
