package com.mechy007.nexus.core

import java.util.Locale

/**
 * Stable, bounded conversation window inspired by OpenAmika's history_window.
 * The window advances in steps to preserve a reusable prompt prefix. The newest
 * message is always retained, even if that single message exceeds maxChars.
 */
object AlliumHistoryWindow {
    fun select(
        messages: List<NexusMessage>,
        endpoint: Int = messages.size,
        keep: Int = 15,
        step: Int = 5,
        maxChars: Int = 20_000
    ): List<NexusMessage> {
        require(keep > 0) { "keep must be positive." }
        require(step > 0) { "step must be positive." }
        require(maxChars > 0) { "maxChars must be positive." }
        val end = endpoint.coerceIn(0, messages.size)
        if (end == 0) return emptyList()

        val excess = (end - (keep + step)).coerceAtLeast(0)
        val start = if (excess == 0) 0 else step * ((excess + step - 1) / step)
        val selected = messages.subList(start.coerceAtMost(end), end).toMutableList()
        while (selected.size > 1 && selected.sumOf { it.content.length } > maxChars) {
            repeat(step.coerceAtMost(selected.size - 1)) { selected.removeAt(0) }
        }
        return selected.toList()
    }
}

/** Detects explicit recall questions so the host can prioritize memory retrieval. */
object AlliumRecallIntentDetector {
    private val recallCue = Regex(
        """\b(?:remember|recall|forgot|forget|told you|i\s+(?:said|told|mentioned)|did i\s+(?:say|tell|mention)|what did i\s+(?:say|tell))\b""",
        RegexOption.IGNORE_CASE
    )

    fun isRecallFollowUp(text: String): Boolean = recallCue.containsMatchIn(text.lowercase(Locale.ROOT))
}
