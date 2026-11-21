package com.limer.antigooglity.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.limer.antigooglity.coordinator.SecurityPermissions
import com.limer.antigooglity.coordinator.SecurityPreset

enum class SettingsSection(val title: String) {
    GENERAL("General"),
    APPLICATION("Application"),
    APPEARANCE("Appearance"),
    SHORTCUTS("Shortcuts"),
    NOTIFICATIONS("Notifications"),
    MODELS("Models"),
    BROWSER("Browser"),
    PROJECTS("Projects"),
}

@Composable
fun SettingsDialog(
    currentPermissions: SecurityPermissions,
    currentPreset: SecurityPreset = SecurityPreset.DEFAULT,
    onSave: (SecurityPermissions) -> Unit,
    onUpdatePreset: (SecurityPreset) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var activeSection by remember { mutableStateOf(SettingsSection.GENERAL) }

    var allowRead by remember { mutableStateOf(currentPermissions.allowReadFile) }
    var allowWrite by remember { mutableStateOf(currentPermissions.allowWriteFile) }
    var allowDelete by remember { mutableStateOf(currentPermissions.allowDeleteFile) }
    var allowExecute by remember { mutableStateOf(currentPermissions.allowExecuteCommand) }
    var allowArbitrary by remember { mutableStateOf(currentPermissions.allowArbitraryCommands) }
    var requireApproval by remember { mutableStateOf(currentPermissions.requireApprovalForDangerous) }

    var isToolPermissionsExpanded by remember { mutableStateOf(false) }
    var isQueuedMessagesEnabled by remember { mutableStateOf(true) }

    fun emitUpdatedPermissions(
        r: Boolean = allowRead,
        w: Boolean = allowWrite,
        d: Boolean = allowDelete,
        e: Boolean = allowExecute,
        a: Boolean = allowArbitrary,
        ap: Boolean = requireApproval,
    ) {
        onSave(
            SecurityPermissions(
                allowReadFile = r,
                allowWriteFile = w,
                allowDeleteFile = d,
                allowExecuteCommand = e,
                allowArbitraryCommands = a,
                requireApprovalForDangerous = ap,
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Large, proportional modal dialog (Reference DOCX Image 2: ~1000dp x ~808dp, adaptive padding)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier
                    .widthIn(max = 1000.dp)
                    .heightIn(max = 808.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Navigation Column (Width ~ 200 dp)
                    Surface(
                        color = SidebarBg,
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .border(BorderStroke(1.dp, BorderColor)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = "Settings",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                            )

                            SettingsSection.entries.forEach { section ->
                                val isSelected = section == activeSection
                                val interaction = remember { MutableInteractionSource() }
                                val isHovered by interaction.collectIsHoveredAsState()

                                val itemBg by animateColorAsState(
                                    targetValue = when {
                                        isSelected -> SurfaceElevated
                                        isHovered -> SurfaceBg
                                        else -> Color.Transparent
                                    },
                                    animationSpec = tween(80),
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(itemBg)
                                        .clickable(
                                            interactionSource = interaction,
                                            indication = null,
                                            onClick = { activeSection = section },
                                        )
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = section.title,
                                        fontSize = 13.sp,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Bottom items
                            Text(
                                text = "Give Feedback",
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier
                                    .padding(start = 10.dp, bottom = 10.dp)
                                    .clickable { /* no-op */ },
                            )
                        }
                    }

                    // Right Content Column (Independent Scroll)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Background)
                            .padding(28.dp),
                    ) {
                        // Header: Title, Subtitle, Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = activeSection.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (activeSection == SettingsSection.GENERAL) {
                                        "Configure agent execution, queued message delivery, and permissions."
                                    } else {
                                        "Configure settings for ${activeSection.title.lowercase()}."
                                    },
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                )
                            }

                            // Close Button (✕)
                            val closeInteraction = remember { MutableInteractionSource() }
                            val isCloseHovered by closeInteraction.collectIsHoveredAsState()
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isCloseHovered) SurfaceElevated else Color.Transparent)
                                    .clickable(
                                        interactionSource = closeInteraction,
                                        indication = null,
                                        onClick = onDismiss,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "✕",
                                    color = if (isCloseHovered) TextPrimary else TextMuted,
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Section Content Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            when (activeSection) {
                                SettingsSection.GENERAL -> {
                                    GeneralContent(
                                        currentPreset = currentPreset,
                                        onUpdatePreset = onUpdatePreset,
                                        allowRead = allowRead,
                                        onAllowReadChange = {
                                            allowRead = it
                                            emitUpdatedPermissions(r = it)
                                        },
                                        allowWrite = allowWrite,
                                        onAllowWriteChange = {
                                            allowWrite = it
                                            emitUpdatedPermissions(w = it)
                                        },
                                        allowDelete = allowDelete,
                                        onAllowDeleteChange = {
                                            allowDelete = it
                                            emitUpdatedPermissions(d = it)
                                        },
                                        allowExecute = allowExecute,
                                        onAllowExecuteChange = {
                                            allowExecute = it
                                            emitUpdatedPermissions(e = it)
                                        },
                                        allowArbitrary = allowArbitrary,
                                        onAllowArbitraryChange = {
                                            allowArbitrary = it
                                            emitUpdatedPermissions(a = it)
                                        },
                                        requireApproval = requireApproval,
                                        onRequireApprovalChange = {
                                            requireApproval = it
                                            emitUpdatedPermissions(ap = it)
                                        },
                                        isToolPermissionsExpanded = isToolPermissionsExpanded,
                                        onToggleToolPermissions = {
                                            isToolPermissionsExpanded = !isToolPermissionsExpanded
                                        },
                                        isQueuedMessagesEnabled = isQueuedMessagesEnabled,
                                        onToggleQueuedMessages = {
                                            isQueuedMessagesEnabled = it
                                        },
                                    )
                                }
                                else -> {
                                    FutureSectionContent(section = activeSection)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralContent(
    currentPreset: SecurityPreset,
    onUpdatePreset: (SecurityPreset) -> Unit,
    allowRead: Boolean,
    onAllowReadChange: (Boolean) -> Unit,
    allowWrite: Boolean,
    onAllowWriteChange: (Boolean) -> Unit,
    allowDelete: Boolean,
    onAllowDeleteChange: (Boolean) -> Unit,
    allowExecute: Boolean,
    onAllowExecuteChange: (Boolean) -> Unit,
    allowArbitrary: Boolean,
    onAllowArbitraryChange: (Boolean) -> Unit,
    requireApproval: Boolean,
    onRequireApprovalChange: (Boolean) -> Unit,
    isToolPermissionsExpanded: Boolean,
    onToggleToolPermissions: () -> Unit,
    isQueuedMessagesEnabled: Boolean,
    onToggleQueuedMessages: (Boolean) -> Unit,
) {
    var presetPopupOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Interface Layout (Reference DOCX Image 2)
        Text(
            text = "Interface Layout",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LayoutCard(
                icon = { AntigravityIcons.Briefcase(color = TextSecondary, modifier = Modifier.size(16.dp)) },
                title = "Knowledge Work",
                description = "Simplified interface for research, writing, and everyday tasks.",
                isSelected = false,
                modifier = Modifier.weight(1f),
            )
            LayoutCard(
                icon = { AntigravityIcons.CrossedTools(color = AccentBlue, modifier = Modifier.size(16.dp)) },
                title = "Software Engineering",
                description = "Full interface for coding, developer tools, and managing agents.",
                isSelected = true,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 2. Global Permissions (Reference DOCX Image 2)
        Text(
            text = "Global Permissions",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Configure global allowed and denied resource permissions. Learn more.",
            fontSize = 11.sp,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Grouped settings container
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceBg,
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Permission Preset
                SettingsGroupRow(
                    title = "Permission Preset",
                    description = "Controls the actions the agent can take.",
                    action = {
                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceElevated)
                                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(6.dp))
                                    .clickable { presetPopupOpen = true }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentPreset.displayName,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AntigravityIcons.ChevronDown(
                                        color = TextMuted,
                                        modifier = Modifier.size(9.dp),
                                    )
                                }
                            }

                            PermissionPresetPopup(
                                expanded = presetPopupOpen,
                                currentPreset = currentPreset,
                                onSelectPreset = {
                                    onUpdatePreset(it)
                                    presetPopupOpen = false
                                },
                                onDismissRequest = { presetPopupOpen = false },
                                onOpenSettings = {},
                            )
                        }
                    },
                )

                HorizontalDivider(color = BorderColor, thickness = 1.dp)

                // Row 2: Tool Permissions (toggles actual permissions checkboxes)
                SettingsGroupRow(
                    title = "Tool Permissions",
                    description = "Modify permissions for file, terminal, and MCP tools.",
                    action = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceElevated)
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(6.dp))
                                .clickable { onToggleToolPermissions() }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = if (isToolPermissionsExpanded) "Close" else "Open",
                                fontSize = 12.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    },
                )

                // Expandable actual SecurityPermissions
                AnimatedVisibility(visible = isToolPermissionsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Background)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        CompactPermissionItem(
                            title = "Allow reading files",
                            subtitle = "Allows the agent to read contents of workspace files",
                            checked = allowRead,
                            onCheckedChange = onAllowReadChange,
                        )
                        CompactPermissionItem(
                            title = "Allow writing files",
                            subtitle = "Allows the agent to create and modify files in the workspace",
                            checked = allowWrite,
                            onCheckedChange = onAllowWriteChange,
                        )
                        CompactPermissionItem(
                            title = "Allow deleting files",
                            subtitle = "Dangerous. Disabled by default. Allows deleting workspace files",
                            checked = allowDelete,
                            onCheckedChange = onAllowDeleteChange,
                        )
                        CompactPermissionItem(
                            title = "Allow command execution",
                            subtitle = "Allows executing allowlisted commands in the terminal",
                            checked = allowExecute,
                            onCheckedChange = onAllowExecuteChange,
                        )
                        CompactPermissionItem(
                            title = "Allow arbitrary commands",
                            subtitle = "Dangerous. Allows commands outside the predefined safe allowlist",
                            checked = allowArbitrary,
                            onCheckedChange = onAllowArbitraryChange,
                        )
                        CompactPermissionItem(
                            title = "Require approval for sensitive commands",
                            subtitle = "Prompts the user before executing dangerous tools/commands",
                            checked = requireApproval,
                            onCheckedChange = onRequireApprovalChange,
                        )
                    }
                }

                HorizontalDivider(color = BorderColor, thickness = 1.dp)

                // Row 3: Auto-approve Workspace Edits (Reference DOCX Image 2)
                SettingsGroupRow(
                    title = "Auto-approve Workspace Edits",
                    description = "Review workspace file edits before they are applied.",
                    action = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceElevated)
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(6.dp))
                                .clickable { /* no-op */ }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Prompt",
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                AntigravityIcons.ChevronDown(
                                    color = TextMuted,
                                    modifier = Modifier.size(9.dp),
                                )
                            }
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 3. Agent Execution (Reference DOCX Image 2)
        Text(
            text = "Agent Execution",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceBg,
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SettingsGroupRow(
                title = "Deliver Queued Messages",
                description = "Enable immediate delivery of user messages while agent runs.",
                action = {
                    CustomSwitch(
                        checked = isQueuedMessagesEnabled,
                        onCheckedChange = onToggleQueuedMessages,
                    )
                },
            )
        }
    }
}

@Composable
private fun CompactPermissionItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextMuted,
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = AccentBlue,
                uncheckedColor = TextMuted,
                checkmarkColor = Color.White,
            ),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SettingsGroupRow(
    title: String,
    description: String,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = TextMuted,
            )
        }
        action()
    }
}

@Composable
private fun LayoutCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, if (isSelected) AccentBlue else BorderColor),
        modifier = modifier
            .height(110.dp)
            .clickable { /* selectable in future */ },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(SurfaceElevated, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(AccentBlue, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AntigravityIcons.Checkmark(
                            color = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }

            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = TextMuted,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackBg by animateColorAsState(
        targetValue = if (checked) AccentBlue else SurfaceElevated,
        animationSpec = tween(100),
    )

    Box(
        modifier = Modifier
            .width(36.dp)
            .height(20.dp)
            .clip(CircleShape)
            .background(trackBg)
            .border(BorderStroke(1.dp, BorderColor), CircleShape)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun FutureSectionContent(section: SettingsSection) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${section.title} Settings",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Detailed configuration for ${section.title.lowercase()} will be implemented in subsequent phases.",
            fontSize = 12.sp,
            color = TextMuted,
        )
    }
}
