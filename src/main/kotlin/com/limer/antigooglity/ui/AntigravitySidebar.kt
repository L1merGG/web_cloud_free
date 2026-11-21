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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limer.antigooglity.coordinator.Conversation
import java.nio.file.Path

fun formatRelativeTime(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val diffSec = (diffMs / 1000).coerceAtLeast(0)
    val diffMin = diffSec / 60
    val diffHours = diffMin / 60
    val diffDays = diffHours / 24

    return when {
        diffMin < 1 -> "now"
        diffMin < 60 -> "${diffMin}m"
        diffHours < 24 -> "${diffHours}h"
        else -> "${diffDays}d"
    }
}

@Composable
fun AntigravitySidebar(
    workspacePath: Path,
    conversations: List<Conversation>,
    activeConversationId: String,
    isRunning: Boolean,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onChangeWorkspace: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sidebarWidth = if (isCollapsed) 64.dp else 230.dp
    var isProjectsExpanded by remember { mutableStateOf(true) }

    Surface(
        color = SidebarBg,
        modifier = modifier
            .fillMaxHeight()
            .width(sidebarWidth)
            .border(BorderStroke(1.dp, BorderColor)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = if (isCollapsed) 8.dp else 12.dp, vertical = 12.dp),
        ) {
            // Header: Minimalist Arch Logo & Brand Name (Reference DOCX Image 1)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp, start = 2.dp, end = 2.dp),
            ) {
                if (!isCollapsed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Pure Antigravity arch logo without box frame
                        AntigravityIcons.Logo(
                            modifier = Modifier.size(20.dp),
                            color = TextPrimary,
                        )
                        Text(
                            text = "Antigooglity",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            letterSpacing = (-0.2).sp,
                        )
                    }
                } else {
                    AntigravityIcons.Logo(
                        modifier = Modifier.size(20.dp),
                        color = TextPrimary,
                    )
                }

                // Collapse Button
                if (!isCollapsed) {
                    val collapseInteraction = remember { MutableInteractionSource() }
                    val isCollapseHovered by collapseInteraction.collectIsHoveredAsState()
                    val collapseBg by animateColorAsState(
                        targetValue = if (isCollapseHovered) SurfaceElevated else Color.Transparent,
                        animationSpec = tween(120),
                    )

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(collapseBg)
                            .clickable(
                                interactionSource = collapseInteraction,
                                indication = null,
                                onClick = onToggleCollapse,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⇤",
                            color = if (isCollapseHovered) TextPrimary else TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // New Conversation Button
            val newChatInteraction = remember { MutableInteractionSource() }
            val isNewChatHovered by newChatInteraction.collectIsHoveredAsState()
            val newChatBg by animateColorAsState(
                targetValue = if (isNewChatHovered) SurfaceElevated else SurfaceBg,
                animationSpec = tween(120),
            )

            Surface(
                shape = RoundedCornerShape(7.dp),
                color = newChatBg,
                border = BorderStroke(1.dp, if (isNewChatHovered) BorderLight else BorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clickable(
                        interactionSource = newChatInteraction,
                        indication = null,
                        onClick = onNewConversation,
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.Start,
                ) {
                    Text(
                        text = "+",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimary,
                        modifier = Modifier.padding(end = if (isCollapsed) 0.dp else 8.dp),
                    )
                    if (!isCollapsed) {
                        Text(
                            text = "New Conversation",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation items: Automations & Customizations (with outline vector icons)
            if (!isCollapsed) {
                SidebarNavItem(
                    icon = { AntigravityIcons.Clock(color = TextMuted) },
                    title = "Automations",
                    badge = "Soon",
                )
                Spacer(modifier = Modifier.height(2.dp))
                SidebarNavItem(
                    icon = { AntigravityIcons.Cube(color = TextMuted) },
                    title = "Customizations",
                    badge = "Soon",
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderColor, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Projects Section Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clickable { isProjectsExpanded = !isProjectsExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Projects",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    AntigravityIcons.ChevronDown(
                        color = TextMuted,
                        modifier = Modifier.size(10.dp)
                    )
                }

                if (isProjectsExpanded) {
                    // Active Workspace Folder row
                    val projectName = workspacePath.fileName?.toString() ?: workspacePath.toString()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isRunning, onClick = onChangeWorkspace)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AntigravityIcons.Folder(
                            modifier = Modifier.size(15.dp).padding(end = 6.dp),
                            color = TextSecondary,
                        )
                        Text(
                            text = projectName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Conversations List (nested under project)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == activeConversationId
                    ConversationTreeRow(
                        conversation = conv,
                        isSelected = isSelected,
                        isCollapsed = isCollapsed,
                        canDelete = conversations.size > 1,
                        onSelect = { onSelectConversation(conv.id) },
                        onDelete = { onDeleteConversation(conv.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Settings Row (Bottom)
            val settingsInteraction = remember { MutableInteractionSource() }
            val isSettingsHovered by settingsInteraction.collectIsHoveredAsState()
            val settingsBg by animateColorAsState(
                targetValue = if (isSettingsHovered) SurfaceElevated else Color.Transparent,
                animationSpec = tween(120),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(settingsBg)
                    .clickable(
                        interactionSource = settingsInteraction,
                        indication = null,
                        onClick = onOpenSettings,
                    )
                    .padding(horizontal = if (isCollapsed) 0.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.Start,
            ) {
                AntigravityIcons.Settings(
                    modifier = Modifier.size(16.dp).padding(end = if (isCollapsed) 0.dp else 8.dp),
                    color = if (isSettingsHovered) TextPrimary else TextSecondary,
                )
                if (!isCollapsed) {
                    Text(
                        text = "Settings",
                        fontSize = 12.sp,
                        color = if (isSettingsHovered) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarNavItem(
    icon: @Composable () -> Unit,
    title: String,
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .background(SurfaceElevated, RoundedCornerShape(4.dp))
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = badge,
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ConversationTreeRow(
    conversation: Conversation,
    isSelected: Boolean,
    isCollapsed: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val itemBg by animateColorAsState(
        targetValue = when {
            isSelected -> SurfaceElevated
            isHovered -> SurfaceBg
            else -> Color.Transparent
        },
        animationSpec = tween(120),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(itemBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = if (isCollapsed) 4.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        if (!isCollapsed) {
            Text(
                text = conversation.title,
                color = if (isSelected) TextPrimary else if (isHovered) TextPrimary else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 4.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Relative time badge
                Text(
                    text = formatRelativeTime(conversation.createdAt),
                    fontSize = 10.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(end = 2.dp),
                )

                if (canDelete && (isHovered || isSelected)) {
                    val deleteInteraction = remember { MutableInteractionSource() }
                    val isDeleteHovered by deleteInteraction.collectIsHoveredAsState()

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = deleteInteraction,
                                indication = null,
                                onClick = onDelete,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "×",
                            color = if (isDeleteHovered) DangerRed else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            // Collapsed indicator dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AccentBlue else TextMuted)
            )
        }
    }
}
