package io.github.seyud.weave.ui.webui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.topjohnwu.superuser.nio.FileSystemManager
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.utils.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val WEBUI_DOMAIN = "mui.kernelsu.org"
private const val WEBUI_HOME_PAGE = "https://$WEBUI_DOMAIN/index.html"

private sealed interface WebViewPreparationResult {
    data class Ready(
        val moduleDir: String,
        val moduleName: String,
        val fs: FileSystemManager,
    ) : WebViewPreparationResult

    data class Failed(val message: String) : WebViewPreparationResult
}

@SuppressLint("SetJavaScriptEnabled")
internal suspend fun prepareWebView(
    activity: Activity,
    moduleId: String,
    webUIState: WebUIState,
) {
    when (val result = prepareWebViewEnvironment(activity, moduleId)) {
        is WebViewPreparationResult.Failed -> {
            webUIState.uiEvent = WebUIEvent.Error(result.message)
        }

        is WebViewPreparationResult.Ready -> {
            initWebView(
                activity = activity,
                fs = result.fs,
                moduleDir = result.moduleDir,
                moduleName = result.moduleName,
                webUIState = webUIState,
            )
        }
    }
}

private suspend fun prepareWebViewEnvironment(
    activity: Activity,
    moduleId: String,
): WebViewPreparationResult = withContext(Dispatchers.IO) {
    val moduleDir = "${Const.MODULE_PATH}/$moduleId"
    val moduleName = activity.intent.getStringExtra("name") ?: moduleId

    if (!RootUtils.ensureServiceConnected()) {
        return@withContext WebViewPreparationResult.Failed("Failed to connect to root file service")
    }

    val fs = RootUtils.fs
    if (!fs.getFile("$moduleDir/webroot").isDirectory) {
        return@withContext WebViewPreparationResult.Failed("WebUI assets not found for module $moduleId")
    }

    WebViewPreparationResult.Ready(
        moduleDir = moduleDir,
        moduleName = moduleName,
        fs = fs,
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun initWebView(
    activity: Activity,
    fs: FileSystemManager,
    moduleDir: String,
    moduleName: String,
    webUIState: WebUIState,
) {
    webUIState.modDir = moduleDir
    webUIState.moduleName = moduleName

    val webView = WebView(activity)
    webView.setBackgroundColor(Color.TRANSPARENT)

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
    }

    val webRoot = File("$moduleDir/webroot")
    val webViewAssetLoader = WebViewAssetLoader.Builder()
        .setDomain(WEBUI_DOMAIN)
        .addPathHandler(
            "/",
            SuFilePathHandler(
                activity,
                webRoot,
                fs,
                { webUIState.currentInsets },
                { enable ->
                    webUIState.isInsetsEnabled = enable
                    (activity as? WebUIActivity)?.enableEdgeToEdge(enable)
                }
            )
        )
        .build()

    webView.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url
            if (url.scheme.equals("ksu", ignoreCase = true) && url.host.equals("icon", ignoreCase = true)) {
                val packageName = url.path?.removePrefix("/")
                if (!packageName.isNullOrEmpty()) {
                    val icon = WebUiPackageRegistry.loadAppIcon(activity, packageName, 512)
                    if (icon != null) {
                        val stream = java.io.ByteArrayOutputStream()
                        icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                        return WebResourceResponse(
                            "image/png",
                            null,
                            java.io.ByteArrayInputStream(stream.toByteArray())
                        )
                    }
                }
            }
            return webViewAssetLoader.shouldInterceptRequest(url)
        }

        override fun doUpdateVisitedHistory(
            view: WebView?,
            url: String?,
            isReload: Boolean
        ) {
            webUIState.webCanGoBack = view?.canGoBack() ?: false
            if (webUIState.isInsetsEnabled) {
                webUIState.webView?.evaluateJavascript(
                    webUIState.currentInsets.js,
                    null
                )
            }
            super.doUpdateVisitedHistory(view, url, isReload)
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            if (message == null || result == null) return false
            webUIState.uiEvent = WebUIEvent.ShowAlert(message, result)
            return true
        }

        override fun onJsConfirm(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            if (message == null || result == null) return false
            webUIState.uiEvent = WebUIEvent.ShowConfirm(message, result)
            return true
        }

        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?
        ): Boolean {
            if (message == null || result == null || defaultValue == null) return false
            webUIState.uiEvent = WebUIEvent.ShowPrompt(message, defaultValue, result)
            return true
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            webUIState.filePathCallback?.onReceiveValue(null)
            webUIState.filePathCallback = filePathCallback
            val intent = fileChooserParams?.createIntent()
                ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            webUIState.uiEvent = WebUIEvent.ShowFileChooser(intent)
            return true
        }
    }

    val webviewInterface = WebViewInterface(webUIState)
    webUIState.webView = webView
    webView.addJavascriptInterface(webviewInterface, "ksu")
    webView.loadUrl(WEBUI_HOME_PAGE)
    webUIState.uiEvent = WebUIEvent.WebViewReady
}
