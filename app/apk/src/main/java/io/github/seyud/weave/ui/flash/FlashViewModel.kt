package io.github.seyud.weave.ui.flash

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.arch.BaseViewModel
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.core.ktx.reboot
import io.github.seyud.weave.core.ktx.synchronized
import io.github.seyud.weave.core.ktx.timeFormatStandard
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.ktx.toTime
import io.github.seyud.weave.core.tasks.FlashZip
import io.github.seyud.weave.core.tasks.MagiskInstaller
import io.github.seyud.weave.core.utils.MediaStoreUtils
import io.github.seyud.weave.core.utils.MediaStoreUtils.displayName
import io.github.seyud.weave.core.utils.MediaStoreUtils.outputStream
import io.github.seyud.weave.terminal.TerminalEmulator
import io.github.seyud.weave.terminal.appendLineOnMain
import io.github.seyud.weave.terminal.runSuCommand
import com.topjohnwu.superuser.CallbackList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlashViewModel(private val savedState: SavedStateHandle) : BaseViewModel() {

    enum class State {
        FLASHING, SUCCESS, FAILED
    }

    /**
     * 进程被杀之前这次刷写已经启动过：安装器和终端输出只活在内存里，已经没了，
     * 重跑一次并不安全（可能半写完 modules_update、已 patch 过 boot），所以页面直接退出。
     * 与上游 FlashViewModel 同一语义。
     */
    val isInterrupted = savedState.get<Boolean>(KEY_STARTED) == true

    private val _state = MutableLiveData(State.FLASHING)
    val state: LiveData<State> get() = _state
    private var isInitialized = false
    private var isFlashingStarted = false

    var showReboot by mutableStateOf(Info.isRooted)
        private set

    private lateinit var request: FlashRequest

    /** 当前刷写类型；保存日志时决定是否补回字符画。 */
    private var flashAction: String = ""

    /** 终端屏幕；由 [io.github.seyud.weave.ui.terminal.TerminalScreen] 渲染。 */
    val emulator = TerminalEmulator(
        columns = 256,
        rows = 40,
        cellWidthPixels = 0,
        cellHeightPixels = 0,
        transcriptRows = null,
    )

    private val terminalReady = CompletableDeferred<Unit>()

    /** 终端完成一次尺寸测量后回调：安装器要按真实宽高启动。 */
    fun onTerminalResized() {
        terminalReady.complete(Unit)
    }

    private val console: MutableList<String>
        get() = logItems

    private val logItems = mutableListOf<String>().synchronized()

    /** 走 [MagiskInstaller] 的分支没有 PTY，逐行写进终端。 */
    private val outItems = object : CallbackList<String>() {
        override fun onAddElement(e: String?) {
            e ?: return
            logItems.add(e)
            emulator.appendLineOnMain(e)
        }
    }

    /**
     * 准备 Compose 界面的刷写环境
     * 每次进入 Flash 页面时都会调用，重置所有状态以确保可以重复执行安装
     *
     * @param action 刷写操作类型（如 FLASH_MAGISK、PATCH_FILE 等）
     * @param uris 附加数据 URI 列表（如模块 ZIP 或修补文件 URI）
     */
    fun prepareForCompose(action: String, uris: List<android.net.Uri>) {
        // 重置状态标志，允许重复初始化
        isInitialized = true
        isFlashingStarted = false

        // 重新初始化参数和状态
        request = FlashRequest(action = action, dataUris = uris)
        flashAction = action
        _state.value = State.FLASHING
        showReboot = Info.isRooted
        synchronized(logItems) {
            logItems.clear()
        }
    }

    private fun appendVisibleConsoleLine(line: String) {
        logItems.add(line)
        emulator.appendLineOnMain(line)
    }

    fun startFlashing() {
        if (isFlashingStarted) return
        isFlashingStarted = true
        // 先落标记再干活：进程随时可能没，恢复后靠它判断"已经跑过，别重跑"
        savedState[KEY_STARTED] = true
        val (action, uris) = request

        viewModelScope.launch {
            try {
                val result = when (action) {
                    Const.Value.FLASH_ZIP -> {
                        if (uris.isEmpty()) {
                            console.add("Error: No file selected")
                            false
                        } else {
                            appendVisibleConsoleLine("")
                            flashSelectedModules(uris)
                        }
                    }
                    Const.Value.UNINSTALL -> {
                        showReboot = false
                        MagiskInstaller.Uninstall(outItems, logItems).exec()
                    }
                    Const.Value.FLASH_MAGISK -> {
                        if (Info.isEmulator)
                            MagiskInstaller.Emulator(outItems, logItems).exec()
                        else
                            MagiskInstaller.Direct(outItems, logItems).exec()
                    }
                    Const.Value.FLASH_INACTIVE_SLOT -> {
                        showReboot = false
                        MagiskInstaller.SecondSlot(outItems, logItems).exec()
                    }
                    Const.Value.PATCH_FILE -> {
                        val uri = uris.singleOrNull()
                        if (uri == null) {
                            console.add("Error: No file selected")
                            false
                        } else {
                            showReboot = false
                            MagiskInstaller.Patch(uri, console = outItems, logs = logItems).exec()
                        }
                    }
                    Const.Value.DOWNLOAD -> {
                        val url = uris.singleOrNull()?.toString()
                        if (url.isNullOrEmpty()) {
                            console.add("Error: No URL provided")
                            false
                        } else {
                            showReboot = false
                            MagiskInstaller.Download(url, console = outItems, logs = logItems).exec()
                        }
                    }
                    else -> {
                        console.add("Error: Unknown action: $action")
                        false
                    }
                }
                onResult(result)
            } catch (e: Exception) {
                console.add("Error: ${e.message}")
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    private suspend fun flashSelectedModules(uris: List<android.net.Uri>): Boolean {
        // 等终端按视口尺寸 resize 过再启动，安装器才能拿到真实列宽
        terminalReady.await()
        val columns = emulator.mColumns
        val rows = emulator.mRows
        uris.forEachIndexed { index, uri ->
            if (uris.size > 1) {
                appendVisibleConsoleLine("")
                appendVisibleConsoleLine("==== Module ${index + 1}/${uris.size}: ${uri.displayName} ====")
            }
            // 准备阶段（拷贝 zip、准备 update-binary）产生的行先攒着，在执行安装器前
            // 一次性回放到终端，保证顺序正确；安装器本身跑在 PTY 里（busybox script）。
            val pending = mutableListOf<String>()
            val ptyRunner: suspend (String) -> Boolean = { command ->
                pending.forEach(emulator::appendLineOnMain)
                pending.clear()
                withContext(Dispatchers.IO) { runSuCommand(emulator, columns, rows, command) }
            }
            val success = FlashZip(uri, pending, logItems, ptyRunner).exec()
            pending.forEach(emulator::appendLineOnMain)
            if (!success) {
                return false
            }
        }
        return true
    }

    private fun onResult(success: Boolean) {
        _state.value = if (success) State.SUCCESS else State.FAILED
    }

    fun saveLogForCompose(context: Context) = withExternalRW {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = "magisk_install_log_%s.log".format(
                        System.currentTimeMillis().toTime(timeFormatStandard)
                    )
                    val file = MediaStoreUtils.getFile(name)
                    file.uri.outputStream().bufferedWriter().use { writer ->
                        // 字符画现在由界面绘制（见 ModuleInstallBanner），文本日志里补回去
                        if (flashAction == Const.Value.FLASH_ZIP && moduleInstallBannerLines.isNotEmpty()) {
                            moduleInstallBannerLines.forEach {
                                writer.write(it)
                                writer.newLine()
                            }
                            writer.newLine()
                        }
                        // 终端 transcript 同时覆盖 PTY（安装器）与逐行（MagiskInstaller）两条路径
                        writer.write(emulator.screen.transcriptText)
                    }
                    file.toString()
                }
            }

            result
                .onSuccess { savedPath ->
                    context.toast(savedPath, Toast.LENGTH_LONG)
                }
                .onFailure { error ->
                    context.toast(error.message ?: context.getString(CoreR.string.save_log_failed), Toast.LENGTH_LONG)
                }
        }
    }

    fun restartPressed() = reboot()

    private companion object {
        /** 标记这次刷写已经启动过（SavedStateHandle 会写进该路由条目的 saved state）。 */
        const val KEY_STARTED = "started"
    }
}
