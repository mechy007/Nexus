package com.mechy007.nexus.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlliumOpenAmikaAdaptationTest {
    @Test
    fun personaBuildsAlliumOwnedIdentityAndContext() {
        val prompt = AlliumPersona().buildSystemPrompt(
            AlliumPersonaContext(
                userName = "Alex",
                city = "Kochi",
                localTime = ZonedDateTime.of(2026, 11, 6, 10, 30, 0, 0, ZoneId.of("Asia/Kolkata")),
                scene = "At home",
                mood = "Curious",
                language = "English"
            )
        )
        assertTrue(prompt.contains("You are Allium"))
        assertTrue(prompt.contains("User's preferred name: Alex"))
        assertTrue(prompt.contains("Current scene: At home"))
        assertTrue(prompt.contains("Preferred response language: English"))
        assertFalse(prompt.contains("Amika"))
    }

    @Test
    fun protocolProtectsTurnWhenSpeechStartsAndCapturesInterruptionMarker() {
        val begun = AlliumConversationProtocol.reduce(AlliumTurnState(), AlliumTurnEvent.Begin("turn-1", "Hi"))
        val ready = AlliumConversationProtocol.reduce(
            begun, AlliumTurnEvent.SurfaceCompleted("turn-1", AlliumResponseMode.SAY, "Hello!")
        )
        val speaking = AlliumConversationProtocol.reduce(ready, AlliumTurnEvent.SpeechStarted("turn-1"))
        val interrupted = AlliumConversationProtocol.reduce(
            speaking, AlliumTurnEvent.Interrupted("turn-1", "Hello, I was just")
        )
        assertEquals(AlliumTurnPhase.SPEAKING, speaking.phase)
        assertTrue(speaking.protectedForHistory)
        assertEquals(AlliumTurnPhase.INTERRUPTED, interrupted.phase)
        assertEquals("Hello, I was just", interrupted.interruptionMarker)
    }

    @Test
    fun staleChunksFromPreviousTurnAreIgnored() {
        val current = AlliumConversationProtocol.reduce(AlliumTurnState(), AlliumTurnEvent.Begin("turn-2", "New turn"))
        assertEquals(current, AlliumConversationProtocol.reduce(current, AlliumTurnEvent.DeepChunk("turn-1", "stale")))
    }

    @Test
    fun failedTurnRetainsPartialTextAndExposesError() {
        val begun = AlliumConversationProtocol.reduce(AlliumTurnState(), AlliumTurnEvent.Begin("turn-3", "Question"))
        val partial = AlliumConversationProtocol.reduce(begun, AlliumTurnEvent.DeepChunk("turn-3", "Partial answer"))
        val failed = AlliumConversationProtocol.reduce(partial, AlliumTurnEvent.Failed("turn-3", "Provider disconnected"))
        assertEquals("Partial answer", failed.deepResponse)
        assertEquals(AlliumTurnPhase.FAILED, failed.phase)
        assertEquals("Provider disconnected", failed.errorMessage)
    }

    @Test
    fun historyWindowAdvancesInStableStepsAndAlwaysKeepsNewestMessage() {
        val messages = (1..26).map { NexusMessage(role = MessageRole.USER, content = "message-$it") }
        val selected = AlliumHistoryWindow.select(messages, keep = 15, step = 5)
        val bounded = AlliumHistoryWindow.select(messages, keep = 15, step = 5, maxChars = 20)
        assertEquals("message-11", selected.first().content)
        assertEquals("message-26", selected.last().content)
        assertEquals("message-26", bounded.last().content)
        assertTrue(bounded.size == 1 || bounded.sumOf { it.content.length } <= 20)
    }

    @Test
    fun recallDetectorOnlyMatchesExplicitRecallLanguage() {
        assertTrue(AlliumRecallIntentDetector.isRecallFollowUp("Do you remember what I said?"))
        assertTrue(AlliumRecallIntentDetector.isRecallFollowUp("I forgot what you told me"))
        assertFalse(AlliumRecallIntentDetector.isRecallFollowUp("What is the weather today?"))
    }

    @Test
    fun emptyHistoryWindowIsSafe() {
        assertTrue(AlliumHistoryWindow.select(emptyList()).isEmpty())
    }
}
