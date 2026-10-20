package io.github.aziztw.sacuma.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.aziztw.sacuma.R

/** Indigo GameCube palette (see the approved mockup). */
object Gc {
    val Background = Color(0xFF231E4A)  // deep indigo screen
    val Card = Color(0xFF322A6B)        // port cards
    val Well = Color(0xFF1A1638)        // recessed: gates, tracks, segmented bg
    val Accent = Color(0xFF5A4FB0)      // logo tile, selected segment, outlines
    val AccentSoft = Color(0xFF8D83E0)  // slider fill
    val ActiveOutline = Color(0xFF7D72D6)
    val EmptyBorder = Color(0xFF3E3680)
    val EmptyNumber = Color(0xFF5C53A3)
    val EmptyLabel = Color(0xFF6E66B5)

    val Text = Color(0xFFF1EEFF)
    val TextMuted = Color(0xFFA79EDB)
    val StickDot = Color(0xFFE9E5FF)
    val TriggerFill = Color(0xFFCFC8F2)

    // Controller accents, used only where they mean something.
    val AOn = Color(0xFF3DBE8B)
    val AOff = Color(0xFF1F4B45)
    val BOn = Color(0xFFE5484D)
    val BOff = Color(0xFF5B2A3A)
    val XyOn = Color(0xFFD8D4EA)
    val XyOff = Color(0xFF4A4566)
    val CStick = Color(0xFFF2C230)

    val GoodText = Color(0xFF9FE6C6)
    val GoodBg = Color(0xFF1F4B45)
}

/** Rubik (OFL, bundled as a variable font in res/font/rubik.ttf). */
@OptIn(ExperimentalTextApi::class)
val Rubik = FontFamily(
    Font(R.font.rubik, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.rubik, FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)
