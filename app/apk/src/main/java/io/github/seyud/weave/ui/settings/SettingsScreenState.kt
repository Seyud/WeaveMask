package io.github.seyud.weave.ui.settings

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.pm.ShortcutManagerCompat
import io.github.seyud.weave.core.BuildConfig
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.isRunningAsStub
import io.github.seyud.weave.core.utils.LocaleSetting
import io.github.seyud.weave.core.R as CoreR

internal data class SettingsVisibility(
    val hidden: Boolean,
    val showAddShortcut: Boolean,
    val showMagiskSection: Boolean,
    val showZygisk: Boolean,
    val showSuperuserSection: Boolean,
    val showHideRestore: Boolean,
    val showTapjack: Boolean,
    val showReauthenticate: Boolean,
    val showRestrict: Boolean,
)

@Composable
internal fun rememberSettingsVisibility(context: Context): SettingsVisibility {
    return remember(
        context.packageName,
        Build.VERSION.SDK_INT,
        Info.env.isActive,
        Info.showSuperUser,
        Info.isZygiskEnabled,
        Const.USER_ID,
    ) {
        val showMagiskSection = Info.env.isActive
        SettingsVisibility(
            hidden = context.packageName != BuildConfig.APP_PACKAGE_NAME,
            showAddShortcut = isRunningAsStub &&
                ShortcutManagerCompat.isRequestPinShortcutSupported(context),
            showMagiskSection = showMagiskSection,
            showZygisk = showMagiskSection && Const.Version.atLeast_24_0(),
            showSuperuserSection = Info.showSuperUser,
            showHideRestore = Info.env.isActive && Const.USER_ID == 0,
            showTapjack = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
            showReauthenticate = Build.VERSION.SDK_INT < Build.VERSION_CODES.O,
            showRestrict = Const.Version.atLeast_30_1(),
        )
    }
}

@Stable
internal class SettingsScreenLocalState(
    private val updateChannelIndexState: MutableIntState,
    private val showCustomChannelDialogState: MutableState<Boolean>,
    private val customChannelUrlState: MutableState<TextFieldValue>,
    private val showModuleRepoDialogState: MutableState<Boolean>,
    private val moduleRepoBaseUrlInputState: MutableState<TextFieldValue>,
    private val showDownloadPathDialogState: MutableState<Boolean>,
    private val downloadPathInputState: MutableState<TextFieldValue>,
    private val showHideDialogState: MutableState<Boolean>,
    private val showRestoreConfirmDialogState: MutableState<Boolean>,
    private val hideAppNameState: MutableState<TextFieldValue>,
    private val isHideInProgressState: MutableState<Boolean>,
    private val isRestoreInProgressState: MutableState<Boolean>,
) {
    var updateChannelIndex by updateChannelIndexState
    var showCustomChannelDialog by showCustomChannelDialogState
    var customChannelUrl by customChannelUrlState
    var showModuleRepoDialog by showModuleRepoDialogState
    var moduleRepoBaseUrlInput by moduleRepoBaseUrlInputState
    var showDownloadPathDialog by showDownloadPathDialogState
    var downloadPathInput by downloadPathInputState
    var showHideDialog by showHideDialogState
    var showRestoreConfirmDialog by showRestoreConfirmDialogState
    var hideAppName by hideAppNameState
    var isHideInProgress by isHideInProgressState
    var isRestoreInProgress by isRestoreInProgressState
}

@Composable
internal fun rememberSettingsScreenLocalState(): SettingsScreenLocalState {
    val updateChannelIndexState = rememberSaveable { mutableIntStateOf(Config.updateChannel) }
    val showCustomChannelDialogState = rememberSaveable { mutableStateOf(false) }
    val customChannelUrlState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(Config.customChannelUrl))
    }
    val showModuleRepoDialogState = rememberSaveable { mutableStateOf(false) }
    val moduleRepoBaseUrlInputState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(Config.moduleRepoBaseUrl))
    }
    val showDownloadPathDialogState = rememberSaveable { mutableStateOf(false) }
    val downloadPathInputState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(Config.downloadDir))
    }
    val showHideDialogState = rememberSaveable { mutableStateOf(false) }
    val showRestoreConfirmDialogState = rememberSaveable { mutableStateOf(false) }
    val settingsLabel = stringResource(CoreR.string.settings)
    val hideAppNameState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(settingsLabel))
    }
    val isHideInProgressState = rememberSaveable { mutableStateOf(false) }
    val isRestoreInProgressState = rememberSaveable { mutableStateOf(false) }

    return remember(
        updateChannelIndexState,
        showCustomChannelDialogState,
        customChannelUrlState,
        showModuleRepoDialogState,
        moduleRepoBaseUrlInputState,
        showDownloadPathDialogState,
        downloadPathInputState,
        showHideDialogState,
        showRestoreConfirmDialogState,
        hideAppNameState,
        isHideInProgressState,
        isRestoreInProgressState,
    ) {
        SettingsScreenLocalState(
            updateChannelIndexState = updateChannelIndexState,
            showCustomChannelDialogState = showCustomChannelDialogState,
            customChannelUrlState = customChannelUrlState,
            showModuleRepoDialogState = showModuleRepoDialogState,
            moduleRepoBaseUrlInputState = moduleRepoBaseUrlInputState,
            showDownloadPathDialogState = showDownloadPathDialogState,
            downloadPathInputState = downloadPathInputState,
            showHideDialogState = showHideDialogState,
            showRestoreConfirmDialogState = showRestoreConfirmDialogState,
            hideAppNameState = hideAppNameState,
            isHideInProgressState = isHideInProgressState,
            isRestoreInProgressState = isRestoreInProgressState,
        )
    }
}

internal fun appLanguageSummary(res: Resources): String {
    val locale = LocaleSetting.instance.appLocale
    return if (locale != null) {
        locale.getDisplayName(locale)
    } else {
        res.getString(CoreR.string.system_default)
    }
}
