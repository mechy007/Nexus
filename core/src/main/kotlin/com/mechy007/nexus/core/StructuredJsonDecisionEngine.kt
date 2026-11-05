package com.mechy007.nexus.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * Converts a provider's strict JSON response into an AgentDecision.
 *
 * The model can propose a tool call, but this class never executes it or grants
 * permissions. AgentRuntime remains the only execution and authorization boundary.
 */
class StructuredJsonDecisionEngine(
    private val router: ProviderRouter,
    private val tools: ToolRegistry,
    private val preferredProvider: ProviderId? = null,
    private val maxConversationMessages: Int = 24,
    private val maxResponseChars: Int = 32_000
) : AgentDecisionEngine {
    init {
        require(maxConversationMessages > 0)
        require(maxResponseChars > 0)
    }

    override suspend fun decide(context: AgentTurnContext): AgentDecision {
        val prompt = buildPrompt(context)
        val response = router.generate(
            GenerationRequest(
                session = context.session,
                agent = context.agent,
                prompt = prompt,
                preferredProvider = preferredProvider,
                requiredCapabilities = ProviderCapabilities(text = true)
            )
        )
        require(response.text.length <= maxResponseChars) { "Decision response exceeds configured limit." }
        val root = try {
            JsonParser.parseString(response.text).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        } ?: throw IllegalArgumentException("Decision engine returned invalid JSON.")
        return when (root.get("type")?.takeIf { it.isJsonPrimitive }?.asString) {
            "reply" -> {
                val text = root.get("text")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    ?: throw IllegalArgumentException("Reply decision requires a string text field.")
                AgentDecision.Reply(text)
            }
            "tool_call" -> parseToolCall(root, context)
            else -> throw IllegalArgumentException("Decision type must be reply or tool_call.")
        }
    }

    private fun parseToolCall(root: JsonObject, context: AgentTurnContext): AgentDecision {
        val toolName = root.get("tool")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Tool decision requires a non-empty tool name.")
        val toolId = ToolId(toolName)
        val registration = tools.find(toolId)?.takeIf { it.enabled }
            ?: throw IllegalArgumentException("Decision references an unavailable tool.")
        val rawArguments = root.get("arguments")
        require(rawArguments != null && rawArguments.isJsonObject) { "Tool arguments must be a JSON object." }
        val arguments = linkedMapOf<String, String>()
        rawArguments.asJsonObject.entrySet().forEach { (name, value) ->
            require(name.isNotBlank() && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                "Tool arguments must contain only named string values."
            }
            arguments[name] = value.asString
        }
        return AgentDecision.CallTool(
            ToolRequest(
                toolId = toolId,
                requestedBy = context.agent.id,
                sessionId = context.session.id,
                arguments = arguments
            )
        )
    }

    private fun buildPrompt(context: AgentTurnContext): String {
        val available = JsonArray()
        tools.all().filter { it.enabled }.forEach { registration ->
            available.add(JsonObject().apply {
                addProperty("name", registration.adapter.id.value)
                addProperty("description", registration.adapter.description)
                add("requiredCapabilities", JsonArray().apply {
                    registration.requiredCapabilities.sorted().forEach(::add)
                })
            })
        }
        val conversation = context.session.messages.takeLast(maxConversationMessages)
            .joinToString("\n") { message ->
                "${message.role.name}: ${message.content}"
            }
        val memories = context.relevantMemories.joinToString("\n") { "- ${it.text}" }
        return buildString {
            append("You are the decision engine for NEXUS agent ")
            append(context.agent.displayName)
            append(". Return exactly one JSON object and no Markdown.\n")
            append("For a normal response use: {\"type\":\"reply\",\"text\":\"...\"}.\n")
            append("To request a tool use: {\"type\":\"tool_call\",\"tool\":\"name\",\"arguments\":{\"key\":\"value\"}}.\n")
            append("Only request tools listed below. All argument values must be strings. Never claim a tool ran. ")
            append("Tool requests are independently checked for permission by NEXUS.\n")
            append("Available tools: ").append(available).append("\n")
            if (memories.isNotBlank()) append("Relevant saved memories:\n").append(memories).append("\n")
            append("Conversation:\n").append(conversation)
            append("\nReturn a single decision JSON object.")
        }
    }
}
