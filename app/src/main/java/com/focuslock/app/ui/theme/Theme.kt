package com.focuslock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focuslock.app.domain.ThemeMode

val Purple = Color(0xFF8B7CFF)
val PurpleDeep = Color(0xFF6C5CE7)
val Blue = Color(0xFF4DA3FF)
val Charcoal = Color(0xFF121214)
val CharcoalCard = Color(0xFF1C1C21)
val CharcoalHigh = Color(0xFF26262D)

private val Dark = darkColorScheme(
    primary = Purple,
    onPrimary = Color(0xFF14102E),
    secondary = Blue,
    onSecondary = Color(0xFF06223F),
    background = Charcoal,
    onBackground = Color(0xFFEDEDF2),
    surface = Charcoal,
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = Color(0xFFB4B4C0),
    surfaceContainer = CharcoalCard,
    surfaceContainerHigh = CharcoalHigh,
    outline = Color(0xFF50505C),
    error = Color(0xFFFF8A80),
)

private val Light = lightColorScheme(
    primary = PurpleDeep,
    onPrimary = Color.White,
    secondary = Color(0xFF1F6FD1),
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF16161A),
    surface = Color(0xFFF7F6FB),
    onSurface = Color(0xFF16161A),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF52525E),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDEBF7),
    outline = Color(0xFF8C8C9A),
    error = Color(0xFFB3261E),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun FocusLockTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = AppShapes,
        content = content,
    )
}
