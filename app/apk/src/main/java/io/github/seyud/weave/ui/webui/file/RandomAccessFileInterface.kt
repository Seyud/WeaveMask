package io.github.seyud.weave.ui.webui.file

import android.util.Base64
import android.webkit.JavascriptInterface
import com.topjohnwu.superuser.io.SuRandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

class RandomAccessFileInterface {
    companion object {
        private const val MAX_OPEN_FILES = 30
    }
    
    private val openFiles = ConcurrentHashMap<String, SuRandomAccessFile>()

    @JavascriptInterface
    fun open(path: String, mode: String): String = runCatching {
        if (openFiles.size >= MAX_OPEN_FILES) {
            Timber.w("RandomAccessFile open rejected: too many open files")
            return ""
        }
        val raf = SuRandomAccessFile.open(path, mode)
        val id = java.util.UUID.randomUUID().toString()
        openFiles[id] = raf
        id
    }.onFailure { Timber.e(it, "RandomAccessFile open failed") }.getOrElse { "" }

    @JavascriptInterface
    fun read(id: String): Int = runCatching {
        val raf = openFiles[id] ?: return -1
        synchronized(raf) { raf.read() }
    }.onFailure { Timber.e(it, "RandomAccessFile read() failed") }.getOrElse { -1 }

    @JavascriptInterface
    fun readBytes(id: String, len: Int): String = runCatching {
        val raf = openFiles[id] ?: return ""
        val buffer = ByteArray(len)
        val bytesRead = synchronized(raf) { raf.read(buffer) }
        if (bytesRead > 0) {
            Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
        } else {
            ""
        }
    }.onFailure { Timber.e(it, "RandomAccessFile readBytes failed") }.getOrElse { "" }

    @JavascriptInterface
    fun readBoolean(id: String): Boolean = runCatching {
        val raf = openFiles[id] ?: return false
        synchronized(raf) { raf.readBoolean() }
    }.onFailure { Timber.e(it, "RandomAccessFile readBoolean failed") }.getOrElse { false }

    @JavascriptInterface
    fun readByte(id: String): Byte = runCatching {
        val raf = openFiles[id] ?: return 0
        synchronized(raf) { raf.readByte() }
    }.onFailure { Timber.e(it, "RandomAccessFile readByte failed") }.getOrElse { 0 }

    @JavascriptInterface
    fun readInt(id: String): Int = runCatching {
        val raf = openFiles[id] ?: return 0
        synchronized(raf) { raf.readInt() }
    }.onFailure { Timber.e(it, "RandomAccessFile readInt failed") }.getOrElse { 0 }

    @JavascriptInterface
    fun readLong(id: String): Long = runCatching {
        val raf = openFiles[id] ?: return 0L
        synchronized(raf) { raf.readLong() }
    }.onFailure { Timber.e(it, "RandomAccessFile readLong failed") }.getOrElse { 0L }

    @JavascriptInterface
    fun readShort(id: String): Short = runCatching {
        val raf = openFiles[id] ?: return 0.toShort()
        synchronized(raf) { raf.readShort() }
    }.onFailure { Timber.e(it, "RandomAccessFile readShort failed") }.getOrElse { 0.toShort() }

    @JavascriptInterface
    fun readFloat(id: String): Float = runCatching {
        val raf = openFiles[id] ?: return 0f
        synchronized(raf) { raf.readFloat() }
    }.onFailure { Timber.e(it, "RandomAccessFile readFloat failed") }.getOrElse { 0f }

    @JavascriptInterface
    fun readDouble(id: String): Double = runCatching {
        val raf = openFiles[id] ?: return 0.0
        synchronized(raf) { raf.readDouble() }
    }.onFailure { Timber.e(it, "RandomAccessFile readDouble failed") }.getOrElse { 0.0 }

    @JavascriptInterface
    fun readUTF(id: String): String = runCatching {
        val raf = openFiles[id] ?: return ""
        synchronized(raf) { raf.readUTF() }
    }.onFailure { Timber.e(it, "RandomAccessFile readUTF failed") }.getOrElse { "" }

    @JavascriptInterface
    fun readLine(id: String): String? = runCatching {
        val raf = openFiles[id] ?: return null
        synchronized(raf) { raf.readLine() }
    }.onFailure { Timber.e(it, "RandomAccessFile readLine failed") }.getOrNull()

