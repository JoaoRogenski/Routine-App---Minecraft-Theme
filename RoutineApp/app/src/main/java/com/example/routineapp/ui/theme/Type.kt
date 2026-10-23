package com.example.routineapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.routineapp.R

// Monocraft (SIL OFL, block-game-style monospace), extracted from art/fonts/Monocraft.ttc as static weights
val PixelFont = FontFamily(
    Font(R.font.monocraft_regular, FontWeight.Normal),
    Font(R.font.monocraft_semibold, FontWeight.Medium),
    Font(R.font.monocraft_semibold, FontWeight.SemiBold),
    Font(R.font.monocraft_bold, FontWeight.Bold),
)

private val base = Typography()

private fun TextStyle.px() = copy(fontFamily = PixelFont, letterSpacing = 0.sp)

val Typography = Typography(
    displayLarge = base.displayLarge.px(), displayMedium = base.displayMedium.px(), displaySmall = base.displaySmall.px(),
    headlineLarge = base.headlineLarge.px().copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.px().copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.px().copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.px().copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.px().copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.px().copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.px(), bodyMedium = base.bodyMedium.px(), bodySmall = base.bodySmall.px().copy(fontSize = 13.sp),
    labelLarge = base.labelLarge.px().copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.px(), labelSmall = base.labelSmall.px(),
)

/** White text with the hard 1-pixel drop shadow of block-game text; for text drawn on the dirt background. */
val ShadowText = TextStyle(color = Color.White, shadow = Shadow(Mc.TextShadow, Offset(4f, 4f), 0f))
