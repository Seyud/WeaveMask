package io.github.seyud.weave.ui.about.blend

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode

object ColorBlendToken {
    val Pured_Regular_Light = listOf(
        BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
    )

    val Overlay_Extra_Thin_Dark = listOf(
        BlendColorEntry(Color(0x757A7A7A), BlurBlendMode.Luminosity),
    )
}
