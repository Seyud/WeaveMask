package com.topjohnwu.magisk.ui.module

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.ktx.timeFormatStandard
import com.topjohnwu.magisk.core.ktx.toTime
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
import com.topjohnwu.superuser.Shell
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException
import com.topjohnwu.magisk.core.R as CoreR

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
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 状态管理
    var actionState by remember { mutableStateOf(ActionState.RUNNING) }
    val consoleItems = remember { mutableStateListOf<String>() }
    val logItems = remember { mutableStateListOf<String>() }

    // 锁定屏幕方向
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        onDispose {
            if (originalOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                activity?.requestedOrientation = originalOrientation
            }
        }
    }

    // 执行操作命令
    LaunchedEffect(moduleId) {
        scope.launch(Dispatchers.IO) {
            try {
                val outItems = object : com.topjohnwu.superuser.CallbackList<String>() {
                    override fun onAddElement(e: String?) {
                        e ?: return
                        consoleItems.add(e)
                        logItems.add(e)
                    }
                }

                val success = Shell.cmd("run_action '$moduleId'")
                    .to(outItems, logItems)
                    .exec().isSuccess

                withContext(Dispatchers.Main) {
                    actionState = if (success) ActionState.SUCCESS else ActionState.FAILED
                }
            } catch (e: IOException) {
                Timber.e(e)
                withContext(Dispatchers.Main) {
                    actionState = ActionState.FAILED
                }
            }
        }
    }

    // 自动滚动到底部
    LaunchedEffect(consoleItems.size) {
        if (consoleItems.isNotEmpty()) {
            listState.animateScrollToItem(consoleItems.size - 1)
        }
    }

    // 处理返回键
    BackHandler(enabled = actionState == ActionState.RUNNING) {
        // 运行中时不允许返回
    }

    // 显示完成提示
    LaunchedEffect(actionState) {
        if (actionState == ActionState.SUCCESS) {
            context.toast(
                context.getString(CoreR.string.done_action, moduleName),
                Toast.LENGTH_SHORT
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = moduleName,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
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
                                        file.uri.outputStream().bufferedWriter().use { writer ->
                                            logItems.forEach {
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
                                contentDescription = context.getString(CoreR.string.menuSaveLog)
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
                    items(consoleItems) { item ->
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
                        modifier = Modifier.size(48.dp),
                        color = MiuixTheme.colorScheme.primary
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
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(text = context.getString(CoreR.string.close))
                }
            }
        }
    }
}
