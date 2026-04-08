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
import io.github.seyud.weave.ui.module.ModuleInstallTarget
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import io.github.seyud.weave.core.R as CoreR

/**
 * 本地模块安装确认对话框
 * 使用 Miuix Compose 对话框呈现安装确认内容
 */
object LocalModuleInstallDialog {

    /**
     * 对话框状态
     *
     * @param visible 是否显示
     * @param modules 待安装模块文件
     */
    data class DialogState(
        val visible: Boolean = false,
        val modules: List<ModuleInstallTarget> = emptyList(),
    )
}

/**
 * 本地模块安装确认对话框 Compose 组件
 *
 * @param state 对话框状态
 * @param context Context
 * @param onDismiss 关闭回调
 * @param onConfirm 确认安装回调
 * @param renderInRootScaffold 是否在根 Scaffold 中渲染，默认为 false
 */
@Composable
fun LocalModuleInstallDialog(
    state: LocalModuleInstallDialog.DialogState,
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    renderInRootScaffold: Boolean = false
) {
    if (!state.visible || state.modules.isEmpty()) return

    val summary = if (state.modules.size == 1) {
        context.getString(CoreR.string.confirm_install, state.modules.first().displayName)
    } else {
        val moduleNames = state.modules.mapIndexed { index, module ->
            "${index + 1}. ${module.displayName}"
        }.joinToString("\n")
        context.getString(CoreR.string.confirm_install_multiple, moduleNames)
    }

    OverlayDialog(
        show = state.visible,
        title = context.getString(CoreR.string.confirm_install_title),
        summary = summary,
        onDismissRequest = onDismiss,
        renderInRootScaffold = renderInRootScaffold
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
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}
