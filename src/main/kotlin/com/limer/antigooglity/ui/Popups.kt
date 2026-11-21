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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limer.antigooglity.coordinator.SecurityPreset
import com.limer.antigooglity.git.GitRepositoryInfo
import java.nio.file.Files
import java.nio.file.Path

@Composable
fun AddContextPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onInsertMention: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(SurfaceElevated)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
            .width(175.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(
                text = "Add Context",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )

            PopupMenuItem(
                icon = { AntigravityIcons.Paperclip(modifier = Modifier.size(14.dp)) },
                title = "Files",
                onClick = { onDismissRequest(); onPickFiles() },
            )
            PopupMenuItem(
                icon = { AntigravityIcons.Folder(modifier = Modifier.size(14.dp)) },
                title = "Folder",
                onClick = { onDismissRequest(); onPickFolder() },
            )
            PopupMenuItem(
                icon = {
                    Text("@", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                },
                title = "Mentions",
                onClick = { onDismissRequest(); onInsertMention() },
            )
            PopupMenuItem(
                icon = { AntigravityIcons.ActionDiagonal(modifier = Modifier.size(14.dp), color = TextMuted) },
                title = "Actions",
                badge = "Soon",
                enabled = false,
                onClick = {},
            )
            PopupMenuItem(
                icon = { AntigravityIcons.Globe(modifier = Modifier.size(14.dp), color = TextMuted) },
                title = "Browser",
                badge = "Soon",
                enabled = false,
                onClick = {},
            )
            PopupMenuItem(
                icon = { AntigravityIcons.Drive(modifier = Modifier.size(14.dp), color = TextMuted) },
                title = "Drive",
                badge = "Set up",
                badgeColor = AccentBlue,
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
fun ProjectSelectorPopup(
    expanded: Boolean,
    currentWorkspace: Path,
    recentWorkspaces: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onChangeWorkspace: () -> Unit,
    onSelectWorkspace: (Path) -> Unit = {},
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(SurfaceElevated)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
            .width(260.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(
                text = "Recent Projects",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )

            val currentName = currentWorkspace.fileName?.toString() ?: currentWorkspace.toString()
            PopupMenuItem(
                icon = { AntigravityIcons.Folder(modifier = Modifier.size(14.dp), color = AccentBlue) },
                title = currentName,
                endContent = {
                    AntigravityIcons.Checkmark(modifier = Modifier.size(13.dp), color = AccentBlue)
                },
                onClick = onDismissRequest,
            )

            // Other real recent workspaces
            val currentNormalized = currentWorkspace.toAbsolutePath().normalize().toString()
            val otherRecents = recentWorkspaces
                .filter { it != currentNormalized }
                .mapNotNull {
                    try {
                        val p = Path.of(it)
                        if (Files.exists(p) && Files.isDirectory(p)) p else null
                    } catch (_: Exception) {
                        null
                    }
                }
                .take(3)

            otherRecents.forEach { recentPath ->
                val recentName = recentPath.fileName?.toString() ?: recentPath.toString()
                PopupMenuItem(
                    icon = { AntigravityIcons.Folder(modifier = Modifier.size(14.dp), color = TextSecondary) },
                    title = recentName,
                    onClick = {
                        onDismissRequest()
                        onSelectWorkspace(recentPath)
                    },
                )
            }

            HorizontalDivider(
                color = BorderColor,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 3.dp),
            )

            PopupMenuItem(
                icon = { AntigravityIcons.Computer(modifier = Modifier.size(14.dp)) },
                title = "This Computer",
                endContent = {
                    AntigravityIcons.ChevronRight(modifier = Modifier.size(10.dp))
                },
                onClick = { onDismissRequest(); onChangeWorkspace() },
            )

            PopupMenuItem(
                icon = { AntigravityIcons.Terminal(modifier = Modifier.size(14.dp), color = TextMuted) },
                title = "SSH Host",
                endContent = {
                    AntigravityIcons.ChevronRight(modifier = Modifier.size(10.dp), color = TextMuted)
                },
                badge = "Soon",
                enabled = false,
                onClick = {},
            )

            HorizontalDivider(
                color = BorderColor,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 3.dp),
            )

            PopupMenuItem(
                icon = { AntigravityIcons.FolderPlus(modifier = Modifier.size(14.dp)) },
                title = "New Project",
                onClick = { onDismissRequest(); onChangeWorkspace() },
            )

            PopupMenuItem(
                icon = { AntigravityIcons.FolderSlash(modifier = Modifier.size(14.dp), color = TextMuted) },
                title = "No Project",
                onClick = onDismissRequest,
            )
        }
    }
}

