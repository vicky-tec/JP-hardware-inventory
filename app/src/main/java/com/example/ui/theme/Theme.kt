package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DukaanLightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = ForestGreenContainer,
    onPrimaryContainer = OnForestGreenContainer,
    secondary = EmeraldAccent,
    onSecondary = Color.White,
    secondaryContainer = WarmStoneSurface,
    onSecondaryContainer = CharcoalInk,
    tertiary = LowStockAmber,
    onTertiary = Color.White,
    tertiaryContainer = LowStockAmberBg,
    onTertiaryContainer = Color(0xFF78350F),
    background = OffWhiteBackground,
    onBackground = CharcoalInk,
    surface = PureWhiteSurface,
    onSurface = CharcoalInk,
    surfaceVariant = WarmStoneSurface,
    onSurfaceVariant = SlateSecondary,
    outline = BorderStone,
    outlineVariant = Color(0xFFE5E2D9),
    error = OutOfStockRed,
    onError = Color.White,
    errorContainer = OutOfStockRedBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

val DukaanShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun DukaanStockTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DukaanLightColorScheme,
        typography = DukaanTypography,
        shapes = DukaanShapes,
        content = content
    )
}
