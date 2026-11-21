package com.limer.antigooglity.model

import com.limer.antigooglity.tools.ToolCall

data class ModelResponse(
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
)
