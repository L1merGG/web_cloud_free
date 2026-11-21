package com.limer.antigooglity.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomInputBar(
    statusText: String,
    isRunning: Boolean,
    onSendMessage: (String) -> Unit,
    onCancelTask: () -> Unit,
    initialText: String = "",
    onClearInitialText: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }

    if (initialText.isNotBlank()) {
        text = initialText
        onClearInitialText()
    }

    val canSend = text.isNotBlank() && !isRunning
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Modern AI Composer Container
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = CardDark,
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // Multiline text input area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp, max = 150.dp)
                        .verticalScroll(scrollState),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = if (isRunning) "Агент выполняет задачу..." else "Спросить Antigooglity…",
                            color = TextMuted,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    BasicTextField(
                        value = text,
                        onValueChange = { if (!isRunning) text = it },
                        enabled = !isRunning,
                        textStyle = TextStyle(
                            color = if (isRunning) TextMuted else TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                        ),
                        cursorBrush = SolidColor(PrimaryBlue),
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

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom actions row within composer: Attachment placeholder (left) and Send/Stop (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Attachment button (visual placeholder for future files)
                    AttachmentPlaceholderButton(enabled = !isRunning)

                    // Right side: Send or Stop button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Ctrl+Enter",
                            fontSize = 10.sp,
                            color = TextMuted.copy(alpha = 0.6f),
                        )

                        if (isRunning) {
                            // Stop button
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed)
                                    .clickable { onCancelTask() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("⏹", color = Color.White, fontSize = 13.sp)
                            }
                        } else {
                            // Send button
                            val sendBgColor by animateColorAsState(
                                targetValue = if (canSend) PrimaryBlue else Color(0xFF2A2D35),
                                animationSpec = tween(durationMillis = 150),
                            )
                            val sendTextColor by animateColorAsState(
                                targetValue = if (canSend) Color.White else TextMuted,
                                animationSpec = tween(durationMillis = 150),
                            )

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
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
                                Text(
                                    text = "↑",
                                    color = sendTextColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Subtle status caption below input
        if (statusText.isNotBlank() && statusText != "Готов к работе") {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
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
private fun AttachmentPlaceholderButton(
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = if (isHovered && enabled) CardDark.copy(alpha = 0.8f) else Color.Transparent,
        animationSpec = tween(120),
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                // Future attachment placeholder
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "📎",
            fontSize = 13.sp,
            color = if (enabled) TextSecondary else TextMuted,
        )
    }
}
