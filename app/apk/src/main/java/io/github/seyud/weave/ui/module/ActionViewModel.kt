package io.github.seyud.weave.ui.module

import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.github.seyud.weave.arch.BaseViewModel
import io.github.seyud.weave.core.ktx.shellQuoted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.Collections

/**
 * 模块 action 执行的持有者（对齐上游 apk-ng 的架构方向：shell 操作与执行状态收敛到 ViewModel）。
 *
 * 相比原先把状态和 shell 调用写在 Composable 里的写法，旋转屏幕 / 进程重建 / 页面重进时
 * 不会丢失正在进行的执行结果；同一模块运行中重复触发为幂等 no-op，避免脚本被重复执行。
 */
class ActionViewModel : BaseViewModel() {

    private val _actionState = MutableStateFlow(ActionState.RUNNING)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    /** 控制台展示内容（仅 stdout）。 */
    private val _console = MutableStateFlow<List<String>>(emptyList())
    val console: StateFlow<List<String>> = _console.asStateFlow()

    /** 导出用完整日志（stdout + stderr），与控制台显示解耦。 */
    private val logItems = Collections.synchronizedList(mutableListOf<String>())

    private var runningModuleId: String? = null

    /**
     * 启动模块 action。同一模块仍在运行时直接返回（保持既有输出流），
     * 其余情况清空旧状态后重新执行。
     */
    fun startRunAction(moduleDir: String) {
        if (runningModuleId == moduleDir && _actionState.value == ActionState.RUNNING) return
        runningModuleId = moduleDir
        _console.value = emptyList()
        synchronized(logItems) { logItems.clear() }
        _actionState.value = ActionState.RUNNING

        val outItems = object : CallbackList<String>() {
            override fun onAddElement(e: String?) {
                e ?: return
                _console.update { it + e }
                logItems.add(e)
            }
        }

        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    // Run in BusyBox ash with standalone mode, like every other script in the
                    // context of Magisk; the manager shell itself is /system/bin/sh.
                    val cmd = "run_action ${moduleDir.shellQuoted()}"
                    Shell.cmd(
                        "ASH_STANDALONE=1 \$(magisk --path)/.magisk/busybox/busybox sh -c ${cmd.shellQuoted()}"
                    )
                        .to(outItems, logItems)
                        .exec().isSuccess
                }
                _actionState.value = if (success) ActionState.SUCCESS else ActionState.FAILED
            } catch (e: IOException) {
                Timber.e(e)
                _actionState.value = ActionState.FAILED
            }
        }
    }

    /** 返回导出用完整日志快照。 */
    fun copyLog(): List<String> = synchronized(logItems) { logItems.toList() }
}
