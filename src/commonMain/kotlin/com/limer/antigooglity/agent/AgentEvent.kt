package com.limer.antigooglity.agent

import com.limer.antigooglity.tools.ToolCall
import com.limer.antigooglity.tools.ToolResult

open class AgentException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class MaxIterationsReachedException(message: String) : AgentException(message)

sealed interface AgentEvent {
    data object Started : AgentEvent

    data class Thinking(
        val iteration: Int,
    ) : AgentEvent

    data class ToolRequested(
        val call: ToolCall,
    ) : AgentEvent

    data class ToolCompleted(
        val result: ToolResult,
    ) : AgentEvent

    data class Completed(
        val response: String,
    ) : AgentEvent

    data class Failed(
        val error: Throwable,
    ) : AgentEvent {
        val message: String get() = error.message ?: error.toString()

        constructor(message: String) : this(AgentException(message))
    }
}
