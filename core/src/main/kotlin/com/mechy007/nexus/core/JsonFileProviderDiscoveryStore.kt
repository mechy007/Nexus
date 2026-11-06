package com.mechy007.nexus.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/** Last-known provider health state. It is historical data, not routing authority. */
data class ProviderDiscoverySnapshot(
    val checkedAt: Instant,
    val report: ProviderDiscoveryReport
)

interface ProviderDiscoveryStore {
    suspend fun save(snapshot: ProviderDiscoverySnapshot)
    suspend fun load(): ProviderDiscoverySnapshot?
    suspend fun clear()
}

/**
 * App-private, versioned JSON storage for the latest discovery snapshot.
 * Writes use a temporary file and atomic replacement where supported.
 */
class JsonFileProviderDiscoveryStore(private val file: File) : ProviderDiscoveryStore {
    private val lock = Any()

    override suspend fun save(snapshot: ProviderDiscoverySnapshot) = synchronized(lock) {
        val parent = file.absoluteFile.parentFile
        require(parent.exists() || parent.mkdirs()) { "Could not create provider discovery directory." }
        val root = JsonObject().apply {
            addProperty("schemaVersion", SCHEMA_VERSION)
            addProperty("checkedAt", snapshot.checkedAt.toString())
            add("entries", JsonArray().apply {
                snapshot.report.entries.forEach { entry ->
                    add(JsonObject().apply {
                        addProperty("providerId", entry.providerId.value)
                        addProperty("status", entry.status.name)
                        addProperty("latencyMillis", entry.latencyMillis)
                        addProperty("failureCode", entry.failureCode)
                    })
                }
            })
        }
        val temp = File.createTempFile("nexus-provider-health-", ".tmp", parent)
        try {
            temp.writeText(root.toString(), Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
        Unit
    }

    override suspend fun load(): ProviderDiscoverySnapshot? = synchronized(lock) {
        if (!file.exists()) return@synchronized null
        val root = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
        require(root.get("schemaVersion")?.asInt == SCHEMA_VERSION) { "Unsupported provider discovery schema version." }
        val entries = root.getAsJsonArray("entries").map { element ->
            val item = element.asJsonObject
            ProviderDiscoveryEntry(
                providerId = ProviderId(item.get("providerId").asString),
                status = ProviderDiscoveryStatus.valueOf(item.get("status").asString),
                latencyMillis = item.get("latencyMillis")?.takeUnless { it.isJsonNull }?.asLong,
                failureCode = item.get("failureCode")?.takeUnless { it.isJsonNull }?.asString
            )
        }
        require(entries.map { it.providerId }.distinct().size == entries.size) {
            "Provider discovery snapshot contains duplicate provider IDs."
        }
        ProviderDiscoverySnapshot(
            checkedAt = Instant.parse(root.get("checkedAt").asString),
            report = ProviderDiscoveryReport(entries)
        )
    }

    override suspend fun clear() = synchronized(lock) {
        if (file.exists() && !file.delete()) error("Could not delete provider discovery snapshot.")
        Unit
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
