package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CherryRed = Color(0xFFF91B42)
val CherryRedContainer = Color(0xFFFFE5EA)
val CoralPink = Color(0xFFFF5C7C)
val CrispWhite = Color(0xFFFFFFFF)
val DeepInk = Color(0xFF1E1B2E)
val MintGreen = Color(0xFF1DB96E)
val MintGreenContainer = Color(0xFFE3F8EE)
val MutedInk = Color(0xFF67637A)
val SkyBlue = Color(0xFF2B95FF)
val SkyBlueContainer = Color(0xFFE4F2FF)
val SoftSurfaceVariant = Color(0xFFF3EFF8)
val SunshineYellow = Color(0xFFFFC933)
val SunshineYellowContainer = Color(0xFFFFF5D1)
val TheaterBlack = Color(0xFF0A0A0E)
val TheaterSurface = Color(0xFF1B1926)
val WarmCream = Color(0xFFFFFBF4)

private val MinikColorScheme = lightColorScheme(
    primary = CherryRed,
    onPrimary = CrispWhite,
    primaryContainer = CherryRedContainer,
    onPrimaryContainer = DeepInk,
    secondary = SkyBlue,
    onSecondary = CrispWhite,
    secondaryContainer = SkyBlueContainer,
    onSecondaryContainer = DeepInk,
    tertiary = MintGreen,
    onTertiary = CrispWhite,
    tertiaryContainer = MintGreenContainer,
    onTertiaryContainer = DeepInk,
    background = WarmCream,
    onBackground = DeepInk,
    surface = CrispWhite,
    onSurface = DeepInk,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = MutedInk
)

private val MinikTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val MinikShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun MinikTVTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MinikColorScheme,
        typography = MinikTypography,
        shapes = MinikShapes,
        content = content
    )
}
