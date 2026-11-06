package com.mechy007.nexus.core

import java.nio.file.Path
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class HybridMemoryStoreTest {
    @TempDir lateinit var tempDir: Path

    private class Embeddings : EmbeddingProvider {
        override suspend fun embed(text: String): FloatArray = when {
            text.contains("cat", true) || text.contains("kitten", true) -> floatArrayOf(1f, 0f)
            text.contains("dog", true) || text.contains("puppy", true) -> floatArrayOf(0f, 1f)
            else -> floatArrayOf(0.7f, 0.7f)
        }
    }

    @Test fun combinesLexicalAndSemanticCandidatesWithoutLosingAttribution() = runHybridSuspend {
        val source = JsonFileMemoryStore(tempDir.resolve("memories.json").toFile())
        val index = JsonFileVectorIndex(tempDir.resolve("vectors.json").toFile())
        val semantic = SemanticMemoryService(source, Embeddings(), index)
        semantic.put(
            MemoryRecord(
                id = "cat-fact",
                text = "The user has a cat named Miso",
                tags = setOf("pet"),
                sourceSessionId = SessionId("session-1"),
                scope = MemoryScope.USER
            )
        )
        semantic.put(
            MemoryRecord(
                id = "dog-fact",
                text = "The user has a dog",
                tags = setOf("pet"),
                scope = MemoryScope.USER
            )
        )
        semantic.put(
            MemoryRecord(
                id = "agent-fact",
                text = "The assistant prefers kitten examples",
                scope = MemoryScope.AGENT,
                scopeId = "allium"
            )
        )

        val hybrid = HybridMemoryStore(source, semantic)
        val results = hybrid.search(
            MemorySearchOptions(query = "kitten", limit = 5, scopes = setOf(MemoryScope.USER))
        )

        assertTrue(results.any { it.id == "cat-fact" })
        assertTrue(results.none { it.id == "agent-fact" })
        val cat = results.first { it.id == "cat-fact" }
        assertEquals(setOf("pet"), cat.tags)
        assertEquals(SessionId("session-1"), cat.sourceSessionId)
    }

    @Test fun emptyQueryAndZeroLimitReturnNoResults() = runHybridSuspend {
        val source = JsonFileMemoryStore(tempDir.resolve("empty.json").toFile())
        val semantic = SemanticMemoryService(source, Embeddings(), JsonFileVectorIndex(tempDir.resolve("vectors.json").toFile()))
        val hybrid = HybridMemoryStore(source, semantic)
        assertTrue(hybrid.search(MemorySearchOptions(query = " ", limit = 5)).isEmpty())
        assertTrue(hybrid.search(MemorySearchOptions(query = "cat", limit = 0)).isEmpty())
    }
}

private fun <T> runHybridSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    val latch = java.util.concurrent.CountDownLatch(1)
    block.startCoroutine(object : kotlin.coroutines.Continuation<T> {
        override val context = kotlin.coroutines.EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) {
            result = value
            latch.countDown()
        }
    })
    check(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
    return requireNotNull(result).getOrThrow()
}
