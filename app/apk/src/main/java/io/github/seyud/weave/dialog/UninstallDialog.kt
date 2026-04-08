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
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.tasks.MagiskInstaller
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 卸载对话框状态管理
 */
object UninstallDialog {

    /**
     * 对话框状态数据类
     *
     * @param visible 对话框是否可见
     * @param isRestoring 是否正在恢复镜像
     */
    data class DialogState(
        val visible: Boolean = false,
        val isRestoring: Boolean = false
    )
}

/**
 * 卸载对话框 Compose 组件
 * 使用 Miuix OverlayDialog 实现二次确认弹窗
 *
 * @param state 对话框状态
 * @param context Context 用于显示 Toast
 * @param onDismiss 关闭回调
 * @param onRestoreImg 恢复镜像回调
 * @param onCompleteUninstall 完全卸载回调
 */
@Composable
fun UninstallDialog(
    state: UninstallDialog.DialogState,
    context: Context,
    onDismiss: () -> Unit,
    onRestoreImg: () -> Unit,
    onCompleteUninstall: () -> Unit
) {
    if (!state.visible) return

    OverlayDialog(
        show = state.visible,
        title = context.getString(CoreR.string.uninstall_magisk_title),
        summary = context.getString(CoreR.string.uninstall_magisk_msg),
        onDismissRequest = onDismiss
    ) {
        if (state.isRestoring) {
            // 恢复中状态：显示进度指示器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    size = 32.dp,
                    strokeWidth = 3.dp
                )
            }
        } else {
            // 正常状态：显示恢复镜像和完全卸载按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = context.getString(CoreR.string.restore_img),
                    onClick = onRestoreImg,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = context.getString(CoreR.string.complete_uninstall),
                    onClick = onCompleteUninstall,
                    modifier = Modifier.weight(1f),
                    colors = TextButtonColors(
                        color = MiuixTheme.colorScheme.error,
                        disabledColor = MiuixTheme.colorScheme.error.copy(alpha = 0.38f),
                        textColor = MiuixTheme.colorScheme.onError,
                        disabledTextColor = MiuixTheme.colorScheme.onError.copy(alpha = 0.38f)
                    )
                )
            }
        }
    }
}
