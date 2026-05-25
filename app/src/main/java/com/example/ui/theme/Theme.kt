package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SendaDarkColorScheme = darkColorScheme(
    primary = LavenderD0,
    onPrimary = DeepPurple38,
    primaryContainer = BalancedEADD,
    onPrimaryContainer = DeepIndigo21,
    secondary = LavenderD0,
    onSecondary = DeepPurple38,
    tertiary = LavenderD0,
    onTertiary = DeepPurple38,
    background = PureDarkBg,
    onBackground = WhiteE6,
    surface = SurfaceM3Dark,
    onSurface = WhiteE6,
    surfaceVariant = OutlineM3Dark,
    onSurfaceVariant = GrayCA,
    error = HTMLDangerRoseText,
    onError = DeepPurple38,
    outline = OutlineM3Dark
)

private val SendaLightColorScheme = lightColorScheme(
    primary = DeepPurple38,
    onPrimary = Color.White,
    primaryContainer = BalancedEADD,
    onPrimaryContainer = DeepIndigo21,
    secondary = LavenderD0,
    onSecondary = DeepPurple38,
    tertiary = HTMLSafeGreenText,
    onTertiary = DeepPurple38,
    background = Color(0xFFF6F5FA),
    onBackground = DeepIndigo21,
    surface = Color.White,
    onSurface = DeepIndigo21,
    surfaceVariant = BalancedEADD,
    onSurfaceVariant = DeepIndigo21,
    error = Rose500,
    onError = Color.White,
    outline = OutlineM3Dark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        SendaDarkColorScheme
    } else {
        SendaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
