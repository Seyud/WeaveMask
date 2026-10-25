package io.github.seyud.weave.core.download

import android.net.Uri
import io.github.seyud.weave.core.R
import io.github.seyud.weave.core.ktx.cachedFile
import io.github.seyud.weave.core.ktx.copyAll
import io.github.seyud.weave.core.ktx.copyAndClose
import io.github.seyud.weave.core.ktx.withInOut
import io.github.seyud.weave.core.ktx.writeTo
import io.github.seyud.weave.core.utils.MediaStoreUtils.outputStream
import io.github.seyud.weave.utils.APKInstall
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class DownloadProcessor(notifier: DownloadNotifier) : DownloadNotifier by notifier {

    suspend fun handle(stream: InputStream, subject: Subject) {
        when (subject) {
            is Subject.App -> handleApp(stream, subject)
            is Subject.Module -> handleModule(stream, subject.file)
            else -> stream.copyAndClose(subject.file.outputStream())
        }
    }

    suspend fun handleApp(stream: InputStream, subject: Subject.App) {
        val external = subject.file.outputStream()
        val session = APKInstall.startSession(context)
        stream.copyAndClose(TeeOutputStream(external, session.openStream(context)))
        subject.intent = session.waitIntent()
    }

    suspend fun handleModule(src: InputStream, file: Uri) {
        val tmp = context.cachedFile("module.zip")
        try {
            // First download the entire zip into cache so we can process it
            src.writeTo(tmp)

            val input = ZipFile.Builder().setFile(tmp).get()
            val output = ZipArchiveOutputStream(file.outputStream())
            withInOut(input, output) { zin, zout ->
                zout.putArchiveEntry(ZipArchiveEntry("META-INF/"))
                zout.closeArchiveEntry()
                zout.putArchiveEntry(ZipArchiveEntry("META-INF/com/"))
                zout.closeArchiveEntry()
                zout.putArchiveEntry(ZipArchiveEntry("META-INF/com/google/"))
                zout.closeArchiveEntry()
                zout.putArchiveEntry(ZipArchiveEntry("META-INF/com/google/android/"))
                zout.closeArchiveEntry()

                zout.putArchiveEntry(ZipArchiveEntry("META-INF/com/google/android/update-binary"))
                context.assets.open("module_installer.sh").use { it.copyAll(zout) }
                zout.closeArchiveEntry()

                zout.putArchiveEntry(ZipArchiveEntry("META-INF/com/google/android/updater-script"))
                zout.write("#MAGISK\n".toByteArray())
                zout.closeArchiveEntry()

                // Then simply copy all entries to output
                zin.copyRawEntries(zout) { entry -> !entry.name.startsWith("META-INF") }
            }
        } finally {
            tmp.delete()
        }
    }

    private class TeeOutputStream(
        private val o1: OutputStream,
        private val o2: OutputStream
    ) : OutputStream() {
        override fun write(b: Int) {
            o1.write(b)
            o2.write(b)
        }
        override fun write(b: ByteArray?, off: Int, len: Int) {
            o1.write(b, off, len)
            o2.write(b, off, len)
        }
        override fun close() {
            o1.close()
            o2.close()
        }
    }
}
