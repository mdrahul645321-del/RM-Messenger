package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.core.model.ThemeMode

private val RmLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = PureWhite,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = PureWhite,
    secondary = SkyBlueSecondary,
    onSecondary = PureWhite,
    secondaryContainer = SkyBlueSoftContainer,
    onSecondaryContainer = NavyPrimary,
    tertiary = SkyBlueBright,
    onTertiary = NavyDeep,
    background = PureWhite,
    onBackground = TextPrimaryLight,
    surface = PureWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = MistSurface,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = MistSurface,
    surfaceContainer = FrostSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE1EAF4),
    outline = OutlineLight,
    outlineVariant = Color(0xFFE3ECF5),
    error = ErrorCrimson,
    onError = PureWhite,
)

private val RmDarkColorScheme = darkColorScheme(
    primary = SkyBlueBright,
    onPrimary = NavyDeep,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = PureWhite,
    secondary = SkyBlueSecondary,
    onSecondary = NavyDeep,
    secondaryContainer = SkyBlueDarkContainer,
    onSecondaryContainer = SkyBlueSoftContainer,
    tertiary = SkyBlueBright,
    onTertiary = NavyDeep,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceElevated,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = OutlineDark,
    outlineVariant = Color(0xFF1B3654),
    error = ErrorDarkCrimson,
    onError = NavyDeep,
)

@Composable
fun RmMessengerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (useDarkTheme) RmDarkColorScheme else RmLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RmTypography,
        shapes = RmShapes,
        content = content,
    )
}
