package io.github.seyud.weave.ui.module

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.arch.BaseViewModel
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.ktx.shellQuoted
import io.github.seyud.weave.terminal.TerminalEmulator
import io.github.seyud.weave.terminal.runSuCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 模块 action 执行的持有者（对齐上游 apk-ng 的架构方向：shell 操作与执行状态收敛到 ViewModel）。
 *
 * 脚本跑在 PTY 里（[runSuCommand] → busybox `script`），终端状态也挂在 ViewModel 上，
 * 所以旋转屏幕 / 重进页面都不会丢输出，脚本也能拿到真实的 `isatty` 与终端宽高。
 */
class ActionViewModel(private val savedState: SavedStateHandle) : BaseViewModel() {

    /**
     * 进程被杀之前 action 已经启动过：脚本与终端输出都只存在内存里，已经没了，
     * 绝不能重跑一次（上游 ActionViewModel 同样语义），页面直接退出。
     */
    val isInterrupted = savedState.get<Boolean>(KEY_STARTED) == true

    private val _actionState = MutableStateFlow(ActionState.RUNNING)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    /** 终端屏幕；由 [io.github.seyud.weave.ui.terminal.TerminalScreen] 渲染。 */
    val emulator = TerminalEmulator(
        columns = 256,
        rows = 40,
        cellWidthPixels = 0,
        cellHeightPixels = 0,
        transcriptRows = null,
    )

    private val terminalReady = CompletableDeferred<Unit>()

    /** 终端完成一次尺寸测量后回调：命令要按真实宽高启动，`stty`/折行才对。 */
    fun onTerminalResized() {
        terminalReady.complete(Unit)
    }

    /**
     * 启动模块 action。同一页面生命周期内只执行一次（旋转 / 重进不会重跑脚本）。
     *
     * 命令与上游一致：先 chdir 到模块目录，再用 `sh` 执行 `action.sh`；外层 PTY 已用
     * `ASH_STANDALONE=1` 的 busybox ash 包好，因此脚本里的 `busybox`、颜色、`stty`、
     * 交互式输入都是 Magisk 原生环境的行为。
     *
     * @param dirName 模块目录名，即 `/data/adb/modules/<dirName>`。目录名与 module.prop 的
     *   id 可能不同（上游 d582d8145），定位一律用目录名。
     */
    fun startRunAction(dirName: String) {
        if (savedState.get<Boolean>(KEY_STARTED) == true) return
        savedState[KEY_STARTED] = true
        val dir = "${Const.MODULE_PATH}/$dirName"
        viewModelScope.launch {
            // 等终端按视口尺寸 resize 过再启动，否则列宽/高度还是默认值
            terminalReady.await()
            val columns = emulator.mColumns
            val rows = emulator.mRows
            val success = withContext(Dispatchers.IO) {
                // 与上游逐字一致：PTY 外层已是 ASH_STANDALONE=1 的 busybox ash，
                // standalone shell 会把 sh / 各 applet 直接拦截成 busybox，无需改 PATH。
                runSuCommand(emulator, columns, rows, "cd ${dir.shellQuoted()} && sh ./action.sh")
            }
            _actionState.value = if (success) ActionState.SUCCESS else ActionState.FAILED
        }
    }

    /** 导出用日志：终端 transcript 全文（stdout + stderr 合并）。 */
    fun copyLog(): List<String> = emulator.screen.transcriptText.lines()

    companion object {
        private const val KEY_STARTED = "started"
    }
}
