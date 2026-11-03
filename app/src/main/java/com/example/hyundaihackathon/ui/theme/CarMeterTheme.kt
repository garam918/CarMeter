package com.example.hyundaihackathon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Colors = darkColorScheme(
    primary = Color(0xFF7CC4FF),
    onPrimary = Color(0xFF00213A),
    secondary = Color(0xFFFFB86B),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF1C232A),
    onSurface = Color(0xFFE6EAEE),
    onSurfaceVariant = Color(0xFFB2BCC6),
    error = Color(0xFFFF8A80),
)

/** Larger than phone defaults: the screen is read at arm's length in a car. */
private val CarTypography = Typography(
    displayLarge = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.Bold, lineHeight = 80.sp),
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, lineHeight = 42.sp),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp),
    bodyLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 21.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
)

@Composable
fun CarMeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = CarTypography, content = content)
}
