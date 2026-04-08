package io.github.seyud.weave.dialog

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.BuildConfig
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.core.ktx.await
import io.github.seyud.weave.core.ktx.reboot
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.tasks.MagiskInstaller
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.UiThreadHandler
import androidx.core.os.postDelayed
import io.github.seyud.weave.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * EnvFixDialog 使用 Miuix OverlayDialog 实现
 * 用于修复 Magisk 运行环境
 */
object EnvFixDialog {

    enum class State {
        IDLE,           // 未显示
        NORMAL_FIX,     // 普通修复模式
        FULL_FIX,       // 完整修复模式（需要重新安装）
        FIXING          // 修复中
    }

    data class DialogState(
        val visible: Boolean = false,
        val state: State = State.IDLE,
        val code: Int = 0
    )

    /**
     * 检查是否需要完整修复
     */
    fun isFullFixRequired(code: Int): Boolean {
        return code == 2 ||
            Info.env.versionCode != BuildConfig.APP_VERSION_CODE ||
            Info.env.versionString != BuildConfig.APP_VERSION_NAME
    }

    /**
     * 执行环境检查
     * @return 返回错误码，0 表示正常，非 0 表示需要修复
     */
    suspend fun checkEnv(): Int {
        val cmd = "env_check ${Info.env.versionString} ${Info.env.versionCode}"
        return Shell.cmd(cmd).await().code
    }
}

/**
 * EnvFixDialog Compose 组件
 *
 * @param state 对话框状态
 * @param context Context 用于显示 Toast
 * @param onDismiss 关闭回调
 * @param onNavigateToInstall 导航到安装页面回调（用于完整修复模式）
 */
@Composable
fun EnvFixDialog(
    state: EnvFixDialog.DialogState,
    context: Context,
    onDismiss: () -> Unit,
    onNavigateToInstall: () -> Unit
) {
    if (!state.visible) return

    val coroutineScope = rememberCoroutineScope()

    val title = when (state.state) {
        EnvFixDialog.State.FIXING -> context.getString(CoreR.string.setup_title)
        else -> context.getString(CoreR.string.env_fix_title)
    }

    val summary = when (state.state) {
        EnvFixDialog.State.FIXING -> context.getString(CoreR.string.setup_msg)
        EnvFixDialog.State.FULL_FIX -> context.getString(CoreR.string.env_full_fix_msg)
        else -> context.getString(CoreR.string.env_fix_msg)
    }

    OverlayDialog(
        show = state.visible,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss
    ) {
        when (state.state) {
            EnvFixDialog.State.FULL_FIX -> {
                // 完整修复模式：显示确定按钮，跳转到安装页面
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = context.getString(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        text = context.getString(android.R.string.ok),
                        onClick = onNavigateToInstall,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
            EnvFixDialog.State.FIXING -> {
                // 修复中状态：只显示进度指示器，不可取消
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        size = 32.dp,
                        strokeWidth = 3.dp
                    )
                }
            }
            else -> {
                // 普通修复模式：显示取消和确定按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = context.getString(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        text = context.getString(android.R.string.ok),
                        onClick = {
                            coroutineScope.launch {
                                MagiskInstaller.FixEnv().exec { success ->
                                    onDismiss()
                                    context.toast(
                                        if (success) CoreR.string.reboot_delay_toast else CoreR.string.setup_fail,
                                        Toast.LENGTH_LONG
                                    )
                                    if (success) {
                                        UiThreadHandler.handler.postDelayed({ reboot() }, 5000)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}
