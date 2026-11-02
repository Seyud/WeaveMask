package io.github.seyud.weave.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope

/**
 * 延迟内容加载工具
 * 返回 true 仅在导航转场动画完成且额外缓冲帧通过之后
 *
 * 时间线：
 * - 动画进行中：返回 false → 页面显示轻量占位符（流畅动画）
 * - 动画结束 + 1 帧：返回 true → 重量级内容组合
 *   （卡顿不可见，因为页面已经静止）
 *
 * 值是粘性的 — 一旦为 true 就永远不会恢复为 false，
 * 所以内容在退出转场期间保持可见。
 */
@Composable
fun rememberContentReady(): Boolean {
    val transitionRunning = LocalNavTransitionScope.current.isRunning
    val ready = remember { mutableStateOf(false) }

    LaunchedEffect(transitionRunning) {
        if (!transitionRunning && !ready.value) {
            withFrameNanos { }
            ready.value = true
        }
    }

    return ready.value
}
