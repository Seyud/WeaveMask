package io.github.seyud.weave.ui

import android.os.Build
import io.github.seyud.weave.R

internal object SplashThemeResolver {

    enum class Kind {
        DEFAULT,
        MONET_SYSTEM,
        MONET_PRESET,
    }

    data class Spec(
        val kind: Kind,
        val dark: Boolean,
        val keyColor: Int,
    )

    private val presetKeyColors = setOf(
        0xFFF44336.toInt(),
        0xFFE91E63.toInt(),
        0xFF9C27B0.toInt(),
        0xFF673AB7.toInt(),
        0xFF3F51B5.toInt(),
        0xFF2196F3.toInt(),
        0xFF00BCD4.toInt(),
        0xFF009688.toInt(),
        0xFF4FAF50.toInt(),
        0xFFFFEB3B.toInt(),
        0xFFFFC107.toInt(),
        0xFFFF9800.toInt(),
        0xFF795548.toInt(),
        0xFF607D8F.toInt(),
        0xFFFF9CA8.toInt(),
    )

    fun resolve(
        colorMode: Int,
        keyColor: Int,
        sdkInt: Int = Build.VERSION.SDK_INT,
        isSystemDark: Boolean,
    ): Spec {
        val dark = when (colorMode) {
            2, 5 -> true
            0, 3 -> isSystemDark
            else -> false
        }

        return when (colorMode) {
            3 -> {
                if (sdkInt >= Build.VERSION_CODES.S) {
                    Spec(kind = Kind.MONET_SYSTEM, dark = dark, keyColor = 0)
                } else {
                    Spec(kind = Kind.DEFAULT, dark = dark, keyColor = 0)
                }
            }

            4, 5 -> {
                if (keyColor in presetKeyColors) {
                    Spec(kind = Kind.MONET_PRESET, dark = dark, keyColor = keyColor)
                } else {
                    Spec(kind = Kind.DEFAULT, dark = dark, keyColor = 0)
                }
            }

            else -> Spec(kind = Kind.DEFAULT, dark = dark, keyColor = 0)
        }
    }

    fun resolveThemeRes(spec: Spec): Int {
        return when (spec.kind) {
            Kind.DEFAULT -> R.style.Theme_WeaveMagisk_Splash_Default
            Kind.MONET_SYSTEM -> R.style.Theme_WeaveMagisk_Splash_MonetSystem

            Kind.MONET_PRESET -> resolvePresetThemeRes(spec.keyColor)
        }
    }

    fun resolveThemeRes(
        colorMode: Int,
        keyColor: Int,
        sdkInt: Int = Build.VERSION.SDK_INT,
        isSystemDark: Boolean,
    ): Int = resolveThemeRes(resolve(colorMode, keyColor, sdkInt, isSystemDark))

    private fun resolvePresetThemeRes(keyColor: Int): Int {
        return when (keyColor) {
            0xFFF44336.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Red
            0xFFE91E63.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Pink
            0xFF9C27B0.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Purple
            0xFF673AB7.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_DeepPurple
            0xFF3F51B5.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Indigo
            0xFF2196F3.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Blue
            0xFF00BCD4.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Cyan
            0xFF009688.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Teal
            0xFF4FAF50.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Green
            0xFFFFEB3B.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Yellow
            0xFFFFC107.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Amber
            0xFFFF9800.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Orange
            0xFF795548.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Brown
            0xFF607D8F.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_BlueGrey
            0xFFFF9CA8.toInt() -> R.style.Theme_WeaveMagisk_Splash_MonetPreset_Sakura
            else -> R.style.Theme_WeaveMagisk_Splash_Default
        }
    }
}
