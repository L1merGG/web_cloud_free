package com.limer.antigooglity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.limer.antigooglity.coordinator.AgentCoordinator
import com.limer.antigooglity.coordinator.ExecutionStatus
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    coordinator: AgentCoordinator,
) {
    val workspacePath by coordinator.workspacePath.collectAsState()
    val permissions by coordinator.permissions.collectAsState()
    val preset by coordinator.preset.collectAsState()
    val gitInfo by coordinator.gitRepositoryInfo.collectAsState()
    val recentWorkspaces by coordinator.recentWorkspaces.collectAsState()
    val conversations by coordinator.conversations.collectAsState()
    val activeConversationId by coordinator.activeConversationId.collectAsState()
    val executionStatus by coordinator.executionStatus.collectAsState()
    val statusText by coordinator.statusText.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var isSidebarCollapsed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val activeConversation = conversations.firstOrNull { it.id == activeConversationId }
    val isRunning = executionStatus == ExecutionStatus.RUNNING

    AntigooglityTheme {
        Box(modifier = Modifier.fillMaxSize().background(Background)) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Navigation Sidebar (Antigravity Style)
                AntigravitySidebar(
                    workspacePath = workspacePath,
                    conversations = conversations,
                    activeConversationId = activeConversationId,
                    isRunning = isRunning,
                    isCollapsed = isSidebarCollapsed,
                    onToggleCollapse = { isSidebarCollapsed = !isSidebarCollapsed },
                    onNewConversation = { coordinator.newConversation() },
                    onSelectConversation = { coordinator.selectConversation(it) },
                    onDeleteConversation = { coordinator.deleteConversation(it) },
                    onChangeWorkspace = {
                        val picked = DirectoryPicker.pickDirectory(initialPath = workspacePath)
                        if (picked != null) {
                            coordinator.changeWorkspace(picked)
                        }
                    },
                    onOpenSettings = { showSettingsDialog = true },
                )

                // Central Workspace Area (Welcome Screen or Active Chat with Floating Composer)
                ChatArea(
                    conversation = activeConversation,
                    workspacePath = workspacePath,
                    recentWorkspaces = recentWorkspaces,
                    gitRepositoryInfo = gitInfo,
                    currentPreset = preset,
                    isRunning = isRunning,
                    statusText = statusText,
                    onSendMessage = { prompt -> coordinator.startTask(prompt) },
                    onCancelTask = { coordinator.cancelTask() },
                    onApproveCommand = { id -> coordinator.approveCommand(id) },
                    onRejectCommand = { id -> coordinator.rejectCommand(id) },
                    onChangeWorkspace = {
                        val picked = DirectoryPicker.pickDirectory(initialPath = workspacePath)
                        if (picked != null) {
                            coordinator.changeWorkspace(picked)
                        }
                    },
                    onSelectWorkspace = { path ->
                        coordinator.changeWorkspace(path)
                    },
                    onSwitchBranch = { branchName ->
                        scope.launch {
                            coordinator.switchGitBranch(branchName)
                        }
                    },
                    onUpdatePreset = { newPreset ->
                        coordinator.updatePreset(newPreset)
                    },
                    onOpenSettings = { showSettingsDialog = true },
                    modifier = Modifier.weight(1f),
                )
            }

            // Antigravity Settings Dialog Modal
            if (showSettingsDialog) {
                SettingsDialog(
                    currentPermissions = permissions,
                    currentPreset = preset,
                    onSave = { updated ->
                        coordinator.updatePermissions(updated)
                        showSettingsDialog = false
                    },
                    onUpdatePreset = { newPreset ->
                        coordinator.updatePreset(newPreset)
                    },
                    onDismiss = { showSettingsDialog = false },
                )
            }
        }
    }
}
