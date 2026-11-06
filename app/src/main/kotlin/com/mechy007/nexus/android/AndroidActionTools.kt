package com.mechy007.nexus.android

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mechy007.nexus.core.*
import java.io.File

/** App-private, bounded audit trail. It records action metadata, never argument contents. */
class AndroidActionAuditSink(context: Context) : AuditSink {
    private val file = File(context.applicationContext.filesDir, "nexus_action_audit.jsonl")
    private val lock = Any()
    private val gson = Gson()

    override suspend fun record(event: AuditEvent) {
        val row = JsonObject().apply {
            addProperty("actor", event.actor)
            addProperty("action", event.action)
            addProperty("target", event.target)
            addProperty("outcome", event.outcome)
            addProperty("occurredAt", event.occurredAt.toString())
        }
        synchronized(lock) {
            val retained = if (file.exists()) file.readLines(Charsets.UTF_8).takeLast(499) else emptyList()
            file.writeText((retained + gson.toJson(row)).joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
        }
    }
}

/** Device actions always require a fresh, visible, request-specific approval. */
object AndroidExplicitApprovalPermissionManager : PermissionManager {
    override suspend fun check(agentId: AgentId, toolId: ToolId): Boolean = false
    override suspend fun request(agentId: AgentId, toolId: ToolId, reason: String): GrantDecision =
        GrantDecision.REQUIRES_USER_ACTION
}

class AndroidLaunchAppTool(private val bridge: AndroidDeviceActionBridge) : ToolAdapter {
    override val id = ToolId("android.launch_app")
    override val description = "Open an installed app by its exact visible app name. Arguments: appName (string). Requires user approval."

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (request.arguments.keys != setOf("appName")) return ToolResult(false, errorCode = "INVALID_ARGUMENTS")
        val appName = request.arguments["appName"].orEmpty().trim()
        if (appName.isBlank() || appName.length > 120) return ToolResult(false, errorCode = "INVALID_APP_NAME")
        val matches = bridge.listLaunchableApps().filter { it.label.equals(appName, ignoreCase = true) }
        if (matches.size != 1) return ToolResult(false, errorCode = if (matches.isEmpty()) "APP_NOT_AVAILABLE" else "APP_NAME_AMBIGUOUS")
        val app = matches.single()
        return when (bridge.launchApp(app.packageName)) {
            AndroidDeviceActionBridge.ActionResult.Launched -> ToolResult(true, "Opened " + app.label + ".")
            AndroidDeviceActionBridge.ActionResult.NoHandler -> ToolResult(false, errorCode = "APP_NOT_LAUNCHABLE")
            is AndroidDeviceActionBridge.ActionResult.Rejected -> ToolResult(false, errorCode = "ACTION_REJECTED")
            is AndroidDeviceActionBridge.ActionResult.Failed -> ToolResult(false, errorCode = "ANDROID_LAUNCH_FAILED")
        }
    }
}

class AndroidOpenWebLinkTool(private val bridge: AndroidDeviceActionBridge) : ToolAdapter {
    override val id = ToolId("android.open_url")
    override val description = "Open one HTTP or HTTPS URL using Android's app chooser. Arguments: url (string). Requires user approval."

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (request.arguments.keys != setOf("url")) return ToolResult(false, errorCode = "INVALID_ARGUMENTS")
        return when (bridge.openWebLink(request.arguments["url"].orEmpty())) {
            AndroidDeviceActionBridge.ActionResult.Launched -> ToolResult(true, "Android opened the link chooser. The user still chooses the app.")
            AndroidDeviceActionBridge.ActionResult.NoHandler -> ToolResult(false, errorCode = "NO_URL_HANDLER")
            is AndroidDeviceActionBridge.ActionResult.Rejected -> ToolResult(false, errorCode = "URL_REJECTED")
            is AndroidDeviceActionBridge.ActionResult.Failed -> ToolResult(false, errorCode = "URL_OPEN_FAILED")
        }
    }
}

class AndroidShareTextTool(private val bridge: AndroidDeviceActionBridge) : ToolAdapter {
    override val id = ToolId("android.share_text")
    override val description = "Open Android's Sharesheet with text for the user to choose and send. Arguments: text (string). Does not send automatically. Requires user approval."

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (request.arguments.keys != setOf("text")) return ToolResult(false, errorCode = "INVALID_ARGUMENTS")
        return when (bridge.shareText(request.arguments["text"].orEmpty())) {
            AndroidDeviceActionBridge.ActionResult.Launched -> ToolResult(true, "Android Sharesheet opened. No message was sent by NEXUS.")
            AndroidDeviceActionBridge.ActionResult.NoHandler -> ToolResult(false, errorCode = "NO_SHARE_HANDLER")
            is AndroidDeviceActionBridge.ActionResult.Rejected -> ToolResult(false, errorCode = "SHARE_REJECTED")
            is AndroidDeviceActionBridge.ActionResult.Failed -> ToolResult(false, errorCode = "SHARE_FAILED")
        }
    }
}
