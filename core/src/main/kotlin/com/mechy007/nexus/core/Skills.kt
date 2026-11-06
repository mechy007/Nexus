package com.mechy007.nexus.core

/** Declarative metadata only: loading a skill never grants permission to run tools. */
data class SkillStep(val toolId: ToolId, val description: String = "") {
    init {
        require(toolId.value.matches(Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,191}"))) {
            "Skill step tool ID must be 1-192 safe characters."
        }
        require(description.length <= MAX_DESCRIPTION_CHARS) {
            "Skill step description is too long."
        }
    }

    companion object {
        const val MAX_DESCRIPTION_CHARS = 512
    }
}

data class SkillManifest(
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val steps: List<SkillStep> = emptyList(),
    val requiredCapabilities: Set<String> = emptySet(),
    val tags: Set<String> = emptySet()
) {
    init {
        require(name.matches(NAME_PATTERN)) {
            "Skill name must be 1-64 safe characters."
        }
        require(version.length <= MAX_VERSION_CHARS && version.matches(VERSION_PATTERN)) {
            "Skill version must use a valid numeric major.minor.patch version."
        }
        require(description.length <= MAX_DESCRIPTION_CHARS) {
            "Skill description is too long."
        }
        require(steps.size <= MAX_STEPS) { "Skill has too many steps." }
        require(requiredCapabilities.size <= MAX_CAPABILITIES &&
            requiredCapabilities.all { it.matches(CAPABILITY_PATTERN) }) {
            "Skill capabilities must use bounded namespace:action identifiers."
        }
        require(tags.size <= MAX_TAGS && tags.all { it.matches(TAG_PATTERN) }) {
            "Skill tags must be bounded safe identifiers."
        }
    }

    companion object {
        const val MAX_VERSION_CHARS = 64
        const val MAX_DESCRIPTION_CHARS = 4_096
        const val MAX_STEPS = 128
        const val MAX_CAPABILITIES = 64
        const val MAX_TAGS = 64

        private val NAME_PATTERN = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")
        private val VERSION_PATTERN = Regex(
            "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?(\\+[0-9A-Za-z.-]+)?"
        )
        private val CAPABILITY_PATTERN = Regex("[a-z][a-z0-9._-]{0,63}:[a-z][a-z0-9._-]{0,63}")
        private val TAG_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,31}")
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
