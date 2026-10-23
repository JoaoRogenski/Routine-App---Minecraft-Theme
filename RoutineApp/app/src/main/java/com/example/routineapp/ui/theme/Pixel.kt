package com.example.routineapp.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routineapp.ui.withClick
import kotlin.random.Random

/**
 * Bevelled box in the style of block-game GUIs: optional 1-pixel black outline with the corner
 * pixels cut, light top/left edge and dark bottom/right edge. [px] is the size of one "pixel".
 */
fun Modifier.bevel(fill: Color, hi: Color, lo: Color, outline: Color? = Color.Black, px: Dp = 2.dp) = drawBehind {
    val p = px.toPx(); val w = size.width; val h = size.height
    val o = if (outline != null) p else 0f
    if (outline != null) {
        drawRect(outline, Offset(p, 0f), Size(w - 2 * p, p)); drawRect(outline, Offset(p, h - p), Size(w - 2 * p, p))
        drawRect(outline, Offset(0f, p), Size(p, h - 2 * p)); drawRect(outline, Offset(w - p, p), Size(p, h - 2 * p))
    }
    drawRect(fill, Offset(o, o), Size(w - 2 * o, h - 2 * o))
    drawRect(hi, Offset(o, o), Size(w - 2 * o - p, p)); drawRect(hi, Offset(o, o), Size(p, h - 2 * o - p))
    drawRect(lo, Offset(o + p, h - o - p), Size(w - 2 * o - p, p)); drawRect(lo, Offset(w - o - p, o + p), Size(p, h - 2 * o - p))
}

/** Raised inventory-style panel in the theme's surface colour. */
@Composable
fun Modifier.panel(): Modifier {
    val s = MaterialTheme.colorScheme.surface
    return bevel(s, lerp(s, Color.White, 0.7f), lerp(s, Color.Black, 0.55f), px = 3.dp)
}

/** Sunken inventory slot (dark edge on top/left). */
@Composable
fun Modifier.slot(fill: Color = MaterialTheme.colorScheme.surfaceVariant): Modifier =
    bevel(fill, lerp(fill, Color.Black, 0.6f), lerp(fill, Color.White, 0.75f), outline = null)

@Composable
fun McPanel(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.panel().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

/** Stone (or grass, when [primary]) button with white shadowed label; pressed state flips the bevel. */
@Composable
fun McButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (face, hi, lo) = when {
        !enabled -> Triple(Color(0xFF5A5A5A), Color(0xFF6A6A6A), Color(0xFF3A3A3A))
        primary -> Triple(Mc.GrassFace, Mc.GrassHi, Mc.GrassLo)
        else -> Triple(Mc.StoneFace, Mc.StoneHi, Mc.StoneLo)
    }
    Row(
        modifier.minimumInteractiveComponentSize().defaultMinSize(minHeight = 40.dp)
            .bevel(face, if (pressed) lo else hi, if (pressed) hi else lo)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = withClick(onClick))
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = if (enabled) ShadowText.copy(color = if (pressed) Mc.ChatYellow else Color.White)
            else ShadowText.copy(color = Color(0xFFA0A0A0), shadow = null)
        CompositionLocalProvider(LocalContentColor provides label.color,
            LocalTextStyle provides MaterialTheme.typography.labelLarge.merge(label)) { content() }
    }
}

/** Tiled, darkened dirt texture generated in code (16×16 texels, nearest-neighbour scaled). */
@Composable
fun Modifier.dirtBackground(dark: Boolean): Modifier {
    val texel = with(LocalDensity.current) { 4.dp.roundToPx() }
    val brush = remember(texel) {
        val shades = intArrayOf(0xFF866043.toInt(), 0xFF79553A.toInt(), 0xFF6C4C33.toInt(), 0xFF593D29.toInt(), 0xFF9A6E4C.toInt())
        val rnd = Random(7)
        val tile = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (x in 0 until 16) for (y in 0 until 16) {
            val r = rnd.nextInt(100)
            tile.setPixel(x, y, shades[when { r < 35 -> 0; r < 65 -> 1; r < 85 -> 2; r < 95 -> 3; else -> 4 }])
        }
        val big = Bitmap.createScaledBitmap(tile, 16 * texel, 16 * texel, false)
        ShaderBrush(ImageShader(big.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    val dim = if (dark) 0.78f else 0.62f
    return drawBehind { drawRect(brush); drawRect(Color.Black.copy(alpha = dim)) }
}

/** Draws [art] (rows of palette keys, '.' = transparent) as crisp square pixels. */
@Composable
fun PixelIcon(art: List<String>, palette: Map<Char, Color>, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val cell = this.size.width / art.maxOf { it.length }
        art.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                palette[c]?.let { drawRect(it, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f)) }
            }
        }
    }
}

