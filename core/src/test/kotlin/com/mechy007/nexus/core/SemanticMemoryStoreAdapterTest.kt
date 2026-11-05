package com.mechy007.nexus.core

import java.nio.file.Files
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SemanticMemoryStoreAdapterTest {
    @TempDir lateinit var tempDir: Path

    private class Store : MemoryStore {
        private val data = linkedMapOf<String, MemoryRecord>()
        override suspend fun search(query: String, limit: Int) = data.values.take(limit)
        override suspend fun put(record: MemoryRecord) { data[record.id] = record }
        override suspend fun delete(id: String) { data.remove(id) }
        override suspend fun exportAll() = data.values.toList()
    }

    private class Embeddings : EmbeddingProvider {
        override suspend fun embed(text: String): FloatArray =
            if (text.contains("cat", true) || text.contains("kitten", true))
                floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f)
    }

    @Test fun adapterProvidesSemanticSearchAndKeepsMemoryPortBehavior() = runSuspend {
        val store = Store()
        val service = SemanticMemoryService(store, Embeddings(),
            JsonFileVectorIndex(tempDir.resolve("vectors.json").toFile()))
        val adapter = SemanticMemoryStoreAdapter(store, service)
        adapter.put(MemoryRecord("cat", "I have a cat"))
        adapter.put(MemoryRecord("dog", "I have a dog"))

        assertEquals(listOf("cat"), adapter.search("kitten", 1).map { it.id })
        assertEquals(2, adapter.exportAll().size)
        adapter.delete("cat")
        assertEquals(listOf("dog"), adapter.exportAll().map { it.id })
    }

    @Test fun blankQueriesAndZeroLimitsReturnNoResults() = runSuspend {
        val store = Store()
        val adapter = SemanticMemoryStoreAdapter(store,
            SemanticMemoryService(store, Embeddings(), JsonFileVectorIndex(tempDir.resolve("empty.json").toFile())))
        adapter.put(MemoryRecord("cat", "I have a cat"))
        assertEquals(emptyList(), adapter.search("", 5))
        assertEquals(emptyList(), adapter.search("kitten", 0))
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
