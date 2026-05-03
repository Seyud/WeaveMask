package io.github.seyud.weave.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.utils.MediaStoreUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import io.github.seyud.weave.core.R as CoreR

@Composable
internal fun AppSettingsSection(
    state: SettingsScreenLocalState,
    visibility: SettingsVisibility,
) {
    val context = LocalContext.current
    val settingsLabel = stringResource(CoreR.string.settings)
    val res = context.resources
    var dohEnabled by rememberSaveable { mutableStateOf(Config.doh) }
    var checkUpdateEnabled by rememberSaveable { mutableStateOf(Config.checkUpdate) }
    var randNameEnabled by rememberSaveable { mutableStateOf(Config.randName) }

    SmallTitle(text = stringResource(CoreR.string.home_app_title))
    Card(modifier = Modifier.fillMaxWidth()) {
        UpdateChannelSelectorItem(
            res = res,
            selectedIndex = state.updateChannelIndex,
            onSelectedIndexChange = { index ->
                Config.updateChannel = index
                Info.resetUpdate()
                state.updateChannelIndex = index
            },
        )

        AnimatedVisibility(visible = state.updateChannelIndex == Config.Value.CUSTOM_CHANNEL) {
            ArrowPreference(
                title = stringResource(CoreR.string.settings_update_custom),
                summary = UpdateChannelUrl.getDescription(res),
                startAction = {
                    Icon(
                        Icons.Rounded.Link,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = null,
                        tint = colorScheme.onBackground,
                    )
                },
                onClick = {
                    state.customChannelUrl = TextFieldValue(Config.customChannelUrl)
                    state.showCustomChannelDialog = true
                },
            )
        }

        SwitchPreference(
            title = stringResource(CoreR.string.settings_doh_title),
            summary = stringResource(CoreR.string.settings_doh_description),
            checked = dohEnabled,
            onCheckedChange = {
                Config.doh = it
                dohEnabled = it
            },
            startAction = {
                Icon(
                    Icons.Rounded.Dns,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
        )

        SwitchPreference(
            title = stringResource(CoreR.string.settings_check_update_title),
            summary = stringResource(CoreR.string.settings_check_update_summary),
            checked = checkUpdateEnabled,
            onCheckedChange = {
                Config.checkUpdate = it
                checkUpdateEnabled = it
            },
            startAction = {
                Icon(
                    Icons.Rounded.SystemUpdate,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
        )

        ArrowPreference(
            title = stringResource(CoreR.string.module_repo_source_title),
            summary = Config.moduleRepoBaseUrl,
            startAction = {
                Icon(
                    Icons.Rounded.Link,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
            onClick = {
                state.moduleRepoBaseUrlInput = TextFieldValue(Config.moduleRepoBaseUrl)
                state.showModuleRepoDialog = true
            },
        )

        ArrowPreference(
            title = stringResource(CoreR.string.settings_download_path_title),
            summary = MediaStoreUtils.fullPath(Config.downloadDir).takeIf { it.isNotEmpty() },
            startAction = {
                Icon(
                    Icons.Rounded.FolderOpen,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
            onClick = {
                state.downloadPathInput = TextFieldValue(Config.downloadDir)
                state.showDownloadPathDialog = true
            },
        )

        SwitchPreference(
            title = stringResource(CoreR.string.settings_random_name_title),
            summary = stringResource(CoreR.string.settings_random_name_description),
            checked = randNameEnabled,
            onCheckedChange = {
                Config.randName = it
                randNameEnabled = it
            },
            startAction = {
                Icon(
                    Icons.Rounded.Shuffle,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
        )

        if (visibility.showHideRestore) {
            if (visibility.hidden) {
                ArrowPreference(
                    title = stringResource(CoreR.string.settings_restore_app_title),
                    summary = stringResource(CoreR.string.settings_restore_app_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.Restore,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    },
                    onClick = {
                        if (!state.isRestoreInProgress) {
                            state.showRestoreConfirmDialog = true
                        }
                    },
                )
            } else {
                ArrowPreference(
                    title = stringResource(CoreR.string.settings_hide_app_title),
                    summary = stringResource(CoreR.string.settings_hide_app_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.VisibilityOff,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    },
                    onClick = {
                        state.hideAppName = TextFieldValue(settingsLabel)
                        state.showHideDialog = true
                    },
                )
            }
        }
    }
}
