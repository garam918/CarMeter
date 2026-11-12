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

// Day: pale green-gray canvas, ink text and teal controls.
private val Light = lightColorScheme(
    primary = Color(0xFF006B59),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F4EA),
    onPrimaryContainer = Color(0xFF064B3E),
    secondary = Color(0xFFB4530A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCEBDD),
    onSecondaryContainer = Color(0xFF5A2600),
    background = Color(0xFFF3F7F5),
    onBackground = Color(0xFF102823),
    surface = Color.White,
    onSurface = Color(0xFF102823),
    surfaceVariant = Color(0xFFEDF4F1),
    onSurfaceVariant = Color(0xFF425B54),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F8F6),
    surfaceContainer = Color(0xFFEDF4F1),
    surfaceContainerHigh = Color(0xFFE5EFEB),
    surfaceContainerHighest = Color(0xFFDCEAE4),
    outline = Color(0xFF718980),
    outlineVariant = Color(0xFFD3E1DA),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF5F1412),
)

// Night: near-black surfaces so the screen does not glare.
private val Dark = darkColorScheme(
    primary = Color(0xFF81E0C2),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF17493D),
    onPrimaryContainer = Color(0xFFC2F3E4),
    secondary = Color(0xFFFFB36B),
    onSecondary = Color(0xFF3A1C00),
    secondaryContainer = Color(0xFF4A2A0C),
    onSecondaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF0D1512),
    onBackground = Color(0xFFE7F3EE),
    surface = Color(0xFF101916),
    onSurface = Color(0xFFE7F3EE),
    surfaceVariant = Color(0xFF1A2924),
    onSurfaceVariant = Color(0xFFB3CAC1),
    surfaceContainerLowest = Color(0xFF0B1511),
    surfaceContainerLow = Color(0xFF15211C),
    surfaceContainer = Color(0xFF1A2924),
    surfaceContainerHigh = Color(0xFF22352D),
    surfaceContainerHighest = Color(0xFF2B4137),
    outline = Color(0xFF789489),
    outlineVariant = Color(0xFF344A40),
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
