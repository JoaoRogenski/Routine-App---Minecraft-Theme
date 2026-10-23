package com.example.routineapp.ui.theme

import androidx.compose.ui.graphics.Color

// Block-game palette: stone GUI greys, grass/XP greens, redstone, gold. Original values, no game assets.
object Mc {
    val Panel = Color(0xFFC6C6C6)
    val PanelText = Color(0xFF3F3F3F)
    val Deepslate = Color(0xFF3B3B41)
    val DeepslateText = Color(0xFFE6E6E6)
    val Slot = Color(0xFF8B8B8B)
    val SlotDark = Color(0xFF2A2A2E)

    val StoneFace = Color(0xFF727272)
    val StoneHi = Color(0xFFA6A6A6)
    val StoneLo = Color(0xFF474747)
    val GrassFace = Color(0xFF3C8527)
    val GrassHi = Color(0xFF63B046)
    val GrassLo = Color(0xFF1F4D12)

    val XpGreen = Color(0xFF80FF20)
    val XpTrack = Color(0xFF1B1B1B)
    val Redstone = Color(0xFFA12722)
    val Gold = Color(0xFFFFDD33)

    // chat colour codes
    val ChatYellow = Color(0xFFFFFF55)
    val ChatGreen = Color(0xFF55FF55)
    val ChatRed = Color(0xFFFF5555)
    val ChatGray = Color(0xFFAAAAAA)
    val TextShadow = Color(0xFF3F3F3F)
}

/** Wool-like category colours (block, text on it); readable in both themes. */
val CategoryPalette = listOf(
    Color(0xFF70B919) to Color(0xFF10210A), // lime
    Color(0xFF3AAFD9) to Color(0xFF0A1F2A), // light blue
    Color(0xFFF8C627) to Color(0xFF2A1F00), // yellow
    Color(0xFFF07613) to Color(0xFF2A1200), // orange
    Color(0xFFBD44B3) to Color(0xFF000000), // magenta
    Color(0xFF117078) to Color(0xFFFFFFFF), // cyan
)
