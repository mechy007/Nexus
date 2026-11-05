package com.mechy007.nexus.core

import java.security.MessageDigest
import java.util.ArrayDeque

/** Per-turn guard against repeated calls, A-B-A-B ping-pong, and runaway tool use.
 * Arguments are hashed and never included in diagnostic messages. */
class AgentLoopGuard(
    private val maxCalls: Int = 100,
    private val maxIdenticalCalls: Int = 2,
    private val pingPongWindow: Int = 4
) {
    private val callCounts = mutableMapOf<String, Int>()
    private val recentTools = ArrayDeque<String>()
    private var totalCalls = 0

    init {
        require(maxCalls > 0)
        require(maxIdenticalCalls > 0)
        require(pingPongWindow >= 4 && pingPongWindow % 2 == 0)
    }

    @Synchronized
    fun check(toolName: String, arguments: String): LoopGuardDecision {
        require(toolName.isNotBlank())
        totalCalls++
        if (totalCalls > maxCalls) return LoopGuardDecision.Blocked("Tool-call budget exceeded.")

        val fingerprint = sha256("$toolName|$arguments")
        val count = (callCounts[fingerprint] ?: 0) + 1
        callCounts[fingerprint] = count
        if (count > maxIdenticalCalls) {
            return LoopGuardDecision.Blocked("Repeated identical tool call detected.")
        }

        recentTools.addLast(toolName)
        while (recentTools.size > pingPongWindow) recentTools.removeFirst()
        if (recentTools.size == pingPongWindow) {
            val calls = recentTools.toList()
            val half = pingPongWindow / 2
            val first = calls.take(half)
            val second = calls.drop(half)
            if (first == second && first.distinct().size > 1) {
                return LoopGuardDecision.Blocked("Alternating tool-call loop detected.")
            }
        }
        return LoopGuardDecision.Allowed
    }

    @Synchronized fun reset() {
        callCounts.clear()
        recentTools.clear()
        totalCalls = 0
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

sealed interface LoopGuardDecision {
    data object Allowed : LoopGuardDecision
    data class Blocked(val reason: String) : LoopGuardDecision
}
