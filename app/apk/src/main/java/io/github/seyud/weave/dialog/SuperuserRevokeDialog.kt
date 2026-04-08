package io.github.seyud.weave.dialog

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.R as CoreR
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 撤销超级用户权限对话框状态管理
 */
object SuperuserRevokeDialog {

    /**
     * 对话框状态数据类
     *
     * @param visible 对话框是否可见
     * @param appName 应用名称
     */
    data class DialogState(
        val visible: Boolean = false,
        val appName: String = ""
    )
}

/**
 * 撤销超级用户权限对话框 Compose 组件
 * 使用 Miuix OverlayDialog 实现二次确认弹窗
 *
 * @param state 对话框状态
 * @param context Context 用于获取字符串资源
 * @param onDismiss 关闭回调
 * @param onConfirm 确认撤销回调
 */
@Composable
fun SuperuserRevokeDialog(
    state: SuperuserRevokeDialog.DialogState,
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!state.visible) return

    OverlayDialog(
        show = state.visible,
        title = context.getString(CoreR.string.su_revoke_title),
        summary = context.getString(CoreR.string.su_revoke_msg, state.appName),
        onDismissRequest = onDismiss
    ) {
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
                onClick = onConfirm,
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