    @JavascriptInterface
    fun write(id: String, b: Int): Unit = runCatching {
        val raf = openFiles[id] ?: return
        synchronized(raf) { raf.write(b) }
    }.onFailure { Timber.e(it, "RandomAccessFile write failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun writeBase64(id: String, data: String): Unit = runCatching {
        val raf = openFiles[id] ?: return
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        synchronized(raf) { raf.write(bytes) }
    }.onFailure { Timber.e(it, "RandomAccessFile writeBase64 failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun writeBoolean(id: String, v: Boolean): Unit = runCatching {
        val raf = openFiles[id] ?: return
        synchronized(raf) { raf.writeBoolean(v) }
    }.onFailure { Timber.e(it, "RandomAccessFile writeBoolean failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun writeByte(id: String, v: Int): Unit = runCatching {
        val raf = openFiles[id] ?: return
        synchronized(raf) { raf.writeByte(v) }
    }.onFailure { Timber.e(it, "RandomAccessFile writeByte failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun writeInt(id: String, v: Int): Unit = runCatching {
        val raf = openFiles[id] ?: return
        synchronized(raf) { raf.writeInt(v) }
    }.onFailure { Timber.e(it, "RandomAccessFile writeInt failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun writeLong(id: String, v: Long): Unit = runCatching {
        val raf = openFiles[id] ?: return
        synchronized(raf) { raf.writeLong(v) }
    }.onFailure { Timber.e(it, "RandomAccessFile writeLong failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun writeShort(id: String, v: Int): Unit = runCatching {
        val raf = openFiles[id] ?: return
        synchronized(raf) { raf.writeShort(v) }
    }.onFailure { Timber.e(it, "RandomAccessFile writeShort failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun writeFloat(id: String, v: Float): Unit = runCatching {
        val raf = openFiles[id] ?: return
        synchronized(raf) { raf.writeFloat(v) }
    }.onFailure { Timber.e(it, "RandomAccessFile writeFloat failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun writeDouble(id: String, v: Double): Unit = runCatching {
        val raf = openFiles[id] ?: return
        synchronized(raf) { raf.writeDouble(v) }
    }.onFailure { Timber.e(it, "RandomAccessFile writeDouble failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun writeUTF(id: String, str: String): Unit = runCatching {
        val raf = openFiles[id] ?: return
        synchronized(raf) { raf.writeUTF(str) }
    }.onFailure { Timber.e(it, "RandomAccessFile writeUTF failed") }.getOrDefault(Unit)

    @JavascriptInterface
    fun seek(id: String, pos: Long): Boolean = runCatching {
        val raf = openFiles[id] ?: return false
        synchronized(raf) { raf.seek(pos) }
        true
    }.onFailure { Timber.e(it, "RandomAccessFile seek failed") }.getOrElse { false }

    @JavascriptInterface
    fun getFilePointer(id: String): Long = runCatching {
        val raf = openFiles[id] ?: return -1L
        synchronized(raf) { raf.getFilePointer() }
    }.onFailure { Timber.e(it, "RandomAccessFile getFilePointer failed") }.getOrElse { -1L }

    @JavascriptInterface
    fun length(id: String): Long = runCatching {
        val raf = openFiles[id] ?: return -1L
        synchronized(raf) { raf.length() }
    }.onFailure { Timber.e(it, "RandomAccessFile length failed") }.getOrElse { -1L }

    @JavascriptInterface
    fun setLength(id: String, newLength: Long): Boolean = runCatching {
        val raf = openFiles[id] ?: return false
        synchronized(raf) { raf.setLength(newLength) }
        true
    }.onFailure { Timber.e(it, "RandomAccessFile setLength failed") }.getOrElse { false }

    @JavascriptInterface
    fun close(id: String): Boolean {
        val raf = openFiles.remove(id) ?: return false
        return runCatching {
            synchronized(raf) { raf.close() }
            true
        }.onFailure { Timber.e(it, "RandomAccessFile close failed") }.getOrElse { false }
    }

    fun closeAll() {
        openFiles.forEach { (id, raf) ->
            runCatching {
                synchronized(raf) { raf.close() }
            }.onFailure { Timber.e(it, "closeAll failed for $id") }
        }
        openFiles.clear()
    }
}
