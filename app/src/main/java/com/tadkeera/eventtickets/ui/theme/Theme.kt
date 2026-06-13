package com.tadkeera.eventtickets.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue700 = Color(0xFF1976D2)
private val Orange700 = Color(0xFFFF6F00)

private val DarkColorScheme = darkColorScheme(
    primary = Blue700,
    secondary = Orange700,
    tertiary = Color.Pink800
)

private val LightColorScheme = lightColorScheme(
    primary = Blue700,
    secondary = Orange700,
    tertiary = Color.Pink400
)

@Composable
fun TadkeeraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
