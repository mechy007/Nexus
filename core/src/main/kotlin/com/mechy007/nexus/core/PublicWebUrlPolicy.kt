package com.mechy007.nexus.core

import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** HTTPS-only URL validation plus best-effort public-address checks for outbound page fetches. */
object PublicWebUrlPolicy {
    fun validate(value: String): String {
        val uri = try { URI(value).normalize() } catch (_: Exception) {
            throw IllegalArgumentException("Invalid web URL.")
        }
        require(uri.scheme.equals("https", true)) { "Only HTTPS web pages are allowed." }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "URL must have a public host and no embedded credentials." }
        require(uri.port == -1 || uri.port == 443) { "Only the default HTTPS port is allowed." }
        val host = uri.host.lowercase()
        require(host != "localhost" && !host.endsWith(".localhost") &&
            !host.endsWith(".local") && !host.endsWith(".internal")) {
            "Local and internal hostnames are blocked."
        }
        return uri.toString()
    }

    suspend fun validateAndResolve(value: String): String {
        val normalized = validate(value)
        val host = URI(normalized).host
        val addresses = withContext(Dispatchers.IO) {
            try { InetAddress.getAllByName(host).toList() } catch (_: Exception) {
                throw IllegalArgumentException("Web host could not be resolved.")
            }
        }
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) {
            "Private, local, reserved and link-local network destinations are blocked."
        }
        return normalized
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false
            if (a == 100 && b in 64..127) return false
            if (a == 169 && b == 254) return false
            if (a == 172 && b in 16..31) return false
            if (a == 192 && (b == 168 || b == 0)) return false
            if (a == 198 && b in 18..19) return false
        } else if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xff
            if ((first and 0xfe) == 0xfc) return false
        }
        return true
    }
}