/** Experience bar: green level number over a segmented bar. */
@Composable
fun XpBar(level: Int, progress: Float, caption: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$level", style = MaterialTheme.typography.titleMedium.merge(ShadowText).copy(color = Mc.XpGreen, fontSize = 18.sp))
        Box(Modifier.fillMaxWidth().height(10.dp).drawBehind {
            val p = 2.dp.toPx()
            drawRect(Color.Black)
            drawRect(Mc.XpTrack, Offset(p, p), Size(size.width - 2 * p, size.height - 2 * p))
            val fillW = (size.width - 2 * p) * progress.coerceIn(0f, 1f)
            drawRect(Mc.XpGreen, Offset(p, p), Size(fillW, size.height - 2 * p))
            drawRect(Color(0xFFC8FF8A), Offset(p, p), Size(fillW, p)) // top highlight
            val seg = (size.width - 2 * p) / 18
            for (i in 1 until 18) drawRect(Color.Black.copy(alpha = 0.55f), Offset(p + i * seg, p), Size(p / 2, size.height - 2 * p))
        })
        Text(caption, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium.merge(ShadowText),
            textAlign = TextAlign.Center)
    }
}

/** Original 12×12 item-style icons for the hotbar. */
object PixelArt {
    private val K = Color(0xFF1A1A1A)

    val clock = listOf(
        "....kkkk....",
        "..kkGGGGkk..",
        ".kGgBBBBgGk.",
        ".kGBBYYBBGk.",
        "kGBBBYYBBBGk",
        "kGBBBBBBBBGk",
        "kGddddddddGk",
        "kGddDddDddGk",
        ".kGddddddGk.",
        ".kGgddddgGk.",
        "..kkGGGGkk..",
        "....kkkk....",
    )
    val clockColors = mapOf('k' to K, 'G' to Color(0xFFFCDB05), 'g' to Color(0xFFC79A00), 'B' to Color(0xFF6CA8FF),
        'Y' to Color(0xFFFFF59A), 'd' to Color(0xFF7A5230), 'D' to Color(0xFF5B3A20))

    val book = listOf(
        "..kkkkkkkkk.",
        ".kbbbbbbbbWk",
        "kbBBBBBBBbWk",
        "kbBggggBBbWk",
        "kbBBBBBBBbWk",
        "kbbbbbbbbbWk",
        "kbbbbbbbbbWk",
        "kbbbbbbbbbWk",
        "kbbbbbbbbbWk",
        "kbbbbbbbbbWk",
        "kkkkkkkkkkWk",
        ".kWWWWWWWWk.",
    )
    val bookColors = mapOf('k' to K, 'b' to Color(0xFF8B5A2B), 'B' to Color(0xFF6B3F1D), 'g' to Color(0xFFFCDB05),
        'W' to Color(0xFFF0EAD6))

    val pickaxe = listOf(
        "..cccccccc..",
        ".cDDDDDDDDc.",
        "cDdccccccdDc",
        "cDc..kk..cDc",
        "cc...kwk..cc",
        ".....kwk....",
        ".....kwk....",
        ".....kwk....",
        ".....kwk....",
        ".....kwk....",
        ".....kwk....",
        "......k.....",
    )
    val pickaxeColors = mapOf('c' to Color(0xFF14524B), 'D' to Color(0xFF4AEDD9), 'd' to Color(0xFF2CB3A3),
        'k' to Color(0xFF3B2611), 'w' to Color(0xFF9C6B36))
}
