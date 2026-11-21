package com.limer.antigooglity.agent

import com.limer.antigooglity.tools.ToolCall

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

data class AgentMessage(
    val role: MessageRole,
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val isError: Boolean? = null,
)
