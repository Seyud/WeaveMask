package com.topjohnwu.magisk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalEnableBlur = staticCompositionLocalOf { true }
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { true }

/**
 * WeaveMagisk 主题包装函数
 * 根据 colorMode 和 keyColor 创建 ThemeController 并应用 MiuixTheme
 *
 * colorMode 取值:
 * 0 = 跟随系统
 * 1 = 亮色
 * 2 = 暗色
 * 3 = Monet 跟随系统
 * 4 = Monet 亮色
 * 5 = Monet 暗色
 *
 * @param colorMode 颜色模式，0-5
 * @param keyColor Monet 种子色，null 表示使用系统壁纸色
 * @param content 子组件
 */
@Composable
fun WeaveMagiskTheme(
    colorMode: Int = 0,
    keyColor: Color? = null,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val controller = when (colorMode) {
        1 -> ThemeController(ColorSchemeMode.Light)
        2 -> ThemeController(ColorSchemeMode.Dark)
        3 -> ThemeController(
            ColorSchemeMode.MonetSystem,
            keyColor = keyColor,
            isDark = isDark
        )
        4 -> ThemeController(
            ColorSchemeMode.MonetLight,
            keyColor = keyColor,
        )
        5 -> ThemeController(
            ColorSchemeMode.MonetDark,
            keyColor = keyColor,
        )
        else -> ThemeController(ColorSchemeMode.System)
    }
    MiuixTheme(
        controller = controller,
        content = content
    )
}
