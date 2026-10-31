package io.github.seyud.weave.ui.webui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

private const val DEFAULT_WEBUI_DOWNLOAD_NAME = "download.bin"

/** Strip directory components so a WebUI cannot escape the Downloads collection. */
internal fun sanitizeWebUIDownloadFileName(requestedFileName: String?): String {
    val trimmed = requestedFileName?.trim().orEmpty()
    if (trimmed.isEmpty()) return DEFAULT_WEBUI_DOWNLOAD_NAME

    val candidate = trimmed
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()

    if (candidate.isEmpty() || candidate == "." || candidate == "..") {
        return DEFAULT_WEBUI_DOWNLOAD_NAME
    }

    return candidate
}

internal fun resolveWebUIDownloadMimeType(fileName: String, fallback: String?): String {
    return MimeUtil.getMimeFromFileName(fileName)
        ?: fallback?.takeIf { it.isNotBlank() }
        ?: "application/octet-stream"
}

/**
 * Write a WebUI download into the public Downloads collection.
 *
 * Android 10+ goes through MediaStore with an `IS_PENDING` window so the file only
 * becomes visible once it is fully written. Older releases fall back to direct file IO
 * in the public Downloads directory.
 */
internal fun saveWebUIDownload(
    context: Context,
    displayName: String,
    mimeType: String,
    write: (OutputStream) -> Unit,
): Uri {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Failed to create download")
        try {
            val output = resolver.openOutputStream(uri) ?: error("Failed to open download")
            output.use(write)
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, published, null, null) != 1) {
                error("Failed to publish download")
            }
            return uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION")
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    dir.mkdirs()
    val target = File(dir, displayName)
    FileOutputStream(target).use(write)
    return Uri.fromFile(target)
}

internal fun writeWebUIDownload(
    target: File,
    source: InputStream,
    onBytesWritten: (Long) -> Unit = {},
): Long {
    target.parentFile?.mkdirs()
    return FileOutputStream(target).use { output ->
        writeWebUIDownload(source, output, onBytesWritten)
    }
}

internal fun writeWebUIDownload(
    source: InputStream,
    output: OutputStream,
    onBytesWritten: (Long) -> Unit = {},
): Long {
    output.buffered(64 * 1024).use { bufferedOutput ->
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = source.read(buffer)
            if (read == -1) {
                break
            }
            bufferedOutput.write(buffer, 0, read)
            total += read
            onBytesWritten(total)
        }
        bufferedOutput.flush()
        return total
    }
}