@Composable
fun GitBranchPopup(
    expanded: Boolean,
    repositoryInfo: GitRepositoryInfo,
    onSwitchBranch: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(SurfaceElevated)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
            .width(260.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Git Branches",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                )
                val status = repositoryInfo.status
                if (status != null) {
                    Text(
                        text = status.summary,
                        fontSize = 10.sp,
                        color = if (status.isClean) SuccessGreen else WarningAmber,
                    )
                }
            }

            if (!repositoryInfo.isGitRepository) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Text(
                        text = repositoryInfo.errorMessage ?: "Not a Git repository",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            } else if (repositoryInfo.branches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Text(
                        text = "No local branches found",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            } else {
                repositoryInfo.branches.forEach { branch ->
                    PopupMenuItem(
                        icon = {
                            AntigravityIcons.GitBranch(
                                modifier = Modifier.size(13.dp),
                                color = if (branch.isCurrent) AccentBlue else TextSecondary,
                            )
                        },
                        title = branch.name,
                        endContent = if (branch.isCurrent) {
                            { AntigravityIcons.Checkmark(modifier = Modifier.size(12.dp), color = AccentBlue) }
                        } else null,
                        onClick = {
                            onDismissRequest()
                            if (!branch.isCurrent) {
                                onSwitchBranch(branch.name)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ModelSelectorPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(SurfaceElevated)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
            .width(260.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(
                text = "Model",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )

            // Current Active Gemini Web Session (Reference DOCX Image 6)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceBg)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Gemini Web (Active)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Authorized WebView2 session",
                        fontSize = 10.sp,
                        color = TextMuted,
                    )
                }
                AntigravityIcons.Checkmark(modifier = Modifier.size(13.dp), color = AccentBlue)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Notice about Stage 7.4.3
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Background, RoundedCornerShape(6.dp))
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                Text(
                    text = "Dynamic DOM Model Discovery and Thinking levels will be connected in Stage 7.4.3.",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp,
                )
            }

            HorizontalDivider(
                color = BorderColor,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // View Usage (Reference DOCX Image 6)
            PopupMenuItem(
                icon = { AntigravityIcons.ActionDiagonal(modifier = Modifier.size(13.dp), color = TextMuted) },
                title = "View Usage",
                endContent = {
                    AntigravityIcons.ChevronRight(modifier = Modifier.size(10.dp), color = TextMuted)
                },
                badge = "Soon",
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
fun ThinkingLevelPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(SurfaceElevated)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
            .width(140.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(
                text = "Thinking Level",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            PopupMenuItem(
                title = "Low",
                enabled = false,
                onClick = {},
            )
            PopupMenuItem(
                title = "Medium",
                enabled = false,
                onClick = {},
            )
            PopupMenuItem(
                title = "High",
                endContent = {
                    AntigravityIcons.Checkmark(modifier = Modifier.size(12.dp), color = AccentBlue)
                },
                enabled = true,
                onClick = onDismissRequest,
            )
        }
    }
}

@Composable
fun PermissionPresetPopup(
    expanded: Boolean,
    currentPreset: SecurityPreset = SecurityPreset.DEFAULT,
    onSelectPreset: (SecurityPreset) -> Unit,
    onDismissRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(SurfaceElevated)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
            .width(300.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(
                text = "Local Permissions",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )

            PresetCompactRow(
                icon = { AntigravityIcons.Eye(modifier = Modifier.size(14.dp)) },
                title = "Request Review",
                description = "Every tool call requires user approval.",
                isSelected = currentPreset == SecurityPreset.REQUEST_REVIEW,
                onClick = {
                    onSelectPreset(SecurityPreset.REQUEST_REVIEW)
                    onDismissRequest()
                },
            )

            PresetCompactRow(
                icon = { AntigravityIcons.Lock(modifier = Modifier.size(14.dp)) },
                title = "Default",
                tag = "Default (i)",
                description = "Ask before sensitive operations.",
                isSelected = currentPreset == SecurityPreset.DEFAULT,
                onClick = {
                    onSelectPreset(SecurityPreset.DEFAULT)
                    onDismissRequest()
                },
            )

            PresetCompactRow(
                icon = { AntigravityIcons.Shield(modifier = Modifier.size(14.dp)) },
                title = "Auto",
                description = "Automatically approve eligible low-risk actions.",
                isSelected = currentPreset == SecurityPreset.AUTO,
                onClick = {
                    onSelectPreset(SecurityPreset.AUTO)
                    onDismissRequest()
                },
            )

            PresetCompactRow(
                icon = { AntigravityIcons.ShieldAlert(modifier = Modifier.size(14.dp)) },
                title = "Turbo",
                description = "Automatically approve eligible actions; security boundaries remain enforced.",
                isSelected = currentPreset == SecurityPreset.TURBO,
                onClick = {
                    onSelectPreset(SecurityPreset.TURBO)
                    onDismissRequest()
                },
            )

            HorizontalDivider(
                color = BorderColor,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            PopupMenuItem(
                icon = { AntigravityIcons.Settings(modifier = Modifier.size(13.dp), color = TextMuted) },
                title = "Configure Tool Permissions...",
                onClick = {
                    onDismissRequest()
                    onOpenSettings()
                },
            )
        }
    }
}

@Composable
private fun PopupMenuItem(
    title: String,
    icon: (@Composable () -> Unit)? = null,
    endContent: (@Composable () -> Unit)? = null,
    badge: String? = null,
    badgeColor: Color = TextMuted,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = if (isHovered && enabled) SurfaceBg else Color.Transparent,
        animationSpec = tween(80),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                fontSize = 12.sp,
                color = if (enabled) (if (isHovered) TextPrimary else TextSecondary) else TextMuted,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (endContent != null) {
            endContent()
        } else if (badge != null) {
            Text(
                text = badge,
                fontSize = 10.sp,
                color = badgeColor,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun PresetCompactRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    isSelected: Boolean,
    tag: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = if (isSelected) SurfaceBg else if (isHovered) SurfaceBg.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(80),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 6.dp),
        ) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = TextPrimary,
                    )
                    if (tag != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(SurfaceElevated, RoundedCornerShape(4.dp))
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 9.sp,
                                color = TextMuted,
                            )
                        }
                    }
                }
                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isSelected) {
            AntigravityIcons.Checkmark(modifier = Modifier.size(13.dp), color = AccentBlue)
        }
    }
}
