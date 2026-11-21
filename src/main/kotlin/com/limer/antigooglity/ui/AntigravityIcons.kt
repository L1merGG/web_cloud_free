package com.limer.antigooglity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clean, lightweight vector outline icons matching the Google Antigravity design system.
 * Zero external icon libraries, zero colored emojis, consistent 1.5dp stroke width.
 */
object AntigravityIcons {

    @Composable
    fun Logo(
        modifier: Modifier = Modifier.size(18.dp),
        color: Color = TextPrimary,
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val strokeWidth = 2.dp.toPx()
            
            // Antigravity smooth arch / chevron mark
            val path = Path().apply {
                moveTo(w * 0.15f, h * 0.85f)
                cubicTo(
                    w * 0.25f, h * 0.45f,
                    w * 0.35f, h * 0.15f,
                    w * 0.50f, h * 0.15f
                )
                cubicTo(
                    w * 0.65f, h * 0.15f,
                    w * 0.75f, h * 0.45f,
                    w * 0.85f, h * 0.85f
                )
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    @Composable
    fun CenterLogo(
        modifier: Modifier.Companion = Modifier,
        size: Dp = 48.dp,
        color: Color = Color(0xFF4B4F58),
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val strokeWidth = 5.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.18f, h * 0.88f)
                cubicTo(
                    w * 0.28f, h * 0.40f,
                    w * 0.38f, h * 0.12f,
                    w * 0.50f, h * 0.12f
                )
                cubicTo(
                    w * 0.62f, h * 0.12f,
                    w * 0.72f, h * 0.40f,
                    w * 0.82f, h * 0.88f
                )
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    @Composable
    fun Folder(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.1f, h * 0.3f)
                lineTo(w * 0.4f, h * 0.3f)
                lineTo(w * 0.5f, h * 0.45f)
                lineTo(w * 0.9f, h * 0.45f)
                lineTo(w * 0.9f, h * 0.85f)
                lineTo(w * 0.1f, h * 0.85f)
                close()
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun FolderPlus(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.1f, h * 0.3f)
                lineTo(w * 0.4f, h * 0.3f)
                lineTo(w * 0.5f, h * 0.45f)
                lineTo(w * 0.9f, h * 0.45f)
                lineTo(w * 0.9f, h * 0.85f)
                lineTo(w * 0.1f, h * 0.85f)
                close()
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
            // Plus
            drawLine(color, Offset(w * 0.75f, h * 0.55f), Offset(w * 0.75f, h * 0.75f), strokeWidth = sw)
            drawLine(color, Offset(w * 0.65f, h * 0.65f), Offset(w * 0.85f, h * 0.65f), strokeWidth = sw)
        }
    }

