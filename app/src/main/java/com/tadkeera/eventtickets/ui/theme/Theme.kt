package com.tadkeera.eventtickets.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium, Elegant Dark Mode Colors matching 2026 UX/UI Trends!
val DeepSlateNavy = Color(0xFF0F172A)  // Deep slate background
val CardSlate = Color(0xFF1E293B)      // Slightly lighter slate for card surfaces
val RoyalPurple = Color(0xFF4F46E5)    // Indigo/Royal purple primary accent
val VividPurple = Color(0xFF6366F1)    // Vibrant indigo for highlight states
val EmeraldGreen = Color(0xFF10B981)   // Emerald green success indicator
val CrimsonRed = Color(0xFFEF4444)     // Crimson red delete/error indicator
val TextWhite = Color(0xFFFFFFFF)      // Pure white text
val TextMuted = Color(0xFF94A3B8)      // Muted slate gray for secondary labels

private val TadkeeraColorScheme = darkColorScheme(
    primary = RoyalPurple,
    onPrimary = TextWhite,
    primaryContainer = CardSlate,
    onPrimaryContainer = TextWhite,
    secondary = VividPurple,
    onSecondary = TextWhite,
    secondaryContainer = CardSlate,
    onSecondaryContainer = TextMuted,
    background = DeepSlateNavy,
    onBackground = TextWhite,
    surface = CardSlate,
    onSurface = TextWhite,
    surfaceVariant = Color(0xFF334155), // Border outline slate
    onSurfaceVariant = TextMuted,
    error = CrimsonRed,
    onError = TextWhite
)

@Composable
fun TadkeeraTheme(
    darkTheme: Boolean = true, // Force premium dark mode first!
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TadkeeraColorScheme,
        content = content
    )
}
