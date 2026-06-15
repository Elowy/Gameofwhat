package com.gameofwhat.arena.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0B1021)
val Surface = Color(0xFF161C36)
val Primary = Color(0xFF4DD0E1)
val Secondary = Color(0xFF7C4DFF)
val Danger = Color(0xFFFF6E6E)
val OnDark = Color(0xFFE8ECF8)

/** Distinct player colours, indexed by join order. */
val PlayerColors = listOf(
    Color(0xFF4DD0E1), // cyan
    Color(0xFFFFB74D), // orange
    Color(0xFF81C784), // green
    Color(0xFFF06292), // pink
    Color(0xFFBA68C8), // purple
    Color(0xFFFFF176), // yellow
)

private val DarkColors = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    onPrimary = Color(0xFF06121A),
    onBackground = OnDark,
    onSurface = OnDark,
    error = Danger,
)

@Composable
fun GameOfWhatTheme(content: @Composable () -> Unit) {
    // Always dark — it is an arena game.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkColors, content = content)
}
