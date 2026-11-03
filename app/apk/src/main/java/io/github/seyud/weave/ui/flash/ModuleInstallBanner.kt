package io.github.seyud.weave.ui.flash

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 模块安装界面的 WeaveMask 字符画。
 *
 * 之前这五行是直接 `echo` 进 PTY 终端的：终端被固定成 256 列，而手机上只能看到 50 列左右，
 * 字符画会被截断（右侧缺一截），所以改成由 UI 按可用宽度等比缩放绘制，保证整幅都露出来。
 * 保存日志时仍把它补回文本最前面（见 [FlashViewModel.saveLogForCompose]）。
 */
internal val moduleInstallBannerLines: List<String> = """
    __        __                    __  __           _
    \ \      / /__  __ ___   _____ |  \/  | __ _ ___| | __
     \ \ /\ / / _ \/ _` \ \ / / _ \| |\/| |/ _` / __| |/ /
      \ V  V /  __/ (_| |\ V /  __/| |  | | (_| \__ \   <
       \_/\_/ \___|\__,_| \_/ \___||_|  |_|\__,_|___/_|\_\
""".trimIndent().lines()

/**
 * 把字符画按可用宽度缩放到刚好铺满一行，任何屏幕宽度下都不会被裁掉。
 */
@Composable
internal fun ModuleInstallBanner(modifier: Modifier = Modifier) {
    val art = moduleInstallBannerLines.joinToString("\n")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val referenceSize = 16.sp
    val referenceWidth = measurer.measure(
        text = AnnotatedString(art),
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = referenceSize),
    ).size.width

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = constraints.maxWidth.toFloat()
        val scale = if (referenceWidth > 0) available / referenceWidth else 1f
        // 留 2% 余量，避免不同字体度量的细微差异把最后一个字符挤出去
        val fontSize = with(density) { (referenceSize.toPx() * scale * 0.98f).toSp() }
        Text(
            text = art,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            lineHeight = fontSize * 1.05f,
            color = MiuixTheme.colorScheme.onSurface,
            softWrap = false,
            maxLines = moduleInstallBannerLines.size,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
