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
            ?: stringResource(CoreR.string.module_repo_source_invalid),
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
        helperText = stringResource(
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
