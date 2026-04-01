package io.github.seyud.weave.ui.flash

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.arch.BaseViewModel
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
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
import io.github.seyud.weave.events.SnackbarEvent
import com.topjohnwu.superuser.CallbackList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlashViewModel : BaseViewModel() {
    companion object {
        private const val MODULE_INSTALL_BANNER = """
            __        __                    __  __           _
            \ \      / /__  __ ___   _____ |  \/  | __ _ ___| | __
             \ \ /\ / / _ \/ _` \ \ / / _ \| |\/| |/ _` / __| |/ /
              \ V  V /  __/ (_| |\ V /  __/| |  | | (_| \__ \   <
               \_/\_/ \___|\__,_| \_/ \___||_|  |_|\__,_|___/_|\_\
        """

        internal val moduleInstallBannerLines: List<String> =
            MODULE_INSTALL_BANNER.trimIndent().lines()
    }

    enum class State {
        FLASHING, SUCCESS, FAILED
    }

    private val _state = MutableLiveData(State.FLASHING)
    val state: LiveData<State> get() = _state
    private var isInitialized = false
    private var isFlashingStarted = false

    var showReboot by mutableStateOf(Info.isRooted)
        private set

    private lateinit var request: FlashRequest
    private val _consoleLines = MutableStateFlow<List<String>>(emptyList())
    val consoleLines: StateFlow<List<String>> = _consoleLines.asStateFlow()

    private val console: MutableList<String>
        get() = logItems

    private val logItems = mutableListOf<String>().synchronized()
    private val outItems = object : CallbackList<String>() {
        override fun onAddElement(e: String?) {
            e ?: return
            logItems.add(e)
            viewModelScope.launch {
                _consoleLines.value = _consoleLines.value + e
            }
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
        _state.value = State.FLASHING
        showReboot = Info.isRooted
        synchronized(logItems) {
            logItems.clear()
        }
        _consoleLines.value = emptyList()
    }

    private fun appendVisibleConsoleLine(line: String) {
        logItems.add(line)
        _consoleLines.value = _consoleLines.value + line
    }

    private fun appendModuleInstallBanner() {
        moduleInstallBannerLines.forEach(::appendVisibleConsoleLine)
        appendVisibleConsoleLine("")
    }

    fun startFlashing() {
        if (isFlashingStarted) return
        isFlashingStarted = true
        val (action, uris) = request

        viewModelScope.launch {
            try {
                val result = when (action) {
                    Const.Value.FLASH_ZIP -> {
                        if (uris.isEmpty()) {
                            console.add("Error: No file selected")
                            false
                        } else {
                            appendModuleInstallBanner()
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
                            MagiskInstaller.Patch(uri, outItems, logItems).exec()
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
        uris.forEachIndexed { index, uri ->
            if (uris.size > 1) {
                appendVisibleConsoleLine("")
                appendVisibleConsoleLine("==== Module ${index + 1}/${uris.size}: ${uri.displayName} ====")
            }
            if (!FlashZip(uri, outItems, logItems).exec()) {
                return false
            }
        }
        return true
    }

    private fun onResult(success: Boolean) {
        _state.value = if (success) State.SUCCESS else State.FAILED
    }

    private fun savePressed() = withExternalRW {
        viewModelScope.launch(Dispatchers.IO) {
            val name = "magisk_install_log_%s.log".format(
                System.currentTimeMillis().toTime(timeFormatStandard)
            )
            val file = MediaStoreUtils.getFile(name)
            file.uri.outputStream().bufferedWriter().use { writer ->
                synchronized(logItems) {
                    logItems.forEach {
                        writer.write(it)
                        writer.newLine()
                    }
                }
            }
            SnackbarEvent(file.toString()).publish()
        }
    }

    fun saveLogForCompose(context: Context) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = "magisk_install_log_%s.log".format(
                        System.currentTimeMillis().toTime(timeFormatStandard)
                    )
                    val file = MediaStoreUtils.getFile(name)
                    file.uri.outputStream().bufferedWriter().use { writer ->
                        synchronized(logItems) {
                            logItems.forEach {
                                writer.write(it)
                                writer.newLine()
                            }
                        }
                    }
                    file.toString()
                }
            }

            result
                .onSuccess { savedPath ->
                    context.toast(savedPath, Toast.LENGTH_LONG)
                }
                .onFailure { error ->
                    context.toast(error.message ?: "保存日志失败", Toast.LENGTH_LONG)
                }
        }
    }

    fun saveLog() = savePressed()

    fun restartPressed() = reboot()
}
