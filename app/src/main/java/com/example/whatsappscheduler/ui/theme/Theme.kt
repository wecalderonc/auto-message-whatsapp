package com.example.whatsappscheduler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenPrimary = Color(0xFF0B6E4F)
private val GreenSecondary = Color(0xFF1B9C85)
private val Sand = Color(0xFFF3EFE7)
private val Ink = Color(0xFF12231C)

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    secondary = GreenSecondary,
    background = Sand,
    surface = Color(0xFFFFFBF5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = GreenSecondary,
    secondary = GreenPrimary,
    background = Color(0xFF0E1713),
    surface = Color(0xFF15221C),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFE8F0EB),
    onSurface = Color(0xFFE8F0EB),
    error = Color(0xFFF2B8B5)
)

@Composable
fun WhatsAppSchedulerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
