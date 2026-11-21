package com.limer.antigooglity.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limer.antigooglity.coordinator.SecurityPreset
import com.limer.antigooglity.git.GitRepositoryInfo
import java.nio.file.Path
import javax.swing.JFileChooser

@Composable
fun AntigravityComposer(
    workspacePath: Path,
    recentWorkspaces: List<String> = emptyList(),
    gitRepositoryInfo: GitRepositoryInfo = GitRepositoryInfo(isGitRepository = false),
    currentPreset: SecurityPreset = SecurityPreset.DEFAULT,
    isRunning: Boolean,
    statusText: String,
    onSendMessage: (String) -> Unit,
    onCancelTask: () -> Unit,
    onChangeWorkspace: () -> Unit,
    onSelectWorkspace: (Path) -> Unit = {},
    onSwitchBranch: (String) -> Unit = {},
    onUpdatePreset: (SecurityPreset) -> Unit = {},
    onOpenSettings: () -> Unit,
    initialText: String = "",
    onClearInitialText: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var contextPopupOpen by remember { mutableStateOf(false) }
    var projectPopupOpen by remember { mutableStateOf(false) }
    var branchPopupOpen by remember { mutableStateOf(false) }
    var modelPopupOpen by remember { mutableStateOf(false) }
    var permissionPopupOpen by remember { mutableStateOf(false) }

    if (initialText.isNotBlank()) {
        text = initialText
        onClearInitialText()
    }

    val canSend = text.isNotBlank() && !isRunning
    val scrollState = rememberScrollState()
    val projectName = remember(workspacePath) {
        workspacePath.fileName?.toString() ?: workspacePath.toString()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Floating Antigravity Composer Card (Reference DOCX Image 1 & 5: max 700dp, centered)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceBg,
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // Top Row: Project & Git Branch Selector (Reference DOCX Image 1 & 5)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Project Selector Chip
                    Box {
                        TopComposerChip(
                            icon = { AntigravityIcons.Folder(color = TextSecondary, modifier = Modifier.size(13.dp)) },
                            label = projectName,
                            onClick = { projectPopupOpen = true },
                        )
                        ProjectSelectorPopup(
                            expanded = projectPopupOpen,
                            currentWorkspace = workspacePath,
                            recentWorkspaces = recentWorkspaces,
                            onDismissRequest = { projectPopupOpen = false },
                            onChangeWorkspace = onChangeWorkspace,
                            onSelectWorkspace = onSelectWorkspace,
                        )
                    }

                    // Git Branch Selector Chip
                    Box {
                        val branchLabel = if (gitRepositoryInfo.isGitRepository) {
                            gitRepositoryInfo.currentBranch ?: "No Branch"
                        } else {
                            "Not a Git repository"
                        }
                        val branchIconColor = if (gitRepositoryInfo.isGitRepository) AccentBlue else TextMuted

                        TopComposerChip(
                            icon = { AntigravityIcons.GitBranch(color = branchIconColor, modifier = Modifier.size(13.dp)) },
                            label = branchLabel,
                            onClick = { branchPopupOpen = true },
                        )
                        GitBranchPopup(
                            expanded = branchPopupOpen,
                            repositoryInfo = gitRepositoryInfo,
                            onSwitchBranch = onSwitchBranch,
                            onDismissRequest = { branchPopupOpen = false },
                        )
                    }
                }

                // Middle: Multiline Input Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp, max = 150.dp)
                        .verticalScroll(scrollState),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = if (isRunning) "Agent is executing task..." else "Ask anything, @ to mention, / for actions",
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    BasicTextField(
                        value = text,
                        onValueChange = { if (!isRunning) text = it },
                        enabled = !isRunning,
                        textStyle = TextStyle(
                            color = if (isRunning) TextMuted else TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        ),
                        cursorBrush = SolidColor(AccentBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    if (event.key == Key.Enter && event.isCtrlPressed) {
                                        if (canSend) {
                                            onSendMessage(text)
                                            text = ""
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Bar: Context (+), Model Selector, Permission, Mic, Send
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 1. Context Plus Button
                        Box {
                            val contextInteraction = remember { MutableInteractionSource() }
                            val isContextHovered by contextInteraction.collectIsHoveredAsState()
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isContextHovered) SurfaceElevated else Color.Transparent)
                                    .clickable(
                                        interactionSource = contextInteraction,
                                        indication = null,
                                        onClick = { contextPopupOpen = true },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+",
                                    fontSize = 16.sp,
                                    color = if (isContextHovered) TextPrimary else TextSecondary,
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                            AddContextPopup(
                                expanded = contextPopupOpen,
                                onDismissRequest = { contextPopupOpen = false },
                                onPickFiles = {
                                    val chooser = JFileChooser().apply {
                                        isMultiSelectionEnabled = true
                                        fileSelectionMode = JFileChooser.FILES_ONLY
                                        dialogTitle = "Select files for context"
                                    }
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        val paths = chooser.selectedFiles.joinToString(" ") { "@${it.name}" }
                                        text = if (text.isEmpty()) paths else "$text $paths"
                                    }
                                },
                                onPickFolder = {
                                    val picked = DirectoryPicker.pickDirectory(initialPath = workspacePath)
                                    if (picked != null) {
                                        val folderTag = "@${picked.fileName}"
                                        text = if (text.isEmpty()) folderTag else "$text $folderTag"
                                    }
                                },
                                onInsertMention = {
                                    text = if (text.endsWith(" ") || text.isEmpty()) "${text}@" else "$text @"
                                },
                            )
                        }

                        // 2. Model Selector Chip (Reference DOCX Image 1 & 6)
                        Box {
                            val modelInteraction = remember { MutableInteractionSource() }
                            val isModelHovered by modelInteraction.collectIsHoveredAsState()
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isModelHovered) SurfaceElevated else Color.Transparent)
                                    .clickable(
                                        interactionSource = modelInteraction,
                                        indication = null,
                                        onClick = { modelPopupOpen = true },
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Gemini Web",
                                    fontSize = 12.sp,
                                    color = if (isModelHovered) TextPrimary else TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                )
                                AntigravityIcons.ChevronDown(
                                    modifier = Modifier.size(10.dp),
                                    color = TextMuted,
                                )
                            }
                            ModelSelectorPopup(
                                expanded = modelPopupOpen,
                                onDismissRequest = { modelPopupOpen = false },
                            )
                        }
                    }

                    // Right actions: Permission, Mic, Send
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 3. Permission Preset Chip (Reference DOCX Image 1 & 3)
                        Box {
                            val permInteraction = remember { MutableInteractionSource() }
                            val isPermHovered by permInteraction.collectIsHoveredAsState()
                            val permColor = when (currentPreset) {
                                SecurityPreset.TURBO -> WarningAmber
                                SecurityPreset.REQUEST_REVIEW -> AccentBlue
                                SecurityPreset.AUTO -> SuccessGreen
                                SecurityPreset.DEFAULT -> if (isPermHovered) TextPrimary else TextSecondary
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isPermHovered) SurfaceElevated else Color.Transparent)
                                    .clickable(
                                        interactionSource = permInteraction,
                                        indication = null,
                                        onClick = { permissionPopupOpen = true },
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                when (currentPreset) {
                                    SecurityPreset.REQUEST_REVIEW -> AntigravityIcons.Eye(
                                        modifier = Modifier.size(12.dp),
                                        color = permColor,
                                    )
                                    SecurityPreset.DEFAULT -> AntigravityIcons.Lock(
                                        modifier = Modifier.size(12.dp),
                                        color = permColor,
                                    )
                                    SecurityPreset.AUTO -> AntigravityIcons.Shield(
                                        modifier = Modifier.size(12.dp),
                                        color = permColor,
                                    )
                                    SecurityPreset.TURBO -> AntigravityIcons.ShieldAlert(
                                        modifier = Modifier.size(12.dp),
                                        color = permColor,
                                    )
                                }
                                Text(
                                    text = currentPreset.displayName,
                                    fontSize = 12.sp,
                                    color = if (isPermHovered) TextPrimary else TextSecondary,
                                    fontWeight = if (currentPreset == SecurityPreset.TURBO) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                AntigravityIcons.ChevronDown(
                                    modifier = Modifier.size(10.dp),
                                    color = TextMuted,
                                )
                            }
                            PermissionPresetPopup(
                                expanded = permissionPopupOpen,
                                currentPreset = currentPreset,
                                onSelectPreset = onUpdatePreset,
                                onDismissRequest = { permissionPopupOpen = false },
                                onOpenSettings = onOpenSettings,
                            )
                        }

                        // 4. Voice Mic Icon
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            AntigravityIcons.Microphone(
                                modifier = Modifier.size(13.dp),
                                color = TextMuted,
                            )
                        }

                        // 5. Send / Stop Button
                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed)
                                    .clickable { onCancelTask() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("⏹", color = Color.White, fontSize = 11.sp)
                            }
                        } else {
                            val sendBgColor by animateColorAsState(
                                targetValue = if (canSend) TextPrimary else SurfaceElevated,
                                animationSpec = tween(100),
                            )
                            val sendIconColor by animateColorAsState(
                                targetValue = if (canSend) Background else TextMuted,
                                animationSpec = tween(100),
                            )

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(sendBgColor)
                                    .clickable(enabled = canSend) {
                                        if (canSend) {
                                            onSendMessage(text)
                                            text = ""
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                AntigravityIcons.SendArrow(
                                    modifier = Modifier.size(13.dp),
                                    color = sendIconColor,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Subtle status caption below composer
        if (statusText.isNotBlank() && statusText != "Готов к работе") {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(if (isRunning) WarningAmber else SuccessGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun TopComposerChip(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isHovered) TextPrimary else TextSecondary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AntigravityIcons.ChevronDown(
            modifier = Modifier.size(9.dp),
            color = TextMuted,
        )
    }
}
