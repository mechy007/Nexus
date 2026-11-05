package com.mechy007.nexus.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/** Durable, versioned trace storage. Trace payloads must remain free of prompts and secrets. */
class JsonFileTraceStore(private val file: File) : TraceStore {
    private val lock = Any()

    override suspend fun save(trace: AgentTrace) = synchronized(lock) {
        val records = readAll().associateBy { it.id }.toMutableMap()
        records[trace.id] = trace
        writeAll(records.values)
    }

    override suspend fun find(id: String): AgentTrace? = synchronized(lock) { readAll().firstOrNull { it.id == id } }

    override suspend fun recent(limit: Int): List<AgentTrace> {
        require(limit >= 0)
        return synchronized(lock) { readAll().sortedByDescending { it.startedAt }.take(limit) }
    }

    override suspend fun delete(id: String) = synchronized(lock) {
        writeAll(readAll().filterNot { it.id == id })
    }

    private fun readAll(): List<AgentTrace> {
        if (!file.exists()) return emptyList()
        val root = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
        require(root.get("schemaVersion")?.asInt == 1) { "Unsupported trace schema version." }
        return root.getAsJsonArray("traces").map { element ->
            val item = element.asJsonObject
            val steps = item.getAsJsonArray("steps").map { stepElement ->
                val step = stepElement.asJsonObject
                TraceStep(
                    id = step.get("id").asString, kind = TraceStepKind.valueOf(step.get("kind").asString),
                    occurredAt = Instant.parse(step.get("occurredAt").asString),
                    durationMillis = step.get("durationMillis").asLong,
                    providerId = step.get("providerId")?.takeUnless { it.isJsonNull }?.asString?.let(::ProviderId),
                    toolId = step.get("toolId")?.takeUnless { it.isJsonNull }?.asString?.let(::ToolId),
                    inputTokens = step.get("inputTokens")?.takeUnless { it.isJsonNull }?.asInt,
                    outputTokens = step.get("outputTokens")?.takeUnless { it.isJsonNull }?.asInt,
                    detail = step.get("detail")?.takeUnless { it.isJsonNull }?.asString
                )
            }
            AgentTrace(
                id = item.get("id").asString, sessionId = SessionId(item.get("sessionId").asString),
                agentId = AgentId(item.get("agentId").asString),
                startedAt = Instant.parse(item.get("startedAt").asString),
                completedAt = item.get("completedAt")?.takeUnless { it.isJsonNull }?.asString?.let(Instant::parse),
                steps = steps, succeeded = item.get("succeeded")?.takeUnless { it.isJsonNull }?.asBoolean
            )
        }
    }

    private fun writeAll(traces: Collection<AgentTrace>) {
        val parent = file.absoluteFile.parentFile
        require(parent.exists() || parent.mkdirs()) { "Could not create trace directory." }
        val root = JsonObject().apply {
            addProperty("schemaVersion", 1)
            add("traces", JsonArray().apply {
                traces.forEach { trace ->
                    add(JsonObject().apply {
                        addProperty("id", trace.id)
                        addProperty("sessionId", trace.sessionId.value)
                        addProperty("agentId", trace.agentId.value)
                        addProperty("startedAt", trace.startedAt.toString())
                        addProperty("completedAt", trace.completedAt?.toString())
                        addProperty("succeeded", trace.succeeded)
                        add("steps", JsonArray().apply {
                            trace.steps.forEach { step ->
                                add(JsonObject().apply {
                                    addProperty("id", step.id)
                                    addProperty("kind", step.kind.name)
                                    addProperty("occurredAt", step.occurredAt.toString())
                                    addProperty("durationMillis", step.durationMillis)
                                    addProperty("providerId", step.providerId?.value)
                                    addProperty("toolId", step.toolId?.value)
                                    addProperty("inputTokens", step.inputTokens)
                                    addProperty("outputTokens", step.outputTokens)
                                    addProperty("detail", step.detail)
                                })
                            }
                        })
                    })
                }
            })
        }
        val temp = File.createTempFile("nexus-traces-", ".tmp", parent)
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
    }
}
