package com.limer.antigooglity.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limer.antigooglity.coordinator.Conversation
import com.limer.antigooglity.coordinator.SecurityPreset
import com.limer.antigooglity.coordinator.UiMessage
import com.limer.antigooglity.coordinator.UiMessageRole
import com.limer.antigooglity.git.GitRepositoryInfo
import java.nio.file.Path

@Composable
fun ChatArea(
    conversation: Conversation?,
    workspacePath: Path,
    recentWorkspaces: List<String> = emptyList(),
    gitRepositoryInfo: GitRepositoryInfo = GitRepositoryInfo(isGitRepository = false),
    currentPreset: SecurityPreset = SecurityPreset.DEFAULT,
    isRunning: Boolean,
    statusText: String,
    onSendMessage: (String) -> Unit,
    onCancelTask: () -> Unit,
    onApproveCommand: (String) -> Unit,
    onRejectCommand: (String) -> Unit,
    onChangeWorkspace: () -> Unit,
    onSelectWorkspace: (Path) -> Unit = {},
    onSwitchBranch: (String) -> Unit = {},
    onUpdatePreset: (SecurityPreset) -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages, events, or approvals
    LaunchedEffect(
        conversation?.messages?.size,
        conversation?.messages?.lastOrNull()?.toolExecutions?.size,
        conversation?.messages?.lastOrNull()?.approvalRequests?.size,
    ) {
        val total = conversation?.messages?.size ?: 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        if (conversation == null || conversation.messages.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                WelcomeView(
                    workspacePath = workspacePath,
                    onSelectSuggestion = onSendMessage,
                    modifier = Modifier.weight(1f),
                )
                AntigravityComposer(
                    workspacePath = workspacePath,
                    recentWorkspaces = recentWorkspaces,
                    gitRepositoryInfo = gitRepositoryInfo,
                    currentPreset = currentPreset,
                    isRunning = isRunning,
                    statusText = statusText,
                    onSendMessage = onSendMessage,
                    onCancelTask = onCancelTask,
                    onChangeWorkspace = onChangeWorkspace,
                    onSelectWorkspace = onSelectWorkspace,
                    onSwitchBranch = onSwitchBranch,
                    onUpdatePreset = onUpdatePreset,
                    onOpenSettings = onOpenSettings,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                                .padding(horizontal = 20.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(conversation.messages, key = { it.id }) { message ->
                                AntigravityMessageItem(
                                    msg = message,
                                    onApproveCommand = onApproveCommand,
                                    onRejectCommand = onRejectCommand,
                                )
                            }
                        }
                    }
                }

                // Floating Composer at bottom of chat (max 700dp, centered)
                AntigravityComposer(
                    workspacePath = workspacePath,
                    recentWorkspaces = recentWorkspaces,
                    gitRepositoryInfo = gitRepositoryInfo,
                    currentPreset = currentPreset,
                    isRunning = isRunning,
                    statusText = statusText,
                    onSendMessage = onSendMessage,
                    onCancelTask = onCancelTask,
                    onChangeWorkspace = onChangeWorkspace,
                    onSelectWorkspace = onSelectWorkspace,
                    onSwitchBranch = onSwitchBranch,
                    onUpdatePreset = onUpdatePreset,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun AntigravityMessageItem(
    msg: UiMessage,
    onApproveCommand: (String) -> Unit,
    onRejectCommand: (String) -> Unit,
) {
    when (msg.role) {
        UiMessageRole.USER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.widthIn(max = 600.dp),
                ) {
                    Text(
                        text = msg.text,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        UiMessageRole.ASSISTANT -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Inline approvals if any
                msg.approvalRequests.forEach { approvalReq ->
                    InlineApprovalCard(
                        request = approvalReq,
                        onApprove = onApproveCommand,
                        onReject = onRejectCommand,
                    )
                }

                // Tool executions collapsed/expanded cards
                msg.toolExecutions.forEach { toolExec ->
                    ToolCard(tool = toolExec)
                }

                // Assistant text response
                if (msg.text.isNotEmpty()) {
                    Text(
                        text = msg.text,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                } else if (msg.toolExecutions.isEmpty() && msg.approvalRequests.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        CircularProgressIndicator(
                            color = AccentBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ассистент думает...",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
