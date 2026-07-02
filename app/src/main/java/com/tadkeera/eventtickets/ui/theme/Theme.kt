package com.tadkeera.eventtickets.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Brand colors
val OrangePrimary = Color(0xFFFF6D00)    // Main active orange
val BlueSecondary = Color(0xFF1976D2)   // Secondary blue
val NavyDark = Color(0xFF111E38)        // Navy text / header background
val LightGrayBg = Color(0xFFF8F9FA)     // Page background
val WhiteSurface = Color(0xFFFFFFFF)    // Card surface
val PurpleTertiary = Color(0xFF7B1FA2)  // Purple button/accent
val TealAccent = Color(0xFF009688)      // Teal button/accent

private val LightTadkeeraColorScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = Color.White,
    secondary = BlueSecondary,
    onSecondary = Color.White,
    tertiary = PurpleTertiary,
    onTertiary = Color.White,
    background = LightGrayBg,
    onBackground = NavyDark,
    surface = WhiteSurface,
    onSurface = NavyDark,
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = NavyDark,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

@Composable
fun TadkeeraTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val customFontFamily = remember {
        try {
            // Cairo is a beautiful, highly professional modern Arabic font present in assets
            FontFamily(
                Font(
                    path = "fonts/cairo.ttf",
                    assetManager = context.assets,
                    weight = FontWeight.Normal
                ),
                Font(
                    path = "fonts/cairo.ttf",
                    assetManager = context.assets,
                    weight = FontWeight.Bold
                )
            )
        } catch (e: Exception) {
            FontFamily.Default
        }
    }

    // Optimize performance: Cache the entire Typography object using remember so it isn't recreated on every recomposition!
    val typography = remember(customFontFamily) {
        val baseTypography = androidx.compose.material3.Typography()
        androidx.compose.material3.Typography(
            displayLarge = baseTypography.displayLarge.copy(fontFamily = customFontFamily),
            displayMedium = baseTypography.displayMedium.copy(fontFamily = customFontFamily),
            displaySmall = baseTypography.displaySmall.copy(fontFamily = customFontFamily),
            headlineLarge = baseTypography.headlineLarge.copy(fontFamily = customFontFamily),
            headlineMedium = baseTypography.headlineMedium.copy(fontFamily = customFontFamily),
            headlineSmall = baseTypography.headlineSmall.copy(fontFamily = customFontFamily),
            titleLarge = baseTypography.titleLarge.copy(fontFamily = customFontFamily),
            titleMedium = baseTypography.titleMedium.copy(fontFamily = customFontFamily),
            titleSmall = baseTypography.titleSmall.copy(fontFamily = customFontFamily),
            bodyLarge = baseTypography.bodyLarge.copy(fontFamily = customFontFamily),
            bodyMedium = baseTypography.bodyMedium.copy(fontFamily = customFontFamily),
            bodySmall = baseTypography.bodySmall.copy(fontFamily = customFontFamily),
            labelLarge = baseTypography.labelLarge.copy(fontFamily = customFontFamily),
            labelMedium = baseTypography.labelMedium.copy(fontFamily = customFontFamily),
            labelSmall = baseTypography.labelSmall.copy(fontFamily = customFontFamily)
        )
    }

    MaterialTheme(
        colorScheme = LightTadkeeraColorScheme,
        typography = typography,
        content = content
    )
}
