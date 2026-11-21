package com.limer.antigooglity.coordinator

import com.limer.antigooglity.tools.ToolCall
import com.limer.antigooglity.tools.ToolExecutor
import com.limer.antigooglity.tools.ToolResult
import com.limer.antigooglity.tools.terminal.CommandApprover
import com.limer.antigooglity.tools.terminal.TerminalCommand
import java.nio.file.Path

/**
 * Single entry point enforcing security policy and approval decisions before tool execution.
 *
 * Pipeline: ToolCall -> Security Policy -> Permission Check -> Approval Decision -> Tool Execution
 */
class PolicyEnforcingToolExecutor(
    val delegate: ToolExecutor,
    private val workspacePath: () -> Path,
    private val permissions: () -> SecurityPermissions,
    private val preset: () -> SecurityPreset,
    private val commandApprover: CommandApprover?,
    private val policy: SecurityPolicy = SecurityPolicy(),
) : ToolExecutor {

    override suspend fun execute(call: ToolCall): ToolResult {
        val currentWorkspace = workspacePath()
        val currentPermissions = permissions()
        val currentPreset = preset()

        val decision = policy.evaluate(
            call = call,
            permissions = currentPermissions,
            preset = currentPreset,
            workspaceRoot = currentWorkspace,
        )

        return when (decision.kind) {
            SecurityDecisionKind.DENIED -> {
                ToolResult(
                    toolCallId = call.id,
                    content = "Security violation: ${decision.reason}",
                    success = false,
                )
            }
            SecurityDecisionKind.UNKNOWN_TOOL -> {
                ToolResult(
                    toolCallId = call.id,
                    content = "Unknown tool: ${decision.reason}",
                    success = false,
                )
            }
            SecurityDecisionKind.REQUIRES_APPROVAL -> {
                val approver = commandApprover
                if (approver == null) {
                    ToolResult(
                        toolCallId = call.id,
                        content = "Security violation: Tool requires user approval, but no approver is configured: '${call.name}'",
                        success = false,
                    )
                } else {
                    val cmd = decision.approvalCommand
                        ?: TerminalCommand(command = call.name, args = listOf(call.arguments))
                    val approved = approver.requestApproval(cmd)
                    if (!approved) {
                        ToolResult(
                            toolCallId = call.id,
                            content = "Security violation: Command requires user approval: execution rejected by user for '${cmd.command}'",
                            success = false,
                        )
                    } else {
                        delegate.execute(call)
                    }
                }
            }
            SecurityDecisionKind.ALLOWED_IMMEDIATE -> {
                delegate.execute(call)
            }
        }
    }
}
