package io.github.seyud.weave.ui.module.dialogs

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.core.download.DownloadEngine
import io.github.seyud.weave.core.ktx.getBitmap
import io.github.seyud.weave.dialog.LocalModuleInstallDialog
import io.github.seyud.weave.dialog.OnlineModuleInstallDialog
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.ui.module.ModuleInstallTarget
import io.github.seyud.weave.ui.module.ModuleShortcutState
import io.github.seyud.weave.ui.module.ShortcutType
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ModuleScreenDialogs(
    uiActivity: MainActivity?,
    onlineInstallDialogState: OnlineModuleInstallDialog.DialogState,
    localInstallDialogState: LocalModuleInstallDialog.DialogState,
    shortcutState: ModuleShortcutState,
    showShortcutDialog: Boolean,
    showShortcutTypeDialog: Boolean,
    onDismissOnlineInstallDialog: () -> Unit,
    onDismissLocalInstallDialog: () -> Unit,
    onConfirmLocalInstall: (List<Uri>) -> Unit,
    onDismissShortcutTypeDialog: () -> Unit,
    onSelectShortcutType: (ShortcutType) -> Unit,
    onDismissShortcutDialog: () -> Unit,
    onPickShortcutIcon: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    OnlineModuleInstallDialog(
        state = onlineInstallDialogState,
        context = context,
        onDismiss = onDismissOnlineInstallDialog,
        onDownload = {
            val module = onlineInstallDialogState.module ?: return@OnlineModuleInstallDialog
            val subject = OnlineModuleInstallDialog.Module(module, false)
            uiActivity?.let { DownloadEngine.startWithActivity(it, subject) }
        },
        onInstall = {
            val module = onlineInstallDialogState.module ?: return@OnlineModuleInstallDialog
            val subject = OnlineModuleInstallDialog.Module(module, true)
            uiActivity?.let { DownloadEngine.startWithActivity(it, subject) }
        },
    )

    LocalModuleInstallDialog(
        state = localInstallDialogState,
        context = context,
        onDismiss = onDismissLocalInstallDialog,
        onConfirm = {
            val uris = localInstallDialogState.modules.map(ModuleInstallTarget::uri)
            if (uris.isEmpty()) return@LocalModuleInstallDialog
            onDismissLocalInstallDialog()
            onConfirmLocalInstall(uris)
        },
    )

    ModuleShortcutTypeDialog(
        show = showShortcutTypeDialog,
        shortcutState = shortcutState,
        onDismiss = onDismissShortcutTypeDialog,
        onSelectType = onSelectShortcutType,
    )

    ModuleShortcutDialog(
        show = showShortcutDialog,
        shortcutState = shortcutState,
        onDismiss = onDismissShortcutDialog,
        onPickShortcutIcon = onPickShortcutIcon,
        onDeleteShortcut = {
            shortcutState.deleteShortcut(context)
            onDismissShortcutDialog()
        },
        onConfirmShortcut = {
            scope.launch {
                if (shortcutState.createShortcut(context)) {
                    onDismissShortcutDialog()
                }
            }
        },
    )
}

@Composable
private fun ModuleShortcutTypeDialog(
    show: Boolean,
    shortcutState: ModuleShortcutState,
    onDismiss: () -> Unit,
    onSelectType: (ShortcutType) -> Unit,
) {
    if (!show) return

    OverlayDialog(
        show = show,
        title = stringResource(CoreR.string.module_shortcut_type_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (shortcutState.supportsActionShortcut) {
                TextButton(
                    text = stringResource(CoreR.string.module_action),
                    onClick = { onSelectType(ShortcutType.Action) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
            if (shortcutState.supportsWebUiShortcut) {
                TextButton(
                    text = stringResource(CoreR.string.module_webui),
                    onClick = { onSelectType(ShortcutType.WebUI) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ModuleShortcutDialog(
    show: Boolean,
    shortcutState: ModuleShortcutState,
    onDismiss: () -> Unit,
    onPickShortcutIcon: () -> Unit,
    onDeleteShortcut: () -> Unit,
    onConfirmShortcut: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val fallbackIcon = remember(context) {
        runCatching { context.getBitmap(CoreR.drawable.ic_launcher).asImageBitmap() }.getOrNull()
    }

    OverlayDialog(
        show = show,
        title = stringResource(CoreR.string.module_shortcut_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                val preview = shortcutState.previewIcon
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    fallbackIcon?.let { icon ->
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(CoreR.string.module_shortcut_icon_pick),
                    onClick = onPickShortcutIcon,
                    modifier = Modifier.weight(1f),
                )
                if (shortcutState.iconUri != shortcutState.defaultShortcutIconUri) {
                    TextButton(
                        text = stringResource(CoreR.string.module_shortcut_icon_reset),
                        onClick = shortcutState::resetIconToDefault,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            TextField(
                value = shortcutState.name,
                onValueChange = shortcutState::updateName,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(CoreR.string.module_shortcut_name_label),
                singleLine = true,
                useLabelAsPlaceholder = true,
            )

            if (shortcutState.hasExistingShortcut) {
                TextButton(
                    text = stringResource(CoreR.string.module_shortcut_delete),
                    onClick = onDeleteShortcut,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(
                        if (shortcutState.hasExistingShortcut) {
                            CoreR.string.update
                        } else {
                            CoreR.string.module_shortcut_add
                        }
                    ),
                    onClick = onConfirmShortcut,
                    enabled = shortcutState.name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
