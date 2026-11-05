package com.mechy007.nexus.core

import java.nio.file.Path
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

class MemoryLifecycleTest {
    @TempDir lateinit var tempDir: Path

    private class MemoryFake : MemoryStore {
        val records = linkedMapOf<String, MemoryRecord>()
        override suspend fun search(query: String, limit: Int) = records.values.take(limit)
        override suspend fun put(record: MemoryRecord) { records[record.id] = record }
        override suspend fun delete(id: String) { records.remove(id) }
        override suspend fun exportAll() = records.values.toList()
    }

    private class Embeddings : EmbeddingProvider {
        override suspend fun embed(text: String): FloatArray =
            if (text.contains("python", true)) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f)
    }

    private fun session() = NexusSession(
        id = SessionId("s1"),
        messages = listOf(NexusMessage(role = MessageRole.USER, content = "I am learning Python for AI projects."))
    )
    private fun agent() = AgentProfile(AgentId("lara"), "Lara", "assistant", "prompt")

    @Test fun captureIsDisabledByDefault() = runSuspend {
        val memory = MemoryFake()
        var extracted = false
        val service = MemoryLifecycleService(
            memory, Embeddings(), JsonFileVectorIndex(tempDir.resolve("disabled.json").toFile()),
            MemoryCandidateExtractor { _, _ -> extracted = true; listOf(MemoryCandidate("I am learning Python.") ) }
        )
        val result = service.captureTurn(session(), agent())
        assertFalse(result.enabled)
        assertFalse(extracted)
        assertTrue(memory.records.isEmpty())
    }

    @Test fun captureDeduplicatesAndRejectsInvalidCandidates() = runSuspend {
        val memory = MemoryFake()
        val index = JsonFileVectorIndex(tempDir.resolve("capture.json").toFile())
        val service = MemoryLifecycleService(
            memory, Embeddings(), index,
            MemoryCandidateExtractor { _, _ -> listOf(
                MemoryCandidate("I am learning Python for AI projects.", setOf("Learning", "AI")),
                MemoryCandidate("  i AM learning   python FOR AI projects. "),
                MemoryCandidate("tiny"),
                MemoryCandidate("A".repeat(2_000))
            ) },
            MemoryCapturePolicy(enabled = true)
        )
        val result = service.captureTurn(session(), agent())
        assertEquals(4, result.extracted)
        assertEquals(1, result.saved)
        assertEquals(1, result.duplicates)
        assertEquals(2, result.rejected)
        assertEquals(1, memory.records.size)
        assertEquals(setOf("learning", "ai"), memory.records.values.first().tags)
        assertEquals(1, index.search(floatArrayOf(1f, 0f)).size)
    }

    @Test fun reindexRebuildsVectorsAndDeleteRemovesBothCopies() = runSuspend {
        val memory = MemoryFake()
        memory.put(MemoryRecord("one", "I am learning Python.", scope = MemoryScope.USER))
        memory.put(MemoryRecord("two", "I enjoy gardening.", scope = MemoryScope.USER))
        val index = JsonFileVectorIndex(tempDir.resolve("reindex.json").toFile())
        index.upsert("stale", floatArrayOf(0f, 1f))
        val service = MemoryLifecycleService(
            memory, Embeddings(), index, MemoryCandidateExtractor { _, _ -> emptyList() }
        )
        assertEquals(2, service.reindexAll())
        assertEquals(setOf("one", "two"), index.search(floatArrayOf(1f, 0f), 10).map { it.id }.toSet())
        service.delete("one")
        assertFalse(memory.records.containsKey("one"))
        assertFalse(index.search(floatArrayOf(1f, 0f), 10).any { it.id == "one" })
    }

    @Test fun providerExtractorParsesOnlyStringFacts() = runSuspend {
        val router = object : ProviderRouter {
            override suspend fun generate(request: GenerationRequest) = GenerationResponse(
                ProviderId("fake"),
                """{"memories":[{"text":"I am learning Python.","tags":["coding"]},{"text":4}]}"""
            )
        }
        val extractor = ProviderMemoryCandidateExtractor(router)
        val result = extractor.extract(session(), agent())
        assertEquals(listOf(MemoryCandidate("I am learning Python.", setOf("coding"))), result)
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    val result = AtomicReference<Result<T>>()
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) { result.set(value); latch.countDown() }
    })
    check(latch.await(5, TimeUnit.SECONDS)) { "Test timed out." }
    return result.get().getOrThrow()
}
