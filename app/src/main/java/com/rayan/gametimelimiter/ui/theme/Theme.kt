package com.rayan.gametimelimiter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Same palette as the desktop app. */
object C {
    val Bg = Color(0xFF0B0C13)
    val Surface = Color(0xFF13141F)
    val Surface2 = Color(0xFF1A1C29)
    val Surface3 = Color(0xFF232637)
    val Border = Color(0x12FFFFFF)
    val BorderStrong = Color(0x21FFFFFF)
    val Text = Color(0xFFECEEF7)
    val Muted = Color(0xFF8B90A8)
    val Faint = Color(0xFF5D6178)
    val Accent = Color(0xFF8B7BFF)
    val AccentDeep = Color(0xFF7462F5)
    val Accent2 = Color(0xFF36D1C4)
    val AccentSoft = Color(0x248B7BFF)
    val Ok = Color(0xFF3DDC97)
    val Warn = Color(0xFFFFB547)
    val Danger = Color(0xFFFF5D6C)
}

private val scheme = darkColorScheme(
    primary = C.Accent,
    onPrimary = Color.White,
    secondary = C.Accent2,
    background = C.Bg,
    onBackground = C.Text,
    surface = C.Surface,
    onSurface = C.Text,
    surfaceVariant = C.Surface2,
    onSurfaceVariant = C.Muted,
    outline = C.BorderStrong,
    error = C.Danger,
)

@Composable
fun GtlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
