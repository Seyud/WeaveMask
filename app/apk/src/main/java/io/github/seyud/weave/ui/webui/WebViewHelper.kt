package io.github.seyud.weave.ui.webui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.topjohnwu.superuser.nio.FileSystemManager
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.model.module.LocalModule
import io.github.seyud.weave.core.utils.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

private const val WEBUI_DOMAIN = "mui.kernelsu.org"
private const val WEBUI_HOME_PAGE = "https://$WEBUI_DOMAIN/index.html"
private const val KSU_SCHEME = "ksu"
private const val ICON_HOST = "icon"
private const val INTERNAL_BLOB_DOWNLOAD_HOST = "blob-download.kernelsu.internal"

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
    dirName: String,
    webUIState: WebUIState,
) {
    when (val result = prepareWebViewEnvironment(activity, dirName)) {
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
    dirName: String,
): WebViewPreparationResult = withContext(Dispatchers.IO) {
    val moduleDir = "${Const.MODULE_PATH}/$dirName"
    val moduleName = activity.intent.getStringExtra(WebUIActivity.EXTRA_NAME) ?: dirName

    if (!RootUtils.ensureServiceConnected()) {
        return@withContext WebViewPreparationResult.Failed("Failed to connect to root file service")
    }

    val fs = RootUtils.fs
    if (!fs.getFile("$moduleDir/webroot").isDirectory) {
        return@withContext WebViewPreparationResult.Failed("WebUI assets not found for module $dirName")
    }

    // Same availability rules as KernelSU manager: no WebUI for missing, disabled or
    // pending-update / pending-removal modules.
    // 按目录名找模块：目录名与 module.prop 的 id 可能不同，id 还可能重复
    val module = runCatching { LocalModule.installed().firstOrNull { it.base.name == dirName } }.getOrNull()
    when {
        module == null ->
            return@withContext WebViewPreparationResult.Failed("No such module: $dirName")

        !module.hasWebUi ->
            return@withContext WebViewPreparationResult.Failed("Module has no WebUI: ${module.name}")

        !module.enable || module.updated || module.remove ->
            return@withContext WebViewPreparationResult.Failed("Module unavailable: ${module.name}")
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

    // Remote DevTools (chrome://inspect) for module WebUIs, same switch as KernelSU.
    WebView.setWebContentsDebuggingEnabled(Config.enableWebUiDebugging)

    val webView = WebView(activity)
    webView.setBackgroundColor(Color.TRANSPARENT)

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
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
            BlobDownloadHandler.shouldInterceptRequest(request)?.let { return it }

            val url = request.url
            if (url.scheme.equals(KSU_SCHEME, ignoreCase = true) &&
                url.host.equals(ICON_HOST, ignoreCase = true)
            ) {
                val packageName = url.path?.removePrefix("/")
                if (!packageName.isNullOrEmpty()) {
                    val icon = WebUiPackageRegistry.loadAppIcon(activity, packageName, 512)
                    if (icon != null) {
                        val stream = ByteArrayOutputStream()
                        icon.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        return WebResourceResponse(
                            "image/png",
                            null,
                            200,
                            "OK",
                            mapOf("Access-Control-Allow-Origin" to "*"),
                            ByteArrayInputStream(stream.toByteArray())
                        )
                    }
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        404,
                        "Not Found",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        ByteArrayInputStream("No such package".toByteArray(Charsets.UTF_8))
                    )
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
            view?.let { currentView ->
                // The shim is lost on every navigation; re-inject it, and the on-device
                // console as well when debugging is enabled.
                WebUiDebug.injectDownloadShim(activity, currentView)
                if (Config.enableWebUiConsole) {
                    WebUiDebug.injectConsole(activity, currentView)
                }
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

    webUIState.webView = webView

    val webviewInterface = WebViewInterface(webUIState)
    val downloadInterface = WebUIDownloadInterface(webUIState)
    webUIState.webViewInterface = webviewInterface
    webUIState.downloadInterface = downloadInterface
    webView.addJavascriptInterface(webviewInterface, "ksu")
    webView.addJavascriptInterface(downloadInterface, "ksu_download")

    webView.setDownloadListener { url, _, _, _, _ ->
        // Chunked blob downloads are already handed to Downloads by
        // `ksu_download.completeChunkedDownload`, so only real links go out to the browser.
        if (Uri.parse(url).host != INTERNAL_BLOB_DOWNLOAD_HOST) {
            downloadInterface.openExternal(url)
        }
    }

    WebUiDebug.injectDownloadShim(activity, webView)
    if (Config.enableWebUiConsole) {
        WebUiDebug.injectConsole(activity, webView)
    }

    webView.loadUrl(WEBUI_HOME_PAGE)
    webUIState.uiEvent = WebUIEvent.WebViewReady
}
