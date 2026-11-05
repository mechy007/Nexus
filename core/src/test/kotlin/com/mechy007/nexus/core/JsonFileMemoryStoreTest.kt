package com.mechy007.nexus.core

import java.nio.file.Path
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class JsonFileMemoryStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun persistsRecordsAcrossStoreInstancesAndSupportsExportAndDelete() = runMemorySuspend {
        val file = tempDir.resolve("memories.json").toFile()
        val store = JsonFileMemoryStore(file)
        val record = MemoryRecord(
            id = "pref-1",
            text = "Prefers concise answers",
            tags = setOf("style", "preference"),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            sourceSessionId = SessionId("session-1")
        )

        store.put(record)
        val reopened = JsonFileMemoryStore(file)
        assertEquals(listOf(record), reopened.exportAll())
        assertEquals(listOf(record), reopened.search("concise"))
        reopened.delete(record.id)
        assertTrue(reopened.exportAll().isEmpty())
    }

    @Test
    fun scopesAndExpirationAreAppliedBeforeRanking() = runMemorySuspend {
        val store = JsonFileMemoryStore(tempDir.resolve("scoped.json").toFile())
        val user = MemoryRecord(id = "user", text = "Likes science fiction", tags = setOf("books"))
        val agent = MemoryRecord(
            id = "agent", text = "Likes science fiction summaries",
            scope = MemoryScope.AGENT, scopeId = "lara"
        )
        val session = MemoryRecord(
            id = "session", text = "Likes science fiction films",
            scope = MemoryScope.SESSION, scopeId = "session-7"
        )
        val expired = MemoryRecord(
            id = "expired", text = "Likes science fiction classics",
            expiresAt = Instant.parse("2000-01-01T00:00:00Z")
        )
        listOf(user, agent, session, expired).forEach { store.put(it) }

        val lara = store.search(
            MemorySearchOptions(query = "science fiction", scopes = setOf(MemoryScope.AGENT), scopeId = "lara")
        )
        assertEquals(listOf(agent), lara)
        assertFalse(store.search("science fiction", 10).any { it.id == "expired" })
        assertTrue(
            store.search(MemorySearchOptions("science fiction", includeExpired = true))
                .any { it.id == "expired" }
        )
    }

    @Test
    fun rejectsUnscopedAgentAndSessionMemories() {
        val agentFailure = runCatching {
            MemoryRecord(id = "bad-agent", text = "private", scope = MemoryScope.AGENT)
        }
        val sessionFailure = runCatching {
            MemoryRecord(id = "bad-session", text = "temporary", scope = MemoryScope.SESSION)
        }
        assertTrue(agentFailure.isFailure)
        assertTrue(sessionFailure.isFailure)
    }

    @Test
    fun validatesSearchLimitAndReturnsNewestForBlankQuery() = runMemorySuspend {
        val store = JsonFileMemoryStore(tempDir.resolve("ordered.json").toFile())
        val older = MemoryRecord(id = "old", text = "Old note", createdAt = Instant.parse("2025-01-01T00:00:00Z"))
        val newer = MemoryRecord(id = "new", text = "New note", createdAt = Instant.parse("2026-01-01T00:00:00Z"))
        store.put(older)
        store.put(newer)
        assertEquals(listOf(newer), store.search(" ", 1))
        assertTrue(runCatching { store.search("note", -1) }.isFailure)
    }
}

private fun <T> runMemorySuspend(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    val result = AtomicReference<Result<T>>()
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) {
            result.set(value)
            latch.countDown()
        }
    })
    check(latch.await(5, TimeUnit.SECONDS)) { "Memory test timed out." }
    return result.get().getOrThrow()
}
