package com.limer.antigooglity.coordinator

import com.limer.antigooglity.agent.Agent
import com.limer.antigooglity.agent.AgentConfig
import com.limer.antigooglity.model.ModelAdapter
import com.limer.antigooglity.tools.CompositeToolExecutor
import com.limer.antigooglity.tools.ToolExecutor
import com.limer.antigooglity.tools.fs.FileSystemToolExecutor
import com.limer.antigooglity.tools.fs.WorkspaceConfig
import com.limer.antigooglity.tools.mock.MockToolExecutor
import com.limer.antigooglity.tools.terminal.CommandApprover
import com.limer.antigooglity.tools.terminal.TerminalConfig
import com.limer.antigooglity.tools.terminal.TerminalToolExecutor
import java.nio.file.Path

/**
 * Composition root that instantiates tool executors, configs, and the [Agent].
 */
object AgentFactory {

    data class AgentInstance(
        val agent: Agent,
        val toolExecutor: ToolExecutor,
        val workspaceConfig: WorkspaceConfig? = null,
        val terminalConfig: TerminalConfig? = null,
    )

    fun createAgent(
        workspacePath: Path,
        permissions: SecurityPermissions,
        commandApprover: CommandApprover?,
        modelAdapter: ModelAdapter,
        toolExecutor: ToolExecutor = MockToolExecutor(),
        agentConfig: AgentConfig = AgentConfig(
            systemPrompt = "Ты автономный AI-ассистент Antigooglity, помогающий в разработке ПО.",
        ),
    ): AgentInstance {
        val agent = Agent(
            modelAdapter = modelAdapter,
            toolExecutor = toolExecutor,
            config = agentConfig,
        )

        return AgentInstance(
            agent = agent,
            toolExecutor = toolExecutor,
        )
    }

    fun createRealToolExecutor(
        workspacePath: Path,
        permissions: SecurityPermissions,
        commandApprover: CommandApprover?,
    ): CompositeToolExecutor {
        val workspaceConfig = WorkspaceConfig(
            rootDirectory = workspacePath,
            allowRead = permissions.allowReadFile,
            allowWrite = permissions.allowWriteFile,
            allowDelete = permissions.allowDeleteFile,
        )

        val terminalConfig = TerminalConfig(
            rootDirectory = workspacePath,
            allowedCommands = if (permissions.allowExecuteCommand) TerminalConfig.DEFAULT_ALLOWED_COMMANDS else emptySet(),
            allowArbitraryCommands = permissions.allowArbitraryCommands,
            requireApprovalForDangerous = permissions.requireApprovalForDangerous,
            commandApprover = commandApprover,
        )

        val fsExecutor = FileSystemToolExecutor(workspaceConfig)
        val termExecutor = TerminalToolExecutor(terminalConfig)
        return CompositeToolExecutor(fsExecutor, termExecutor)
    }

    fun createPolicyEnforcingToolExecutor(
        workspacePath: Path,
        permissions: SecurityPermissions,
        preset: SecurityPreset,
        commandApprover: CommandApprover?,
    ): ToolExecutor {
        val workspaceConfig = WorkspaceConfig(
            rootDirectory = workspacePath,
            allowRead = permissions.allowReadFile,
            allowWrite = permissions.allowWriteFile,
            allowDelete = permissions.allowDeleteFile,
        )

        val terminalConfig = TerminalConfig(
            rootDirectory = workspacePath,
            allowedCommands = if (permissions.allowExecuteCommand) TerminalConfig.DEFAULT_ALLOWED_COMMANDS else emptySet(),
            allowArbitraryCommands = permissions.allowArbitraryCommands,
            requireApprovalForDangerous = false, // Centralized in PolicyEnforcingToolExecutor
            commandApprover = null, // Centralized in PolicyEnforcingToolExecutor
        )

        val fsExecutor = FileSystemToolExecutor(workspaceConfig)
        val termExecutor = TerminalToolExecutor(terminalConfig)
        val composite = CompositeToolExecutor(fsExecutor, termExecutor)

        return PolicyEnforcingToolExecutor(
            delegate = composite,
            workspacePath = { workspacePath },
            permissions = { permissions },
            preset = { preset },
            commandApprover = commandApprover,
        )
    }
}
