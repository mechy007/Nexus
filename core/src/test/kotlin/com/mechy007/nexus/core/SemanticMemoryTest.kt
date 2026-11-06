package com.mechy007.nexus.core

import java.nio.file.Path
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class SemanticMemoryTest {
    @TempDir lateinit var tempDir: Path

    private class MemoryFake : MemoryStore {
        val records = linkedMapOf<String, MemoryRecord>()
        override suspend fun search(query: String, limit: Int) = records.values.take(limit)
        override suspend fun put(record: MemoryRecord) { records[record.id] = record }
        override suspend fun delete(id: String) { records.remove(id) }
        override suspend fun exportAll() = records.values.toList()
    }
    private class Embeddings : EmbeddingProvider {
        override suspend fun embed(text: String): FloatArray = when {
            text.contains("cat", true) || text.contains("kitten", true) -> floatArrayOf(1f, 0f, 0f)
            text.contains("dog", true) || text.contains("puppy", true) -> floatArrayOf(0f, 1f, 0f)
            else -> floatArrayOf(0f, 0f, 1f)
        }
    }

    @Test fun semanticSearchRanksByCosineAndHonorsScope() = runSuspend {
        val memory = MemoryFake()
        val service = SemanticMemoryService(memory, Embeddings(), JsonFileVectorIndex(tempDir.resolve("vectors.json").toFile()))
        service.put(MemoryRecord("cat", "I have a cat", scope = MemoryScope.USER))
        service.put(MemoryRecord("dog", "I have a dog", scope = MemoryScope.AGENT, scopeId = "lara"))
        val all = service.search(MemorySearchOptions("kitten", limit = 5))
        assertEquals("cat", all.first().record.id)
        val scoped = service.search(MemorySearchOptions("kitten", limit = 5, scopes = setOf(MemoryScope.AGENT), scopeId = "lara"))
        assertEquals(listOf("dog"), scoped.map { it.record.id })
    }

    @Test fun semanticSearchBoundsVectorCandidates() = runSuspend {
        class RecordingIndex : VectorIndex {
            var requestedLimit: Int? = null
            override suspend fun upsert(id: String, vector: FloatArray) = Unit
            override suspend fun search(vector: FloatArray, limit: Int): List<VectorMatch> {
                requestedLimit = limit
                return emptyList()
            }
            override suspend fun delete(id: String) = Unit
            override suspend fun clear() = Unit
        }
        val index = RecordingIndex()
        val service = SemanticMemoryService(MemoryFake(), Embeddings(), index, candidateMultiplier = 4, maxCandidates = 25)
        assertTrue(service.search(MemorySearchOptions("kitten", limit = 3)).isEmpty())
        assertEquals(12, index.requestedLimit)
        service.search(MemorySearchOptions("kitten", limit = 20))
        assertEquals(25, index.requestedLimit)
    }

    @Test fun semanticSearchSkipsEmbeddingForBlankOrNonPositiveLimit() = runSuspend {
        var embeddingCalls = 0
        val embeddings = EmbeddingProvider { embeddingCalls++; floatArrayOf(1f, 0f) }
        val service = SemanticMemoryService(MemoryFake(), embeddings, JsonFileVectorIndex(tempDir.resolve("empty-vectors.json").toFile()))
        assertTrue(service.search(MemorySearchOptions(" ", limit = 5)).isEmpty())
        assertTrue(service.search(MemorySearchOptions("cat", limit = 0)).isEmpty())
        assertEquals(0, embeddingCalls)
    }

    @Test fun vectorIndexSurvivesRestartAndDeletes() = runSuspend {
        val file = tempDir.resolve("persistent-vectors.json").toFile()
        val index = JsonFileVectorIndex(file)
        index.upsert("a", floatArrayOf(1f, 0f))
        index.upsert("b", floatArrayOf(0f, 1f))
        val reopened = JsonFileVectorIndex(file)
        assertEquals("a", reopened.search(floatArrayOf(0.9f, 0.1f)).first().id)
        reopened.delete("a")
        assertEquals(listOf("b"), reopened.search(floatArrayOf(1f, 0f)).map { it.id })
    }

    @Test fun rejectsInvalidVectors() = runSuspend {
        val index = JsonFileVectorIndex(tempDir.resolve("invalid.json").toFile())
        assertFailsWith<IllegalArgumentException> { index.upsert("zero", floatArrayOf(0f, 0f)) }
        assertFailsWith<IllegalArgumentException> { index.search(floatArrayOf(Float.NaN)) }
        assertTrue(index.search(floatArrayOf(1f, 0f)).isEmpty())
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    val latch = java.util.concurrent.CountDownLatch(1)
    block.startCoroutine(object : kotlin.coroutines.Continuation<T> {
        override val context = kotlin.coroutines.EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) { result = value; latch.countDown() }
    })
    check(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
    return requireNotNull(result).getOrThrow()
}
