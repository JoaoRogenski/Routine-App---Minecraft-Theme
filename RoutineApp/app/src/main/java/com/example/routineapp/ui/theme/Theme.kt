package com.example.routineapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Light = stone GUI panels ("dia"), dark = deepslate panels ("noite"); both sit on the dirt background.
private val DarkColorScheme = darkColorScheme(
    primary = Mc.XpGreen,
    onPrimary = Color(0xFF0E2600),
    primaryContainer = Mc.GrassFace,
    onPrimaryContainer = Color.White,
    secondary = Mc.StoneHi,
    onSecondary = Color(0xFF1B1B1B),
    secondaryContainer = Mc.StoneFace,
    onSecondaryContainer = Color.White,
    tertiary = Mc.XpGreen,
    onTertiary = Color(0xFF0E2600),
    error = Mc.ChatRed,
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF5A1410),
    onErrorContainer = Color(0xFFFFD6D2),
    background = Color(0xFF1B1410),
    onBackground = Color.White,
    surface = Mc.Deepslate,
    onSurface = Mc.DeepslateText,
    surfaceVariant = Mc.SlotDark,
    onSurfaceVariant = Color(0xFFBDBDC4),
    surfaceContainerLowest = Color(0xFF26262B),
    surfaceContainerLow = Mc.Deepslate,
    surfaceContainer = Mc.Deepslate,
    surfaceContainerHigh = Color(0xFF45454C),
    surfaceContainerHighest = Color(0xFF505058),
    inverseSurface = Color(0xFF101010),
    inverseOnSurface = Color.White,
    outline = Color(0xFF8A8A92),
    outlineVariant = Color(0xFF55555C),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2A5E1B), // 4.6:1 on the stone panel, so green text links stay readable
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7E28F),
    onPrimaryContainer = Color(0xFF10300A),
    secondary = Mc.StoneLo,
    onSecondary = Color.White,
    secondaryContainer = Mc.Slot,
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Mc.GrassFace,
    onTertiary = Color.White,
    error = Mc.Redstone,
    onError = Color.White,
    errorContainer = Color(0xFFFFC9C2),
    onErrorContainer = Color(0xFF4A0905),
    background = Color(0xFF1B1410),
    onBackground = Color.White,
    surface = Mc.Panel,
    onSurface = Mc.PanelText,
    surfaceVariant = Mc.Slot,
    onSurfaceVariant = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color(0xFFD6D6D6),
    surfaceContainerLow = Mc.Panel,
    surfaceContainer = Mc.Panel,
    surfaceContainerHigh = Mc.Panel,
    surfaceContainerHighest = Color(0xFFB4B4B4),
    inverseSurface = Color(0xFF101010),
    inverseOnSurface = Color.White,
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF9A9A9A),
)

// Pixel look: no rounding anywhere.
private val square = RoundedCornerShape(0.dp)
private val AppShapes = Shapes(extraSmall = square, small = square, medium = square, large = square, extraLarge = square)

@Composable
fun RoutineAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
