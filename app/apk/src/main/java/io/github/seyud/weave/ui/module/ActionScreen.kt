package io.github.seyud.weave.ui.module

import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.ktx.timeFormatStandard
import io.github.seyud.weave.core.ktx.toTime
import io.github.seyud.weave.core.utils.MediaStoreUtils
import io.github.seyud.weave.core.utils.MediaStoreUtils.outputStream
import io.github.seyud.weave.ui.component.AutoScrollToBottom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
 * @param moduleId 模块 ID
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
    val listState = rememberLazyListState()

    // 状态管理：执行状态与输出收敛在 ActionViewModel（旋转屏幕/重进页面不丢失）
    val viewModel: ActionViewModel = viewModel()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val consoleLines by viewModel.console.collectAsStateWithLifecycle()
    val exitAction = remember(activity, fromShortcut, onNavigateBack) {
        {
            if (fromShortcut && activity != null) {
                activity.finishAndRemoveTask()
            } else {
                onNavigateBack()
            }
        }
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

    // 执行操作命令（幂等：同一模块运行中重复触发不会重复执行）
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

    // 自动滚动到底部（新的输出行始终可见；用户上滑回看历史时暂停跟随）
    AutoScrollToBottom(listState = listState, itemCount = consoleLines.size)

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
                        enabled = actionState != ActionState.RUNNING
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
                        visible = actionState != ActionState.RUNNING,
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
            // 控制台输出列表
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(consoleLines) { item ->
                        Text(
                            text = item,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 运行中指示器
            if (actionState == ActionState.RUNNING) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        size = 48.dp,
                        strokeWidth = 3.dp,
                    )
                }
            }

            // 关闭按钮（操作完成后显示）
            AnimatedVisibility(
                visible = actionState != ActionState.RUNNING,
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
