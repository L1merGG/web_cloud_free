package com.limer.antigooglity.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path

@Composable
fun WelcomeView(
    workspacePath: Path,
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            // Antigravity Minimalist Center Arch Logo (Reference DOCX Image 1 - no box frame)
            AntigravityIcons.CenterLogo(
                size = 54.dp,
                color = Color(0xFF383B42),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Greeting (Reference DOCX Image 1)
            Text(
                text = "Good to see you. What's next?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
                color = TextPrimary,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Quick suggestion rows (Reference DOCX Image 1: compact rows, small icons)
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                CompactSuggestionRow(
                    icon = { AntigravityIcons.Globe(modifier = Modifier.size(14.dp), color = AccentBlue) },
                    text = "Build me a web app",
                    onClick = { onSelectSuggestion("Build me a web app") },
                )
                CompactSuggestionRow(
                    icon = { AntigravityIcons.Sparkle(modifier = Modifier.size(14.dp), color = Color(0xFF34D399)) },
                    text = "Add a Gemini feature to my app",
                    onClick = { onSelectSuggestion("Add a Gemini feature to my app") },
                )
                CompactSuggestionRow(
                    icon = { AntigravityIcons.Logo(modifier = Modifier.size(13.dp), color = TextSecondary) },
                    text = "Help me understand this project",
                    onClick = { onSelectSuggestion("Help me understand this project and inspect its architecture") },
                )
            }
        }
    }
}

@Composable
private fun CompactSuggestionRow(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = if (isHovered) SurfaceElevated.copy(alpha = 0.7f) else Color.Transparent,
        animationSpec = tween(100),
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (isHovered) TextPrimary else TextSecondary,
            fontWeight = FontWeight.Normal,
        )
    }
}
