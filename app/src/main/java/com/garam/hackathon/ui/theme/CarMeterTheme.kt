package com.garam.hackathon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Day: white and near-black like the Pleos system screens, with one blue for actions and
// bars and one orange for "more than before". Text pairs keep at least 4.5:1 contrast.
private val Light = lightColorScheme(
    primary = Color(0xFF1F5FD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3ECFB),
    onPrimaryContainer = Color(0xFF0B2F6B),
    secondary = Color(0xFFB4530A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCEBDD),
    onSecondaryContainer = Color(0xFF5A2600),
    background = Color.White,
    onBackground = Color(0xFF111418),
    surface = Color.White,
    onSurface = Color(0xFF111418),
    surfaceVariant = Color(0xFFF2F4F7),
    onSurfaceVariant = Color(0xFF4A5361),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF2F4F7),
    surfaceContainerHigh = Color(0xFFECEEF2),
    surfaceContainerHighest = Color(0xFFE6E9EE),
    outline = Color(0xFF8A93A0),
    outlineVariant = Color(0xFFD9DDE3),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF5F1412),
)

// Night: near-black surfaces so the screen does not glare.
private val Dark = darkColorScheme(
    primary = Color(0xFF8AB8FF),
    onPrimary = Color(0xFF0A2350),
    primaryContainer = Color(0xFF16345F),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFFFFB36B),
    onSecondary = Color(0xFF3A1C00),
    secondaryContainer = Color(0xFF4A2A0C),
    onSecondaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFEEF1F4),
    surface = Color(0xFF0F1114),
    onSurface = Color(0xFFEEF1F4),
    surfaceVariant = Color(0xFF1C2026),
    onSurfaceVariant = Color(0xFFAAB3BE),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF15181C),
    surfaceContainer = Color(0xFF1C2026),
    surfaceContainerHigh = Color(0xFF23282F),
    surfaceContainerHighest = Color(0xFF2A3038),
    outline = Color(0xFF7C8692),
    outlineVariant = Color(0xFF343A42),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A07),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD5),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/**
 * Read at arm's length while seated: nothing smaller than 24sp, so secondary text stays
 * above the 3 mm the Pleos guide asks for. Every style is set, so no Material default
 * (11 to 16sp) can slip in through a component.
 */
private val CarTypography = Typography(
    displayLarge = style(72, 80, FontWeight.Bold),
    displayMedium = style(56, 64, FontWeight.Bold),
    displaySmall = style(44, 52, FontWeight.SemiBold),
    headlineLarge = style(40, 48, FontWeight.SemiBold),
    headlineMedium = style(36, 44, FontWeight.SemiBold),
    headlineSmall = style(32, 40, FontWeight.SemiBold),
    titleLarge = style(30, 38, FontWeight.SemiBold),
    titleMedium = style(26, 36, FontWeight.Medium),
    titleSmall = style(24, 34, FontWeight.Medium),
    bodyLarge = style(26, 38),
    bodyMedium = style(24, 36),
    bodySmall = style(24, 36),
    labelLarge = style(24, 32, FontWeight.Medium),
    labelMedium = style(24, 32, FontWeight.Medium),
    labelSmall = style(24, 32),
)

private val CarShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Follows the system's day and night mode, which the car switches with the light outside. */
@Composable
fun CarMeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = CarTypography,
        shapes = CarShapes,
        content = content,
    )
}
