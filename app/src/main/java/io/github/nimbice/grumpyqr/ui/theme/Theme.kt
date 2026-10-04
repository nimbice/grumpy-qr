package io.github.nimbice.grumpyqr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The mustard from the app icon. */
val BrandMustard = Color(0xFFF4C430)

/** The near-black "ink" from the app icon. */
val BrandInk = Color(0xFF1F1A10)

private val LightColors = lightColorScheme(
    primary = Color(0xFF725C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE07F),
    onPrimaryContainer = Color(0xFF231B00),
    secondary = Color(0xFF675E40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE2BC),
    onSecondaryContainer = Color(0xFF211B04),
    tertiary = Color(0xFF44664F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC6ECCF),
    onTertiaryContainer = Color(0xFF002110),
    background = Color(0xFFFFF8F0),
    onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFEBE2CF),
    onSurfaceVariant = Color(0xFF4C4639),
    surfaceTint = Color(0xFF725C00),
    surfaceDim = Color(0xFFE1D9CC),
    surfaceBright = Color(0xFFFFF8F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF3E5),
    surfaceContainer = Color(0xFFF5EDDF),
    surfaceContainerHigh = Color(0xFFEFE7DA),
    surfaceContainerHighest = Color(0xFFE9E2D4),
    inverseSurface = Color(0xFF343027),
    inverseOnSurface = Color(0xFFF8F0E2),
    inversePrimary = Color(0xFFE6C449),
    outline = Color(0xFF7D7767),
    outlineVariant = Color(0xFFCEC6B4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE6C449),
    onPrimary = Color(0xFF3C2F00),
    primaryContainer = Color(0xFF564500),
    onPrimaryContainer = Color(0xFFFFE07F),
    secondary = Color(0xFFD2C6A1),
    onSecondary = Color(0xFF373016),
    secondaryContainer = Color(0xFF4E4628),
    onSecondaryContainer = Color(0xFFEFE2BC),
    tertiary = Color(0xFFABD0B4),
    onTertiary = Color(0xFF163724),
    tertiaryContainer = Color(0xFF2D4E38),
    onTertiaryContainer = Color(0xFFC6ECCF),
    background = Color(0xFF16130B),
    onBackground = Color(0xFFE9E2D4),
    surface = Color(0xFF16130B),
    onSurface = Color(0xFFE9E2D4),
    surfaceVariant = Color(0xFF4C4639),
    onSurfaceVariant = Color(0xFFCEC6B4),
    surfaceTint = Color(0xFFE6C449),
    surfaceDim = Color(0xFF16130B),
    surfaceBright = Color(0xFF3D392F),
    surfaceContainerLowest = Color(0xFF110E07),
    surfaceContainerLow = Color(0xFF1E1B13),
    surfaceContainer = Color(0xFF231F17),
    surfaceContainerHigh = Color(0xFF2D2A21),
    surfaceContainerHighest = Color(0xFF38342B),
    inverseSurface = Color(0xFFE9E2D4),
    inverseOnSurface = Color(0xFF343027),
    inversePrimary = Color(0xFF725C00),
    outline = Color(0xFF979080),
    outlineVariant = Color(0xFF4C4639),
)

@Composable
fun GrumpyQRTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
