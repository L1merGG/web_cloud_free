package com.limer.antigooglity.coordinator

import com.limer.antigooglity.agent.AgentEvent
import com.limer.antigooglity.tools.ToolResult
import com.limer.antigooglity.tools.terminal.TerminalCommand
import java.util.UUID

enum class ExecutionStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

enum class ToolStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class UiToolExecution(
    val toolCallId: String,
    val toolName: String,
    val arguments: String,
    val status: ToolStatus = ToolStatus.RUNNING,
    val result: ToolResult? = null,
)

enum class ApprovalDecision {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
}

data class UiApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val command: TerminalCommand,
    val decision: ApprovalDecision = ApprovalDecision.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class UiMessageRole {
    USER,
    ASSISTANT,
}

data class UiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: UiMessageRole,
    val text: String,
    val toolExecutions: List<UiToolExecution> = emptyList(),
    val approvalRequests: List<UiApprovalRequest> = emptyList(),
    val events: List<AgentEvent> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Новый диалог",
    val messages: List<UiMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
