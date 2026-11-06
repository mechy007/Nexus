package com.mechy007.nexus.core

import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonFileProviderDiscoveryStoreTest {
    @Test
    fun snapshotRoundTripsAllStatusesAndMetadata() = runBlocking {
        val directory = Files.createTempDirectory("nexus-provider-health-test").toFile()
        try {
            val store = JsonFileProviderDiscoveryStore(directory.resolve("providers.json"))
            val checkedAt = Instant.parse("2026-11-06T12:30:00Z")
            val report = ProviderDiscoveryReport(
                listOf(
                    ProviderDiscoveryEntry(ProviderId("alpha"), ProviderDiscoveryStatus.AVAILABLE, 42),
                    ProviderDiscoveryEntry(ProviderId("beta"), ProviderDiscoveryStatus.UNAVAILABLE, 5, "TIMEOUT"),
                    ProviderDiscoveryEntry(ProviderId("gamma"), ProviderDiscoveryStatus.DISABLED),
                    ProviderDiscoveryEntry(ProviderId("delta"), ProviderDiscoveryStatus.NOT_CHECKABLE)
                )
            )

            store.save(ProviderDiscoverySnapshot(checkedAt, report))
            val restored = store.load()

            assertEquals(checkedAt, restored?.checkedAt)
            assertEquals(report, restored?.report)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingSnapshotReturnsNullAndClearRemovesSavedState() = runBlocking {
        val directory = Files.createTempDirectory("nexus-provider-health-test").toFile()
        try {
            val store = JsonFileProviderDiscoveryStore(directory.resolve("providers.json"))
            assertNull(store.load())

            store.save(
                ProviderDiscoverySnapshot(
                    Instant.parse("2026-11-06T12:30:00Z"),
                    ProviderDiscoveryReport(emptyList())
                )
            )
            assertTrue(store.load() != null)

            store.clear()
            assertNull(store.load())
        } finally {
            directory.deleteRecursively()
        }
    }
}
