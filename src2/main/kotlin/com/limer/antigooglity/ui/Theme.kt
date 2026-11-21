package com.limer.antigooglity.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Antigravity Design System Color Palette (Reference DOCX)
val Background = Color(0xFF0D0D0E)
val SidebarBg = Color(0xFF111113)
val SurfaceBg = Color(0xFF161618)
val SurfaceElevated = Color(0xFF1E1E22)
val BorderColor = Color(0xFF2A2A2F)
val BorderLight = Color(0xFF38383F)
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)
val TextMuted = Color(0xFF6B7280)
val SuccessGreen = Color(0xFF22C55E)
val DangerRed = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)

// Accents
val AccentPurple = Color(0xFF818CF8)
val AccentBlue = Color(0xFF3B82F6)

// Compatibility aliases for existing components
val BgDark = Background
val PanelDark = SidebarBg
val CardDark = SurfaceBg
val BgCard = SurfaceElevated
val BorderDark = BorderColor
val BorderDarkLight = BorderLight
val PrimaryBlue = AccentBlue
val PrimaryBlueHover = Color(0xFF2563EB)
val AccentTeal = Color(0xFF14B8A6)
val MutedGray = TextMuted

val AntigooglityColorScheme = darkColorScheme(
    background = Background,
    surface = SurfaceBg,
    surfaceVariant = SurfaceElevated,
    primary = AccentBlue,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    error = DangerRed,
)

val AntigooglityShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun AntigooglityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AntigooglityColorScheme,
        shapes = AntigooglityShapes,
        content = content,
    )
}
