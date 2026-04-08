package io.github.seyud.weave.dialog

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.seyud.weave.core.download.Subject
import io.github.seyud.weave.core.model.module.OnlineModule
import io.github.seyud.weave.ui.component.MarkdownText
import io.github.seyud.weave.ui.flash.FlashRequest
import io.github.seyud.weave.core.integration.AppNotifications
import kotlinx.parcelize.Parcelize
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.seyud.weave.core.R as CoreR

/**
 * 在线模块安装/更新对话框
 * 使用 Miuix Compose 对话框呈现更新日志和操作按钮
 */
object OnlineModuleInstallDialog {

    /**
     * 对话框状态
     *
     * @param visible 是否显示
     * @param module 在线模块信息
     * @param changelog Markdown 格式的更新日志内容（已加载完成）
     * @param isLoadingChangelog 是否正在加载更新日志
     * @param errorMessage 加载失败时的错误消息
     */
    data class DialogState(
        val visible: Boolean = false,
        val module: OnlineModule? = null,
        val changelog: String? = null,
        val isLoadingChangelog: Boolean = false,
        val errorMessage: String? = null
    )

    /**
     * 下载主题，供 DownloadEngine 使用
     */
    @Parcelize
    class Module(
        override val module: OnlineModule,
        override val autoLaunch: Boolean,
        override val notifyId: Int = AppNotifications.nextId()
    ) : Subject.Module() {
        override fun pendingIntent(context: Context) = FlashRequest.install(file).toPendingIntent(context)
    }
}

/**
 * 在线模块安装对话框 Compose 组件
 * 参考 KernelSU 的实现，使用 Miuix OverlayDialog 显示更新日志和操作按钮
 *
 * @param state 对话框状态
 * @param context Context
 * @param onDismiss 关闭回调
 * @param onDownload 下载（不安装）回调
 * @param onInstall 下载并安装回调
 */
@Composable
fun OnlineModuleInstallDialog(
    state: OnlineModuleInstallDialog.DialogState,
    context: Context,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    if (!state.visible || state.module == null) return

    val module = state.module
    val title = context.getString(
        CoreR.string.repo_install_title,
        module.name, module.version, module.versionCode
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val dialogMaxHeight = maxHeight * 0.9f
            val changelogMaxHeight = maxHeight * 0.62f

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MiuixTheme.colorScheme.background)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .heightIn(min = 320.dp)
                        .heightIn(max = dialogMaxHeight)
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.heightIn(min = 12.dp))

                    when {
                        state.isLoadingChangelog -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    size = 32.dp,
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                        state.errorMessage != null -> {
                            Text(
                                text = state.errorMessage,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        !state.changelog.isNullOrBlank() -> {
                            MarkdownText(
                                content = state.changelog,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = changelogMaxHeight)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.heightIn(min = 16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            text = context.getString(android.R.string.cancel),
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            text = context.getString(CoreR.string.download),
                            onClick = {
                                onDownload()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            text = context.getString(CoreR.string.install),
                            onClick = {
                                onInstall()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}
