package io.github.seyud.weave.ui.home

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.core.base.IActivityExtension
import io.github.seyud.weave.core.download.DownloadEngine
import io.github.seyud.weave.core.download.Subject
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.dialog.EnvFixDialog
import io.github.seyud.weave.dialog.UninstallDialog
import io.github.seyud.weave.ui.component.MarkdownText
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
internal fun HomeDialogHost(
    viewModel: HomeViewModel,
    onNavigateToInstall: () -> Unit,
    onNavigateToUninstall: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    EnvFixDialog(
        state = viewModel.envFixDialogState,
        context = context,
        onDismiss = { viewModel.dismissEnvFixDialog() },
        onNavigateToInstall = {
            viewModel.dismissEnvFixDialog()
            onNavigateToInstall()
        }
    )

    UninstallDialog(
        state = viewModel.uninstallDialogState,
        context = context,
        onDismiss = { viewModel.dismissUninstallDialog() },
        onRestoreImg = {
            viewModel.startRestoreImg()
            coroutineScope.launch {
                io.github.seyud.weave.core.tasks.MagiskInstaller.Restore().exec { success ->
                    viewModel.dismissUninstallDialog()
                    context.toast(
                        if (success) CoreR.string.restore_done else CoreR.string.restore_fail,
                        if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    )
                }
            }
        },
        onCompleteUninstall = {
            viewModel.dismissUninstallDialog()
            onNavigateToUninstall()
        }
    )

    ManagerInstallDialog(
        show = viewModel.managerInstallDialogState.visible,
        title = context.getString(CoreR.string.home_app_title),
        version = viewModel.managerInstallDialogState.version,
        releaseNotes = viewModel.managerInstallDialogState.releaseNotes,
        installEnabled = viewModel.managerInstallDialogState.installEnabled,
        onDismiss = { viewModel.dismissManagerInstallDialog() },
        onInstall = {
            val updateInfo = viewModel.managerInstallDialogState.updateInfo
            if (!viewModel.managerInstallDialogState.installEnabled || updateInfo == null) {
                context.toast(CoreR.string.no_connection, Toast.LENGTH_SHORT)
                viewModel.dismissManagerInstallDialog()
                return@ManagerInstallDialog
            }
            val activity = context as? ComponentActivity
            if (activity is IActivityExtension) {
                DownloadEngine.startWithActivity(activity, Subject.App(updateInfo))
            } else {
                DownloadEngine.start(context.applicationContext, Subject.App(updateInfo))
            }
            viewModel.dismissManagerInstallDialog()
        }
    )
}

@Composable
internal fun ManagerInstallDialog(
    show: Boolean,
    title: String,
    version: String,
    releaseNotes: String,
    installEnabled: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    if (!show) return

    OverlayDialog(
        show = show,
        title = title,
        summary = version,
        onDismissRequest = onDismiss
    ) {
        MarkdownText(
            content = releaseNotes.ifBlank { version },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = LocalContext.current.getString(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(20.dp))
            TextButton(
                text = LocalContext.current.getString(CoreR.string.install),
                onClick = onInstall,
                modifier = Modifier.weight(1f),
                enabled = installEnabled,
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}
