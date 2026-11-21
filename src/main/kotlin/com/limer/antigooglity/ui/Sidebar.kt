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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.limer.antigooglity.coordinator.Conversation
import java.nio.file.Path

@Composable
fun Sidebar(
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
    val sidebarWidth = if (isCollapsed) 64.dp else 260.dp

    Surface(
        color = PanelDark,
        modifier = modifier
            .fillMaxHeight()
            .width(sidebarWidth),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = if (isCollapsed) 8.dp else 12.dp, vertical = 12.dp),
        ) {
            // Top Bar: Logo & Collapse Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                if (!isCollapsed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(PrimaryBlue, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✦", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Antigooglity",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                }

                val collapseInteraction = remember { MutableInteractionSource() }
                val isCollapseHovered by collapseInteraction.collectIsHoveredAsState()
                val collapseBg by animateColorAsState(
                    targetValue = if (isCollapseHovered) CardDark else Color.Transparent,
                    animationSpec = tween(120),
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(collapseBg)
                        .clickable(
                            interactionSource = collapseInteraction,
                            indication = null,
                            onClick = onToggleCollapse,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isCollapsed) "☰" else "⇤",
                        color = if (isCollapseHovered) TextPrimary else TextSecondary,
                        fontSize = 16.sp,
                    )
                }
            }

            // New Chat Button
            if (isCollapsed) {
                val newChatInteraction = remember { MutableInteractionSource() }
                val isNewChatHovered by newChatInteraction.collectIsHoveredAsState()
                val newChatBg by animateColorAsState(
                    targetValue = if (isNewChatHovered) PrimaryBlue.copy(alpha = 0.2f) else CardDark,
                    animationSpec = tween(120),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(newChatBg)
                        .border(BorderStroke(1.dp, BorderDark), RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = newChatInteraction,
                            indication = null,
                            onClick = onNewConversation,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = PrimaryBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                val newChatInteraction = remember { MutableInteractionSource() }
                val isNewChatHovered by newChatInteraction.collectIsHoveredAsState()
                val newChatBg by animateColorAsState(
                    targetValue = if (isNewChatHovered) CardDark else CardDark.copy(alpha = 0.6f),
                    animationSpec = tween(120),
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = newChatBg,
                    border = BorderStroke(1.dp, if (isNewChatHovered) BorderDarkLight else BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable(
                            interactionSource = newChatInteraction,
                            indication = null,
                            onClick = onNewConversation,
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "+",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text = "Новый чат",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Conversations List Header
            if (!isCollapsed) {
                Text(
                    text = "ИСТОРИЯ ДИАЛОГОВ",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }

            // Conversations List (Independent scrolling)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == activeConversationId
                    ConversationItem(
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
            HorizontalDivider(color = BorderDark)
            Spacer(modifier = Modifier.height(10.dp))

            // Workspace Section
            if (isCollapsed) {
                val wsInteraction = remember { MutableInteractionSource() }
                val isWsHovered by wsInteraction.collectIsHoveredAsState()
                val wsBg by animateColorAsState(
                    targetValue = if (isWsHovered) CardDark else CardDark.copy(alpha = 0.5f),
                    animationSpec = tween(120),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(wsBg)
                        .border(BorderStroke(1.dp, BorderDark), RoundedCornerShape(8.dp))
                        .clickable(
                            enabled = !isRunning,
                            interactionSource = wsInteraction,
                            indication = null,
                            onClick = onChangeWorkspace,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📁", fontSize = 16.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardDark.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "📁",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            Text(
                                text = "РАБОЧАЯ ПАПКА",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                letterSpacing = 0.5.sp,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val folderName = workspacePath.fileName?.toString() ?: workspacePath.toString()
                        Text(
                            text = folderName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Text(
                            text = workspacePath.toString(),
                            fontSize = 10.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        OutlinedButton(
                            onClick = onChangeWorkspace,
                            enabled = !isRunning,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPrimary,
                                disabledContentColor = TextMuted,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, BorderDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp),
                        ) {
                            Text(
                                text = if (isRunning) "Выполняется..." else "Сменить папку",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Settings Button
            if (isCollapsed) {
                val setInteraction = remember { MutableInteractionSource() }
                val isSetHovered by setInteraction.collectIsHoveredAsState()
                val setBg by animateColorAsState(
                    targetValue = if (isSetHovered) CardDark else Color.Transparent,
                    animationSpec = tween(120),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(setBg)
                        .clickable(
                            interactionSource = setInteraction,
                            indication = null,
                            onClick = onOpenSettings,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙️", fontSize = 16.sp)
                }
            } else {
                val setInteraction = remember { MutableInteractionSource() }
                val isSetHovered by setInteraction.collectIsHoveredAsState()
                val setBg by animateColorAsState(
                    targetValue = if (isSetHovered) CardDark else CardDark.copy(alpha = 0.4f),
                    animationSpec = tween(120),
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = setBg,
                    border = BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clickable(
                            interactionSource = setInteraction,
                            indication = null,
                            onClick = onOpenSettings,
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚙️", fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = "Настройки",
                            fontSize = 12.sp,
                            color = if (isSetHovered) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    isCollapsed: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val targetBg = when {
        isSelected -> PrimaryBlue.copy(alpha = 0.12f)
        isHovered -> CardDark.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val itemBg by animateColorAsState(targetValue = targetBg, animationSpec = tween(120))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(itemBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = if (isCollapsed) 6.dp else 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        if (isCollapsed) {
            Text(
                text = "💬",
                fontSize = 13.sp,
                color = if (isSelected) PrimaryBlue else TextMuted,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 14.dp)
                            .background(PrimaryBlue, RoundedCornerShape(1.5.dp))
                            .padding(end = 6.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = conversation.title,
                    color = if (isSelected) PrimaryBlue else if (isHovered) TextPrimary else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (canDelete) {
                val deleteInteraction = remember { MutableInteractionSource() }
                val isDeleteHovered by deleteInteraction.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = deleteInteraction,
                            indication = null,
                            onClick = onDelete,
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "×",
                        color = if (isDeleteHovered) DangerRed else TextMuted.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
