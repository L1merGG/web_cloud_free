package com.limer.antigooglity.tools

fun interface ToolExecutor {
    suspend fun execute(call: ToolCall): ToolResult
}
