package com.mechy007.nexus.core

/** Declarative metadata only: loading a skill never grants permission to run tools. */
data class SkillStep(val toolId: ToolId, val description: String = "")

data class SkillManifest(
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val steps: List<SkillStep> = emptyList(),
    val requiredCapabilities: Set<String> = emptySet(),
    val tags: Set<String> = emptySet()
) {
    init {
        require(name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}"))) {
            "Skill name must be 1-64 safe characters."
        }
        require(version.isNotBlank()) { "Skill version cannot be blank." }
        require(requiredCapabilities.none { it.isBlank() }) {
            "Skill capabilities cannot contain blank values."
        }
    }
}

enum class SkillTrustTier { BUNDLED, WORKSPACE, SIGNED_CATALOG, UNREVIEWED }

object SkillTrust {
    fun classify(
        bundled: Boolean = false,
        workspace: Boolean = false,
        signatureVerified: Boolean = false,
        catalogEntryVerified: Boolean = false
    ): SkillTrustTier = when {
        bundled -> SkillTrustTier.BUNDLED
        workspace -> SkillTrustTier.WORKSPACE
        signatureVerified && catalogEntryVerified -> SkillTrustTier.SIGNED_CATALOG
        else -> SkillTrustTier.UNREVIEWED
    }
}

data class SkillAuthorization(
    val allowed: Boolean,
    val missingCapabilities: Set<String>,
    val dangerousCapabilities: Set<String>,
    val trustTier: SkillTrustTier
)

object SkillPolicy {
    val dangerousCapabilities = setOf(
        "shell:execute", "network:listen", "filesystem:write", "device:control"
    )

    fun authorize(
        manifest: SkillManifest,
        grantedCapabilities: Set<String>,
        trustTier: SkillTrustTier
    ): SkillAuthorization {
        val missing = manifest.requiredCapabilities - grantedCapabilities
        val dangerous = manifest.requiredCapabilities intersect dangerousCapabilities
        // Unreviewed skills remain denied even if a caller supplies broad grants.
        return SkillAuthorization(
            allowed = missing.isEmpty() && trustTier != SkillTrustTier.UNREVIEWED,
            missingCapabilities = missing,
            dangerousCapabilities = dangerous,
            trustTier = trustTier
        )
    }
}
