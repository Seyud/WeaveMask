package io.github.seyud.weave.ui.module

import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seyud.weave.core.ktx.timeFormatStandard
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.ktx.toTime
import io.github.seyud.weave.core.utils.MediaStoreUtils
import io.github.seyud.weave.core.utils.MediaStoreUtils.outputStream
import io.github.seyud.weave.ui.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import io.github.seyud.weave.core.R as CoreR

/**
 * 操作执行状态
 */
enum class ActionState {
    RUNNING,
    SUCCESS,
    FAILED
}

/**
 * 模块操作页面（纯 Compose 实现）
 * 替代原有的 ActionFragment，完全迁移到 Compose 架构
 *
 * 输出走终端视图（[TerminalScreen]）：action.sh 跑在 PTY 里，所以 ANSI 颜色、`\r` 覆盖写、
 * `stty` 与交互输入的表现和上游 Magisk 一致，而不是逐行文本列表。
 *
 * @param moduleId 模块目录名（Route.Action 里沿用的字段名）
 * @param moduleName 模块名称
 * @param onNavigateBack 返回回调
 * @param modifier Modifier
 */
@Composable
fun ActionScreen(
    moduleId: String,
    moduleName: String,
    fromShortcut: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 操作脚本运行期间禁止边缘横移返回 */
    onBackGestureEnabledChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    // 状态与终端收敛在 ActionViewModel（旋转屏幕/重进页面不丢失输出，脚本也不会重跑）
    val viewModel: ActionViewModel = viewModel(
        factory = viewModelFactory { initializer { ActionViewModel(createSavedStateHandle()) } }
    )
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val exitAction = remember(activity, fromShortcut, onNavigateBack) {
        {
            if (fromShortcut && activity != null) {
                activity.finishAndRemoveTask()
            } else {
                onNavigateBack()
            }
        }
    }

    // 进程在一次 action 执行中被杀过：脚本和输出都已经没了，绝不能再跑一遍，
    // 直接退出这个页面（上游同样处理）。
    if (viewModel.isInterrupted) {
        LaunchedEffect(Unit) {
            exitAction()
        }
        return
    }

    // 锁定屏幕方向
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        onDispose {
            if (originalOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                activity?.requestedOrientation = originalOrientation
            }
        }
    }

    // 执行操作命令（startRunAction 幂等：同一页面生命周期内只跑一次）
    // 完成提示通过订阅状态流转发出，避免重进页面时回放上一次的成功提示
    val doneActionText = stringResource(CoreR.string.done_action, moduleName)
    LaunchedEffect(moduleId) {
        viewModel.startRunAction(moduleId)
        viewModel.actionState.collect { state ->
            if (state == ActionState.SUCCESS) {
                context.toast(doneActionText, Toast.LENGTH_SHORT)
            }
        }
    }

    // 处理返回键
    val isRunning = actionState == ActionState.RUNNING
    BackHandler(enabled = isRunning) {
        // 运行中时不允许返回
    }
    // 边缘横移手势与返回键对齐
    LaunchedEffect(isRunning) {
        onBackGestureEnabledChange(!isRunning)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = moduleName,
                navigationIcon = {
                    IconButton(
                        onClick = exitAction,
                        enabled = !isRunning
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    // 保存日志按钮
                    AnimatedVisibility(
                        visible = !isRunning,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val fileName = "%s_action_log_%s.log".format(
                                            moduleName,
                                            System.currentTimeMillis().toTime(timeFormatStandard)
                                        )
                                        val file = MediaStoreUtils.getFile(fileName)
                                        val logSnapshot = viewModel.copyLog()
                                        file.uri.outputStream().bufferedWriter().use { writer ->
                                            logSnapshot.forEach {
                                                writer.write(it)
                                                writer.newLine()
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            context.toast(file.toString(), Toast.LENGTH_SHORT)
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e)
                                        withContext(Dispatchers.Main) {
                                            context.toast(CoreR.string.failure, Toast.LENGTH_SHORT)
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Download,
                                contentDescription = stringResource(CoreR.string.menuSaveLog)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 终端视图：原始输出（含颜色 / 覆盖写），滚动与自动跟随由终端自己处理
            TerminalScreen(
                emulator = viewModel.emulator,
                modifier = Modifier.fillMaxSize(),
                onResized = viewModel::onTerminalResized,
            )

            // 关闭按钮（操作完成后显示）
            AnimatedVisibility(
                visible = !isRunning,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(
                    onClick = exitAction,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(text = stringResource(CoreR.string.close))
                }
            }
        }
    }
}
