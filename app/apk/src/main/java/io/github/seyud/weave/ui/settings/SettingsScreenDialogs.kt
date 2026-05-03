package io.github.seyud.weave.ui.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.utils.MediaStoreUtils
import io.github.seyud.weave.ui.component.MiuixConfirmDialog
import io.github.seyud.weave.ui.component.MiuixTextInputDialog
import kotlinx.coroutines.launch
import io.github.seyud.weave.core.R as CoreR

@Composable
internal fun SettingsScreenDialogs(
    viewModel: SettingsViewModel,
    state: SettingsScreenLocalState,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsLabel = stringResource(CoreR.string.settings)

    HideAppDialog(
        show = state.showHideDialog,
        appName = state.hideAppName,
        onAppNameChange = { state.hideAppName = it },
        onDismissRequest = {
            if (!state.isHideInProgress) {
                state.showHideDialog = false
            }
        },
        onConfirm = {
            val newName = state.hideAppName.text.ifBlank { settingsLabel }
            coroutineScope.launch {
                state.isHideInProgress = true
                val success = viewModel.hideApp(context, newName)
                state.isHideInProgress = false
                if (success) {
                    state.showHideDialog = false
                } else {
                    context.toast(CoreR.string.failure, Toast.LENGTH_LONG)
                }
            }
        },
    )

    HideAppLoadingDialog(
        show = state.isHideInProgress,
        title = stringResource(CoreR.string.hide_app_title),
    )

    HideAppLoadingDialog(
        show = state.isRestoreInProgress,
        title = stringResource(CoreR.string.restore_img_msg),
    )

    MiuixConfirmDialog(
        show = state.showRestoreConfirmDialog,
        title = stringResource(CoreR.string.settings_restore_app_title),
        summary = stringResource(CoreR.string.restore_app_confirmation),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = {
            if (!state.isRestoreInProgress) {
                state.showRestoreConfirmDialog = false
            }
        },
        onConfirm = {
            if (!state.isRestoreInProgress) {
                state.showRestoreConfirmDialog = false
                coroutineScope.launch {
                    state.isRestoreInProgress = true
                    val success = viewModel.restoreApp(context)
                    state.isRestoreInProgress = false
                    if (!success) {
                        context.toast(CoreR.string.failure, Toast.LENGTH_LONG)
                    }
                }
            }
        },
    )

    MiuixTextInputDialog(
        show = state.showCustomChannelDialog,
        title = stringResource(CoreR.string.settings_update_custom),
        value = state.customChannelUrl,
        onValueChange = { state.customChannelUrl = it },
        label = stringResource(CoreR.string.settings_update_custom_msg),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = { state.showCustomChannelDialog = false },
        onConfirm = {
            Config.customChannelUrl = state.customChannelUrl.text
            Info.resetUpdate()
            state.showCustomChannelDialog = false
        },
        confirmEnabled = true,
    )

    MiuixTextInputDialog(
        show = state.showModuleRepoDialog,
        title = stringResource(CoreR.string.module_repo_source_title),
        value = state.moduleRepoBaseUrlInput,
        onValueChange = { state.moduleRepoBaseUrlInput = it },
        label = stringResource(CoreR.string.module_repo_source_message),
        helperText = Config.normalizeModuleRepoBaseUrl(state.moduleRepoBaseUrlInput.text)
            ?: context.getString(CoreR.string.module_repo_source_invalid),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = { state.showModuleRepoDialog = false },
        onConfirm = {
            Config.moduleRepoBaseUrl = state.moduleRepoBaseUrlInput.text
            state.showModuleRepoDialog = false
        },
        confirmEnabled = Config.normalizeModuleRepoBaseUrl(state.moduleRepoBaseUrlInput.text) != null,
    )

    MiuixTextInputDialog(
        show = state.showDownloadPathDialog,
        title = stringResource(CoreR.string.settings_download_path_title),
        value = state.downloadPathInput,
        onValueChange = { state.downloadPathInput = it },
        label = stringResource(CoreR.string.settings_download_path_title),
        helperText = context.getString(
            CoreR.string.settings_download_path_message,
            MediaStoreUtils.fullPath(Config.downloadDir),
        ),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = { state.showDownloadPathDialog = false },
        onConfirm = {
            Config.downloadDir = state.downloadPathInput.text
            state.showDownloadPathDialog = false
        },
        confirmEnabled = true,
    )
}
