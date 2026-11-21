package com.limer.antigooglity.tools

/**
 * Composite [ToolExecutor] that delegates tool calls to a list of registered executors.
 *
 * Each registered executor is queried in order. If an executor indicates that the tool
 * is unknown (via an "Unknown tool:" prefix), delegation continues to the next executor.
 */
class CompositeToolExecutor(
    private val executors: List<ToolExecutor>,
) : ToolExecutor {

    constructor(vararg executors: ToolExecutor) : this(executors.toList())

    override suspend fun execute(call: ToolCall): ToolResult {
        var lastUnknownResult: ToolResult? = null
        for (executor in executors) {
            val result = executor.execute(call)
            if (!result.content.startsWith("Unknown tool:")) {
                return result
            }
            lastUnknownResult = result
        }
        return lastUnknownResult ?: ToolResult(
            toolCallId = call.id,
            content = "No executor available for tool: '${call.name}'",
            success = false,
        )
    }
}
