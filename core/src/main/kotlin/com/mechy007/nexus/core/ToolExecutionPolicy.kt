package com.mechy007.nexus.core

/** Configurable resource limits applied immediately before invoking a tool. */
data class ToolExecutionPolicy(
    val timeoutMillis: Long = 30_000,
    val maxArguments: Int = 64,
    val maxArgumentNameChars: Int = 128,
    val maxArgumentValueChars: Int = 16_384,
    val maxTotalArgumentChars: Int = 32_768,
    val maxOutputChars: Int = 32_000
) {
    init {
        require(timeoutMillis > 0 && maxArguments > 0)
        require(maxArgumentNameChars > 0 && maxArgumentValueChars > 0)
        require(maxTotalArgumentChars > 0 && maxOutputChars > 0)
    }

    fun validate(request: ToolRequest) {
        require(request.arguments.size <= maxArguments) { "Tool argument count limit exceeded." }
        var total = 0L
        request.arguments.forEach { (name, value) ->
            require(name.isNotBlank() && name.length <= maxArgumentNameChars) { "Invalid tool argument name." }
            require(value.length <= maxArgumentValueChars) { "Tool argument value limit exceeded." }
            total += name.length.toLong() + value.length
        }
        require(total <= maxTotalArgumentChars) { "Tool argument payload limit exceeded." }
    }

    fun bound(result: ToolResult): ToolResult =
        if (result.output.length <= maxOutputChars) result
        else result.copy(output = result.output.take(maxOutputChars))
}
