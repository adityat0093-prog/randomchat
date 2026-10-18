package com.example.randomchat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF0E1016)
    val Surface = Color(0xFF171A24)
    val SurfaceHi = Color(0xFF242838)
    val Accent = Color(0xFF7C6CFF)
    val Accent2 = Color(0xFF4F8CFF)
    val TextPrimary = Color(0xFFF2F3F8)
    val TextSecondary = Color(0xFF9AA0B4)
    val Online = Color(0xFF3DDC97)
    val Danger = Color(0xFFFF6B6B)

    val AccentGradient = Brush.linearGradient(listOf(Accent, Accent2))
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    background = Palette.Bg,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHi,
    onSurfaceVariant = Palette.TextSecondary,
    error = Palette.Danger,
)

@Composable
fun RandomChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
