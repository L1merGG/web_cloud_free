package com.limer.antigooglity.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limer.antigooglity.coordinator.ToolStatus
import com.limer.antigooglity.coordinator.UiToolExecution

@Composable
fun ToolCard(
    tool: UiToolExecution,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    val statusColor = when (tool.status) {
        ToolStatus.RUNNING -> AccentBlue
        ToolStatus.SUCCESS -> SuccessGreen
        ToolStatus.FAILED -> DangerRed
        ToolStatus.CANCELLED -> MutedGray
    }

    val statusText = when (tool.status) {
        ToolStatus.RUNNING -> "Выполняется"
        ToolStatus.SUCCESS -> "Успешно"
        ToolStatus.FAILED -> "Ошибка"
        ToolStatus.CANCELLED -> "Отменено"
    }

    val badgeIcon = when (tool.status) {
        ToolStatus.RUNNING -> "●"
        ToolStatus.SUCCESS -> "✓"
        ToolStatus.FAILED -> "✕"
        ToolStatus.CANCELLED -> "⏹"
    }

    val parsedDetails = remember(tool.arguments, tool.toolName) {
        parseToolDetails(tool.toolName, tool.arguments)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderDark),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            // Заголовок карточки инструмента
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Бейдж категории инструмента
                Box(
                    modifier = Modifier
                        .background(
                            color = parsedDetails.categoryColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = parsedDetails.categoryBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = parsedDetails.categoryColor,
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Название операции
                Text(
                    text = parsedDetails.operationName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )

                Spacer(Modifier.weight(1f))

                // Статус выполнения
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (tool.status == ToolStatus.RUNNING) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = AccentBlue,
                            modifier = Modifier.size(12.dp),
                        )
                    } else {
                        Text(
                            text = badgeIcon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                        )
                    }
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                    )
                }
            }

            // Целевой путь или команда
            if (parsedDetails.targetInfo.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgDark, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = parsedDetails.targetLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = parsedDetails.targetInfo,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Кнопка разворачивания результата/аргументов
            val result = tool.result
            if (result != null || tool.status == ToolStatus.RUNNING) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = if (isExpanded) "Скрыть подробности ▴" else "Показать вывод ▾",
                        fontSize = 11.sp,
                        color = AccentBlue,
                        fontWeight = FontWeight.Medium,
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    ) {
                        if (result != null) {
                            Text(
                                text = "Вывод операции:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .background(BgDark, RoundedCornerShape(6.dp))
                                    .border(BorderStroke(1.dp, BorderDark), RoundedCornerShape(6.dp))
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp),
                            ) {
                                Text(
                                    text = result.content,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (result.success) TextPrimary else DangerRed,
                                    lineHeight = 16.sp,
                                )
                            }
                        } else {
                            Text(
                                text = "Инструмент ещё выполняется...",
                                fontSize = 11.sp,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ParsedToolDetails(
    val categoryBadge: String,
    val categoryColor: Color,
    val operationName: String,
    val targetLabel: String,
    val targetInfo: String,
)

private fun parseToolDetails(toolName: String, rawArgs: String): ParsedToolDetails {
    val categoryColor = when {
        toolName.contains("file", ignoreCase = true) || toolName.contains("directory", ignoreCase = true) -> AccentTeal
        toolName.contains("command", ignoreCase = true) || toolName.contains("terminal", ignoreCase = true) -> AccentPurple
        else -> AccentBlue
    }

    val categoryBadge = when {
        toolName.contains("file", ignoreCase = true) || toolName.contains("directory", ignoreCase = true) -> "FS"
        toolName.contains("command", ignoreCase = true) || toolName.contains("terminal", ignoreCase = true) -> "Terminal"
        else -> "Tool"
    }

    val (opName, targetLabel, targetInfo) = when (toolName) {
        "read_file" -> Triple("Чтение файла", "Файл:", extractParam(rawArgs, "path"))
        "write_file" -> Triple("Запись файла", "Файл:", extractParam(rawArgs, "path"))
        "list_directory" -> Triple("Обзор каталога", "Путь:", extractParam(rawArgs, "path").ifEmpty { "." })
        "create_directory" -> Triple("Создание каталога", "Каталог:", extractParam(rawArgs, "path"))
        "delete_file" -> Triple("Удаление файла", "Файл:", extractParam(rawArgs, "path"))
        "execute_command" -> {
            val cmd = extractParam(rawArgs, "command")
            val args = extractArgsList(rawArgs)
            val fullCmd = if (args.isNotEmpty()) "$cmd $args" else cmd
            Triple("Выполнение команды", "Команда:", fullCmd)
        }
        else -> Triple(toolName, "Аргументы:", rawArgs.take(80))
    }

    return ParsedToolDetails(
        categoryBadge = categoryBadge,
        categoryColor = categoryColor,
        operationName = opName,
        targetLabel = targetLabel,
        targetInfo = targetInfo,
    )
}

private fun extractParam(json: String, param: String): String {
    val regex = "\"$param\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    return regex.find(json)?.groupValues?.get(1) ?: ""
}

private fun extractArgsList(json: String): String {
    val regex = "\"args\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
    val match = regex.find(json)?.groupValues?.get(1) ?: return ""
    return match.split(",")
        .map { it.trim().removeSurrounding("\"") }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}
