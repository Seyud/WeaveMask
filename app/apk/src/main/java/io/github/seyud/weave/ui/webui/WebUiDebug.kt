package io.github.seyud.weave.ui.webui

import android.content.Context
import android.webkit.WebView
import timber.log.Timber

/**
 * On-device WebUI debugging helpers.
 *
 * The devtools panel is [eruda](https://github.com/liriliri/eruda), injected on every page
 * load. Ported from KsuWebUIStandalone, whose on-device console is the reason a module
 * WebUI can be debugged without a USB connection.
 */
internal object WebUiDebug {
    private const val BOOT_SNIPPET =
        "try { if (window.eruda && !window.eruda_inited) { window.eruda_inited = true; eruda.init(); } } catch (e) { console.error('eruda init failed', e); }"

    @Volatile
    private var erudaSource: String? = null

    /**
     * Inject eruda into [webView] and open its console. No-op when the asset is missing.
     * Safe to call repeatedly: eruda is only initialised once per page.
     */
    fun injectConsole(context: Context, webView: WebView) {
        val source = loadAsset(context, "webview/eruda.min.js") ?: return
        // The panel survives same-document navigation, so only boot it once per page.
        webView.evaluateJavascript("!!(window.eruda && window.eruda_inited)") { alreadyInjected ->
            if (alreadyInjected == "true") return@evaluateJavascript
            webView.evaluateJavascript(source, null)
            webView.evaluateJavascript(BOOT_SNIPPET, null)
        }
    }

    /** Load the JS shim that routes `<a download>` blobs to `ksu_download`. */
    fun injectDownloadShim(context: Context, webView: WebView) {
        loadAsset(context, "webview/download.js")?.let { webView.evaluateJavascript(it, null) }
    }

    private fun loadAsset(context: Context, path: String): String? {
        if (path.endsWith("eruda.min.js")) {
            erudaSource?.let { return it }
        }
        return runCatching {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.onFailure {
            Timber.w(it, "Missing WebUI asset: $path")
        }.getOrNull()?.also { source ->
            if (path.endsWith("eruda.min.js")) erudaSource = source
        }
    }
}
