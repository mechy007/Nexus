package com.mechy007.nexus.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Narrow Android action boundary for user-visible, foreground device actions.
 *
 * This bridge deliberately does not send messages, change system settings, or run
 * actions in the background. Callers must present the requested action to the user
 * and invoke it only after the appropriate confirmation.
 */
class AndroidDeviceActionBridge(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    data class LaunchableApp(
        val packageName: String,
        val label: String
    )

    sealed interface ActionResult {
        data object Launched : ActionResult
        data object NoHandler : ActionResult
        data class Rejected(val reason: String) : ActionResult
        data class Failed(val reason: String) : ActionResult
    }

    /** Returns apps Android exposes through the launcher intent, sorted by display name. */
    fun listLaunchableApps(): List<LaunchableApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                if (activity.packageName == appContext.packageName) return@mapNotNull null
                LaunchableApp(
                    packageName = activity.packageName,
                    label = info.loadLabel(packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Launches only an installed app with a launcher entry; never silently installs anything. */
    fun launchApp(packageName: String): ActionResult {
        if (packageName.isBlank() || packageName.length > 255) {
            return ActionResult.Rejected("Invalid application identifier.")
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult.NoHandler
        return runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(launchIntent)
            ActionResult.Launched
        }.getOrElse { ActionResult.Failed("Android could not launch that app.") }
    }

    /**
     * Opens an explicitly supplied web link through Android's app chooser.
     * Only HTTP(S) URLs are accepted; the user chooses the destination app.
     */
    fun openWebLink(rawUrl: String): ActionResult {
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull()
            ?: return ActionResult.Rejected("Invalid link.")
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.toString().length > 4096
        ) {
            return ActionResult.Rejected("Only valid HTTP or HTTPS links are allowed.")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(packageManager) == null) return ActionResult.NoHandler
        return runCatching {
            appContext.startActivity(Intent.createChooser(intent, "Open link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult.Launched
        }.getOrElse { ActionResult.Failed("Android could not open that link.") }
    }

    /**
     * Shares text through the Android Sharesheet. This does not send the content;
     * the user must choose an app and complete the send themselves.
     */
    fun shareText(text: String): ActionResult {
        if (text.isBlank() || text.length > 20_000) {
            return ActionResult.Rejected("Text must contain 1–20,000 characters.")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return runCatching {
            appContext.startActivity(
                Intent.createChooser(send, "Share with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ActionResult.Launched
        }.getOrElse { ActionResult.Failed("Android could not open the share sheet.") }
    }
}
