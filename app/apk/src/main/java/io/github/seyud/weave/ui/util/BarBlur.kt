package io.github.seyud.weave.ui.util

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

private const val DefaultBarBlurRadius = 25f
private const val DefaultBarBlurTintAlpha = 0.8f

@Composable
fun rememberBarBlurBackdrop(
    enabled: Boolean,
    surfaceColor: Color,
): LayerBackdrop? {
    if (!enabled || !isRuntimeShaderSupported()) return null
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    return backdrop
}

fun Modifier.attachBarBlurBackdrop(backdrop: LayerBackdrop?): Modifier = then(
    if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
)

@Composable
fun Modifier.defaultBarBlur(
    backdrop: LayerBackdrop?,
    surfaceColor: Color,
    shape: Shape = RectangleShape,
): Modifier = then(
    if (backdrop != null) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = DefaultBarBlurRadius,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(surfaceColor.copy(alpha = DefaultBarBlurTintAlpha)),
                ),
            ),
        )
    } else {
        Modifier.background(surfaceColor, shape)
    },
)

fun barBlurContainerColor(
    backdrop: LayerBackdrop?,
    surfaceColor: Color,
): Color = if (backdrop != null) Color.Transparent else surfaceColor
