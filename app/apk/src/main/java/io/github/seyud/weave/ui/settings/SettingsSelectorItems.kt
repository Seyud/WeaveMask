package io.github.seyud.weave.ui.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Update
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.ui.icon.SuperuserIcon
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import io.github.seyud.weave.core.R as CoreR

@Composable
internal fun UpdateChannelSelectorItem(
    res: Resources,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val entries = remember(res) { res.getStringArray(CoreR.array.update_channel) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.settings_update_channel_title),
        items = entries.toList(),
        selectedIndex = selectedIndex.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = onSelectedIndexChange,
        startAction = {
            Icon(
                Icons.Rounded.Update,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
        },
    )
}

@Composable
internal fun AccessModeSelectorItem(res: Resources) {
    val entries = remember(res) { res.getStringArray(CoreR.array.su_access) }
    var selected by rememberSaveable { mutableIntStateOf(Config.rootMode) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.superuser_access),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.rootMode = index
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.AdminPanelSettings,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
        },
    )
}

@Composable
internal fun SuperuserModeSelectorItem(res: Resources) {
    val entries = remember(res) { res.getStringArray(CoreR.array.su_list_mode) }
    var selected by rememberSaveable {
        mutableIntStateOf(
            Config.suListMode.coerceIn(0, entries.size - 1),
        )
    }
    val summary = when (selected) {
        Config.Value.SU_MODE_BLACKLIST -> stringResource(CoreR.string.settings_su_mode_summary_blacklist)
        else -> stringResource(CoreR.string.settings_su_mode_summary_whitelist)
    }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.settings_su_mode_title),
        summary = summary,
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.suListMode = index
            selected = index
        },
        startAction = {
            Icon(
                SuperuserIcon,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
        },
    )
}

@Composable
internal fun MultiuserModeSelectorItem(res: Resources) {
    val entries = remember(res) { res.getStringArray(CoreR.array.multiuser_mode) }
    val summaries = remember(res) { res.getStringArray(CoreR.array.multiuser_summary) }
    var selected by rememberSaveable { mutableIntStateOf(Config.suMultiuserMode) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.multiuser_mode),
        summary = summaries.getOrElse(selected) { "" },
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.suMultiuserMode = index
            selected = index
        },
        enabled = Const.USER_ID == 0,
        startAction = {
            Icon(
                Icons.Rounded.Group,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
        },
    )
}

@Composable
internal fun MountNamespaceModeSelectorItem(res: Resources) {
    val entries = remember(res) { res.getStringArray(CoreR.array.namespace) }
    val summaries = remember(res) { res.getStringArray(CoreR.array.namespace_summary) }
    var selected by rememberSaveable { mutableIntStateOf(Config.suMntNamespaceMode) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.mount_namespace_mode),
        summary = summaries.getOrElse(selected) { "" },
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.suMntNamespaceMode = index
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.FolderSpecial,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
        },
    )
}

@Composable
internal fun AutomaticResponseSelectorItem(
    res: Resources,
    viewModel: SettingsViewModel,
) {
    val entries = remember(res) { res.getStringArray(CoreR.array.auto_response) }
    var selected by rememberSaveable { mutableIntStateOf(Config.suAutoResponse) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.auto_response),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            if (Config.suAuth) {
                viewModel.authenticate { success ->
                    if (success) {
                        Config.suAutoResponse = index
                        selected = index
                    }
                }
            } else {
                Config.suAutoResponse = index
                selected = index
            }
        },
        startAction = {
            Icon(
                Icons.Rounded.QuestionAnswer,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
        },
    )
}

@Composable
internal fun RequestTimeoutSelectorItem(res: Resources) {
    val entries = remember(res) { res.getStringArray(CoreR.array.request_timeout) }
    val entryValues = remember { listOf(10, 15, 20, 30, 45, 60) }
    var selected by rememberSaveable {
        mutableIntStateOf(
            entryValues.indexOfFirst { it == Config.suDefaultTimeout }.coerceAtLeast(0),
        )
    }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.request_timeout),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.suDefaultTimeout = entryValues[index]
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.Timer,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
        },
    )
}

@Composable
internal fun SUNotificationSelectorItem(res: Resources) {
    val entries = remember(res) { res.getStringArray(CoreR.array.su_notification) }
    var selected by rememberSaveable { mutableIntStateOf(Config.suNotification) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.superuser_notification),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.suNotification = index
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.Notifications,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
        },
    )
}
