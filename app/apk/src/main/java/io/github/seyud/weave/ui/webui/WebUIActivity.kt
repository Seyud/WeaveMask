package io.github.seyud.weave.ui.webui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.seyud.weave.R
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.ui.theme.WeaveMagiskTheme
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator

@SuppressLint("SetJavaScriptEnabled")
class WebUIActivity : ComponentActivity() {

    companion object {
        /** 模块目录名：WebUI 资源一律按目录名定位（与 action 一致，见上游 d582d8145）。 */
        const val EXTRA_DIR = "dir"

        /** 旧快捷方式/旧版本只带 id，读取时兜底用。 */
        const val EXTRA_MODULE_ID = "id"
        const val EXTRA_NAME = "name"
    }
    fun enableEdgeToEdge(enable: Boolean) {
        runOnUiThread {
            if (enable) {
                (this as ComponentActivity).enableEdgeToEdge()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(resolveWebUiTheme(Config.colorMode))
        super.onCreate(savedInstanceState)

        setContent {
            val colorMode = Config.colorMode
            val keyColorInt = Config.keyColor
            val keyColor = if (keyColorInt == 0) null else Color(keyColorInt)

            WeaveMagiskTheme(
                colorMode = colorMode,
                keyColor = keyColor,
            ) {
                MonetColorsProvider.UpdateCss()
                MainContent(activity = this, onFinish = { finish() })
            }
        }
    }

    private fun resolveWebUiTheme(colorMode: Int): Int = when (colorMode) {
        1, 4 -> R.style.Theme_WeaveMagisk_WebUI_Light
        2, 5 -> R.style.Theme_WeaveMagisk_WebUI_Dark
        else -> R.style.Theme_WeaveMagisk_WebUI_System
    }
}

@Composable
private fun MainContent(activity: ComponentActivity, onFinish: () -> Unit) {
    // 目录名优先；旧的 intent 只有 "id" 时兜底（历史上两者当作同一个东西用）
    val moduleDir = remember {
        activity.intent.getStringExtra(WebUIActivity.EXTRA_DIR)
            ?: activity.intent.getStringExtra(WebUIActivity.EXTRA_MODULE_ID)
    }
    val webUIState = remember { WebUIState() }

    LaunchedEffect(Unit) {
        if (!webUIState.isInsetsEnabled) {
            webUIState.isInsetsEnabled = true
            (activity as? WebUIActivity)?.enableEdgeToEdge(true)
        }
    }

    LaunchedEffect(moduleDir) {
        if (moduleDir == null) {
            onFinish()
            return@LaunchedEffect
        }
        prepareWebView(activity, moduleDir, webUIState)
    }

    DisposableEffect(Unit) {
        onDispose { webUIState.dispose() }
    }

    when (val event = webUIState.uiEvent) {
        is WebUIEvent.Error -> {
            LaunchedEffect(event) {
                Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
                onFinish()
            }
        }
        is WebUIEvent.Close -> {
            LaunchedEffect(event) { onFinish() }
        }
        else -> {}
    }

    val isLoading = webUIState.uiEvent is WebUIEvent.Loading

    Crossfade(targetState = isLoading, animationSpec = tween(300)) { loading ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                InfiniteProgressIndicator()
            }
        } else {
            WebUIScreen(webUIState = webUIState)
        }
    }
}