    @Composable
    fun FolderSlash(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.15f, h * 0.25f),
                size = Size(w * 0.7f, h * 0.55f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = sw)
            )
            drawLine(color, Offset(w * 0.15f, h * 0.8f), Offset(w * 0.85f, h * 0.25f), strokeWidth = sw)
        }
    }

    @Composable
    fun File(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.2f, h * 0.15f)
                lineTo(w * 0.6f, h * 0.15f)
                lineTo(w * 0.8f, h * 0.35f)
                lineTo(w * 0.8f, h * 0.85f)
                lineTo(w * 0.2f, h * 0.85f)
                close()
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
            // fold
            drawLine(color, Offset(w * 0.6f, h * 0.15f), Offset(w * 0.6f, h * 0.35f), strokeWidth = sw)
            drawLine(color, Offset(w * 0.6f, h * 0.35f), Offset(w * 0.8f, h * 0.35f), strokeWidth = sw)
        }
    }

    @Composable
    fun Paperclip(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.3f, h * 0.75f)
                lineTo(w * 0.3f, h * 0.35f)
                cubicTo(w * 0.3f, h * 0.15f, w * 0.7f, h * 0.15f, w * 0.7f, h * 0.35f)
                lineTo(w * 0.7f, h * 0.65f)
                cubicTo(w * 0.7f, h * 0.8f, w * 0.45f, h * 0.8f, w * 0.45f, h * 0.65f)
                lineTo(w * 0.45f, h * 0.4f)
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun Computer(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.15f, h * 0.2f),
                size = Size(w * 0.7f, h * 0.45f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = sw)
            )
            // Stand
            drawLine(color, Offset(w * 0.5f, h * 0.65f), Offset(w * 0.5f, h * 0.8f), strokeWidth = sw)
            drawLine(color, Offset(w * 0.3f, h * 0.8f), Offset(w * 0.7f, h * 0.8f), strokeWidth = sw)
        }
    }

    @Composable
    fun Terminal(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.12f, h * 0.2f),
                size = Size(w * 0.76f, h * 0.6f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = sw)
            )
            // >_
            val path = Path().apply {
                moveTo(w * 0.28f, h * 0.4f)
                lineTo(w * 0.42f, h * 0.5f)
                lineTo(w * 0.28f, h * 0.6f)
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(color, Offset(w * 0.48f, h * 0.6f), Offset(w * 0.65f, h * 0.6f), strokeWidth = sw)
        }
    }

    @Composable
    fun Clock(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawCircle(color = color, radius = w * 0.4f, style = Stroke(width = sw))
            drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.68f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Cube(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.32f)
                lineTo(w * 0.85f, h * 0.72f)
                lineTo(w * 0.5f, h * 0.9f)
                lineTo(w * 0.15f, h * 0.72f)
                lineTo(w * 0.15f, h * 0.32f)
                close()
                moveTo(w * 0.5f, h * 0.15f)
                lineTo(w * 0.5f, h * 0.55f)
                moveTo(w * 0.85f, h * 0.32f)
                lineTo(w * 0.5f, h * 0.55f)
                moveTo(w * 0.15f, h * 0.32f)
                lineTo(w * 0.5f, h * 0.55f)
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Settings(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawCircle(color = color, radius = w * 0.22f, style = Stroke(width = sw))
            drawCircle(color = color, radius = w * 0.38f, style = Stroke(width = sw))
            // 4 spokes
            drawLine(color, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.5f, h * 0.22f), strokeWidth = sw)
            drawLine(color, Offset(w * 0.5f, h * 0.78f), Offset(w * 0.5f, h * 0.9f), strokeWidth = sw)
            drawLine(color, Offset(w * 0.1f, h * 0.5f), Offset(w * 0.22f, h * 0.5f), strokeWidth = sw)
            drawLine(color, Offset(w * 0.78f, h * 0.5f), Offset(w * 0.9f, h * 0.5f), strokeWidth = sw)
        }
    }

    @Composable
    fun Eye(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.1f, h * 0.5f)
                cubicTo(w * 0.3f, h * 0.2f, w * 0.7f, h * 0.2f, w * 0.9f, h * 0.5f)
                cubicTo(w * 0.7f, h * 0.8f, w * 0.3f, h * 0.8f, w * 0.1f, h * 0.5f)
                close()
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
            drawCircle(color = color, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.5f))
        }
    }

    @Composable
    fun Lock(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            // Body
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.22f, h * 0.45f),
                size = Size(w * 0.56f, h * 0.45f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = sw)
            )
            // Shackle
            val shackle = Path().apply {
                moveTo(w * 0.32f, h * 0.45f)
                lineTo(w * 0.32f, h * 0.28f)
                cubicTo(w * 0.32f, h * 0.12f, w * 0.68f, h * 0.12f, w * 0.68f, h * 0.28f)
                lineTo(w * 0.68f, h * 0.45f)
            }
            drawPath(path = shackle, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun Shield(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.28f)
                cubicTo(w * 0.85f, h * 0.6f, w * 0.65f, h * 0.82f, w * 0.5f, h * 0.88f)
                cubicTo(w * 0.35f, h * 0.82f, w * 0.15f, h * 0.6f, w * 0.15f, h * 0.28f)
                close()
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ShieldAlert(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.28f)
                cubicTo(w * 0.85f, h * 0.6f, w * 0.65f, h * 0.82f, w * 0.5f, h * 0.88f)
                cubicTo(w * 0.35f, h * 0.82f, w * 0.15f, h * 0.6f, w * 0.15f, h * 0.28f)
                close()
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
            // !
            drawLine(color, Offset(w * 0.5f, h * 0.35f), Offset(w * 0.5f, h * 0.55f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(color, radius = 1.dp.toPx(), center = Offset(w * 0.5f, h * 0.68f))
        }
    }

    @Composable
    fun GitBranch(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(w * 0.3f, h * 0.25f))
            drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(w * 0.3f, h * 0.75f))
            drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(w * 0.7f, h * 0.45f))

            drawLine(color, Offset(w * 0.3f, h * 0.32f), Offset(w * 0.3f, h * 0.68f), strokeWidth = sw)
            val branch = Path().apply {
                moveTo(w * 0.3f, h * 0.55f)
                cubicTo(w * 0.3f, h * 0.45f, w * 0.55f, h * 0.45f, w * 0.65f, h * 0.45f)
            }
            drawPath(path = branch, color = color, style = Stroke(width = sw))
        }
    }

    @Composable
    fun Checkmark(modifier: Modifier = Modifier.size(14.dp), color: Color = AccentBlue) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.5.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.2f, h * 0.5f)
                lineTo(w * 0.45f, h * 0.75f)
                lineTo(w * 0.85f, h * 0.25f)
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Microphone(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.35f, h * 0.15f),
                size = Size(w * 0.3f, h * 0.45f),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = sw)
            )
            val cradle = Path().apply {
                moveTo(w * 0.22f, h * 0.42f)
                cubicTo(w * 0.22f, h * 0.72f, w * 0.78f, h * 0.72f, w * 0.78f, h * 0.42f)
            }
            drawPath(path = cradle, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
            drawLine(color, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.88f), strokeWidth = sw)
        }
    }

    @Composable
    fun SendArrow(modifier: Modifier = Modifier.size(16.dp), color: Color = Color.White) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 2.dp.toPx()

            drawLine(color, Offset(w * 0.5f, h * 0.8f), Offset(w * 0.5f, h * 0.22f), strokeWidth = sw, cap = StrokeCap.Round)
            val arrow = Path().apply {
                moveTo(w * 0.25f, h * 0.45f)
                lineTo(w * 0.5f, h * 0.2f)
                lineTo(w * 0.75f, h * 0.45f)
            }
            drawPath(path = arrow, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ChevronRight(modifier: Modifier = Modifier.size(12.dp), color: Color = TextMuted) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.3f, h * 0.2f)
                lineTo(w * 0.7f, h * 0.5f)
                lineTo(w * 0.3f, h * 0.8f)
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ChevronDown(modifier: Modifier = Modifier.size(12.dp), color: Color = TextMuted) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.2f, h * 0.35f)
                lineTo(w * 0.5f, h * 0.65f)
                lineTo(w * 0.8f, h * 0.35f)
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ActionDiagonal(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.15f, h * 0.15f),
                size = Size(w * 0.7f, h * 0.7f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = sw)
            )
            drawLine(color, Offset(w * 0.35f, h * 0.65f), Offset(w * 0.65f, h * 0.35f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.45f, h * 0.35f), Offset(w * 0.65f, h * 0.35f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.65f, h * 0.35f), Offset(w * 0.65f, h * 0.55f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Globe(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawCircle(color = color, radius = w * 0.4f, style = Stroke(width = sw))
            drawLine(color, Offset(w * 0.1f, h * 0.5f), Offset(w * 0.9f, h * 0.5f), strokeWidth = sw)
            val ellipse = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(w * 0.25f, h * 0.1f, w * 0.75f, h * 0.9f))
            }
            drawPath(path = ellipse, color = color, style = Stroke(width = sw))
        }
    }

    @Composable
    fun Drive(modifier: Modifier = Modifier.size(16.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            val path = Path().apply {
                moveTo(w * 0.35f, h * 0.2f)
                lineTo(w * 0.65f, h * 0.2f)
                lineTo(w * 0.85f, h * 0.6f)
                lineTo(w * 0.7f, h * 0.85f)
                lineTo(w * 0.3f, h * 0.85f)
                lineTo(w * 0.15f, h * 0.6f)
                close()
            }
            drawPath(path = path, color = color, style = Stroke(width = sw, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Briefcase(modifier: Modifier = Modifier.size(18.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.15f, h * 0.32f),
                size = Size(w * 0.7f, h * 0.55f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = sw)
            )
            val handle = Path().apply {
                moveTo(w * 0.35f, h * 0.32f)
                lineTo(w * 0.35f, h * 0.18f)
                lineTo(w * 0.65f, h * 0.18f)
                lineTo(w * 0.65f, h * 0.32f)
            }
            drawPath(path = handle, color = color, style = Stroke(width = sw))
        }
    }

    @Composable
    fun CrossedTools(modifier: Modifier = Modifier.size(18.dp), color: Color = TextSecondary) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val sw = 1.3.dp.toPx()

            // Hammer/Wrench cross
            drawLine(color, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.8f, h * 0.8f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.8f, h * 0.2f), Offset(w * 0.2f, h * 0.8f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(color, radius = 2.dp.toPx(), center = Offset(w * 0.2f, h * 0.2f))
            drawCircle(color, radius = 2.dp.toPx(), center = Offset(w * 0.8f, h * 0.2f))
        }
    }

    @Composable
    fun Sparkle(modifier: Modifier = Modifier.size(16.dp), color: Color = AccentBlue) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.1f)
                cubicTo(w * 0.5f, h * 0.4f, w * 0.6f, h * 0.5f, w * 0.9f, h * 0.5f)
                cubicTo(w * 0.6f, h * 0.5f, w * 0.5f, h * 0.6f, w * 0.5f, h * 0.9f)
                cubicTo(w * 0.5f, h * 0.6f, w * 0.4f, h * 0.5f, w * 0.1f, h * 0.5f)
                cubicTo(w * 0.4f, h * 0.5f, w * 0.5f, h * 0.4f, w * 0.5f, h * 0.1f)
                close()
            }
            drawPath(path = path, color = color)
        }
    }
}
