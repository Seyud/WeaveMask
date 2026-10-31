package io.github.seyud.weave.ui.webui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.net.toUri
import io.github.seyud.weave.core.R as CoreR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Download bridge for module WebUIs (`ksu_download`), mirroring KernelSU manager:
 * `openExternal`, base64 `save`, and the chunked blob download trio.
 */
class WebUIDownloadInterface(private val state: WebUIState) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val webView get() = state.webView

    /**
     * Open an absolute http(s) URL in the browser instead of downloading it.
     *
     * Lint flags every implicit ACTION_VIEW because the app's own non-exported
     * SuRequestActivity declares a bare VIEW filter; that component is only ever started
     * explicitly, and a real link must go to the user's default browser, so this has to
     * stay implicit.
     */
    @SuppressLint("UnsafeImplicitIntentLaunch")
    @JavascriptInterface
    fun openExternal(url: String) {
        val currentWebView = webView ?: return
        val uri = url.toUri()
        if (uri.scheme != "http" && uri.scheme != "https") return
        val context = currentWebView.context
        // CATEGORY_BROWSABLE pins this to real link handlers: without it the intent also
        // matches the app's own non-exported SuRequestActivity VIEW filter.
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(intent)
        }.onFailure { Timber.e(it, "Failed to open $url") }
    }

    /** Save a base64 encoded blob (small files) into the public Downloads collection. */
    @JavascriptInterface
    fun save(base64: String, fileName: String?) {
        val context = webView?.context ?: return
        val displayName = sanitizeWebUIDownloadFileName(fileName)
        val mimeType = resolveWebUIDownloadMimeType(displayName, null)

        scope.launch {
            val error = runCatching {
                val data = Base64.decode(base64, Base64.DEFAULT)
                saveWebUIDownload(context, displayName, mimeType) { output -> output.write(data) }
            }.exceptionOrNull()
            error?.let { Timber.e(it, "Failed to save $displayName") }
            notifySaved(context, displayName, error)
        }
    }

    /** Begin a chunked (streaming) blob download; returns the internal download URL. */
    @JavascriptInterface
    fun startChunkedDownload(fileName: String?, mimeType: String?): String {
        val currentWebView = webView ?: return ""
        val displayName = sanitizeWebUIDownloadFileName(fileName)
        val resolvedMimeType = resolveWebUIDownloadMimeType(displayName, mimeType)
        return runCatching {
            BlobDownloadHandler.registerBlobDownload(
                displayName,
                resolvedMimeType,
                currentWebView.context.cacheDir,
            )
        }.getOrElse {
            Timber.e(it, "Failed to start chunked download")
            ""
        }
    }

    @JavascriptInterface
    fun writeDownloadChunk(downloadId: String, base64Chunk: String): Boolean =
        BlobDownloadHandler.writeChunk(downloadId, base64Chunk)

    /**
     * Finish a chunked download and move the spooled file into Downloads.
     * Returns as soon as the write is accepted; the copy runs off the bridge thread.
     */
    @JavascriptInterface
    fun completeChunkedDownload(downloadId: String): Boolean {
        val context = webView?.context ?: return false
        if (!BlobDownloadHandler.completeDownload(downloadId)) return false
        val completed = BlobDownloadHandler.takeCompleted(downloadId) ?: return false
        val mimeType = completed.mimeType
            ?: resolveWebUIDownloadMimeType(completed.fileName, null)

        scope.launch {
            val error = runCatching {
                completed.file.inputStream().use { input ->
                    saveWebUIDownload(context, completed.fileName, mimeType) { output ->
                        writeWebUIDownload(input, output)
                    }
                }
            }.exceptionOrNull()
            error?.let { Timber.e(it, "Failed to save ${completed.fileName}") }
            completed.file.delete()
            notifySaved(context, completed.fileName, error)
        }
        return true
    }

    @JavascriptInterface
    fun cancelChunkedDownload(downloadId: String) {
        BlobDownloadHandler.cancelDownload(downloadId)
    }

    fun destroy() {
        scope.cancel()
        BlobDownloadHandler.cleanup()
    }

    private fun notifySaved(context: Context, displayName: String, error: Throwable?) {
        val message = if (error == null) {
            context.getString(CoreR.string.webui_download_complete, displayName)
        } else {
            context.getString(CoreR.string.webui_download_failed, displayName, error.message.orEmpty())
        }
        postToast(message)
    }

    private fun postToast(message: String) {
        webView?.let { currentWebView ->
            currentWebView.post {
                Toast.makeText(currentWebView.context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
