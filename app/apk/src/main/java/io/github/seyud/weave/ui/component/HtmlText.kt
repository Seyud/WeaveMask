package io.github.seyud.weave.ui.component

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.github.seyud.weave.core.registry.RegistryUrls
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.ByteArrayInputStream

/**
 * Renders a trusted HTML fragment in a locked-down WebView.
 *
 * @param restrictOrigin when non-null, sub-resources may only be loaded from this origin and
 *   links never navigate inside the WebView: in-page anchors scroll, every other link is handed
 *   to [onOpenExternal] (or dropped), so registry content can never pull in remote content or
 *   steer the WebView somewhere else.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlText(
    content: String,
    modifier: Modifier = Modifier,
    baseUrl: String? = null,
    restrictOrigin: String? = null,
    onOpenExternal: ((String) -> Unit)? = null,
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val textColor = "#%06X".format(0xFFFFFF and MiuixTheme.colorScheme.onBackground.toArgb())
    val linkColor = "#%06X".format(0xFFFFFF and MiuixTheme.colorScheme.primary.toArgb())
    val html = """
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    background: transparent;
                }
                body {
                    color: $textColor;
                    font-size: 14px;
                    line-height: 1.6;
                    word-break: break-word;
                }
                a { color: $linkColor; }
                img { max-width: 100%; height: auto; }
                pre, code {
                    white-space: pre-wrap;
                    word-break: break-word;
                    background: transparent;
                }
                pre { overflow: auto; }
                blockquote {
                    margin-left: 0;
                    padding-left: 12px;
                    border-left: 3px solid rgba(127,127,127,.35);
                }
            </style>
        </head>
        <body>$content</body>
        </html>
    """.trimIndent()
    val loadKey = "${baseUrl.orEmpty()}\u0000$restrictOrigin\u0000$html"

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clipToBounds(),
        factory = { context ->
            val frameLayout = FrameLayout(context)
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.cacheMode = if (restrictOrigin != null) {
                    WebSettings.LOAD_NO_CACHE
                } else {
                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
                settings.domStorageEnabled = true
                settings.offscreenPreRaster = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                webViewClient = object : WebViewClient() {}
            }
            frameLayout.addView(webView)
            frameLayout
        },
        update = { frameLayout ->
            val webView = frameLayout.getChildAt(0) as? WebView ?: return@AndroidView
            if (webView.tag != loadKey) {
                webView.tag = loadKey
                onLoadingChange(true)
                val documentUrl = baseUrl.orEmpty()
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        onLoadingChange(false)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadingChange(false)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val origin = restrictOrigin ?: return null
                        val url = request?.url?.toString() ?: return null
                        if (isSelfContained(url) || RegistryUrls.isAllowedRestrictedResource(url, origin)) return null
                        // Block anything else without a network round trip.
                        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (restrictOrigin == null) return false
                        if (RegistryUrls.isInPageAnchor(url, documentUrl)) return false
                        if (RegistryUrls.isWebUrl(url)) onOpenExternal?.invoke(url)
                        return true
                    }
                }
                webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { frameLayout ->
            val webView = frameLayout.getChildAt(0) as? WebView
            frameLayout.removeAllViews()
            webView?.apply {
                stopLoading()
                destroy()
            }
        },
    )
}

/** Documents the WebView itself creates; they never hit the network. */
private fun isSelfContained(url: String): Boolean =
    url.startsWith("data:") || url.startsWith("about:") || url.startsWith("blob:")
