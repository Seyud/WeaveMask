package io.github.seyud.weave.ui.log

import android.system.Os
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.arch.AsyncLoadViewModel
import io.github.seyud.weave.core.BuildConfig
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.R
import io.github.seyud.weave.core.ktx.timeFormatStandard
import io.github.seyud.weave.core.ktx.toTime
import io.github.seyud.weave.core.model.su.SuLog
import io.github.seyud.weave.core.repository.LogRepository
import io.github.seyud.weave.core.utils.MediaStoreUtils
import io.github.seyud.weave.core.utils.MediaStoreUtils.outputStream
import io.github.seyud.weave.events.SnackbarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream

class LogViewModel(
    private val repo: LogRepository
) : AsyncLoadViewModel() {

    private data class LoadResult(
        val magiskEntries: List<MagiskLogEntry>,
        val suItems: List<SuLog>,
    )

    var loadingState by mutableStateOf(true)
        private set
    private var hasLoadedOnce = false

    // --- su log
    val itemsState = mutableStateListOf<SuLog>()

    // --- magisk log
    val magiskLogEntriesState = mutableStateListOf<MagiskLogEntry>()
    var magiskLogRaw = " "

    fun ensureLoaded() {
        if (hasLoadedOnce) return
        startLoading()
    }

    override suspend fun doLoadWork() {
        loadingState = true

        try {
            val result = withContext(Dispatchers.Default) {
                magiskLogRaw = repo.fetchMagiskLogs()
                val magiskEntries = MagiskLogParser.parse(magiskLogRaw).asReversed()
                val suLogs = repo.fetchSuLogs()
                    .sortedByDescending { it.time }
                LoadResult(
                    magiskEntries = magiskEntries,
                    suItems = suLogs,
                )
            }

            magiskLogEntriesState.clear()
            magiskLogEntriesState.addAll(result.magiskEntries)

            itemsState.clear()
            itemsState.addAll(result.suItems)
            hasLoadedOnce = true
        } catch (e: Throwable) {
            SnackbarEvent(R.string.failure).publish()
        } finally {
            loadingState = false
        }
    }

    fun saveMagiskLog() = withExternalRW {
        viewModelScope.launch(Dispatchers.IO) {
            val filename = "magisk_log_%s.log".format(
                System.currentTimeMillis().toTime(timeFormatStandard))
            val logFile = MediaStoreUtils.getFile(filename)
            logFile.uri.outputStream().bufferedWriter().use { file ->
                file.write("---Detected Device Info---\n\n")
                file.write("isAB=${Info.isAB}\n")
                file.write("isSAR=${Info.isSAR}\n")
                file.write("ramdisk=${Info.ramdisk}\n")
                val uname = Os.uname()
                file.write("kernel=${uname.sysname} ${uname.machine} ${uname.release} ${uname.version}\n")

                file.write("\n\n---System Properties---\n\n")
                ProcessBuilder("getprop").start()
                    .inputStream.reader().use { it.copyTo(file) }

                file.write("\n\n---Environment Variables---\n\n")
                System.getenv().forEach { (key, value) -> file.write("${key}=${value}\n") }

                file.write("\n\n---System MountInfo---\n\n")
                FileInputStream("/proc/self/mountinfo").reader().use { it.copyTo(file) }

                file.write("\n---Magisk Logs---\n")
                file.write("${Info.env.versionString} (${Info.env.versionCode})\n\n")
                if (Info.env.isActive) file.write(magiskLogRaw)

                file.write("\n---Manager Logs---\n")
                file.write("${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})\n\n")
                ProcessBuilder("logcat", "-d").start()
                    .inputStream.reader().use { it.copyTo(file) }
            }
            SnackbarEvent(logFile.toString()).publish()
        }
    }

    fun clearMagiskLog() = repo.clearMagiskLogs {
        SnackbarEvent(R.string.logs_cleared).publish()
        startLoading()
    }

    fun clearLog() = viewModelScope.launch {
        repo.clearLogs()
        SnackbarEvent(R.string.logs_cleared).publish()
        startLoading()
    }
}
