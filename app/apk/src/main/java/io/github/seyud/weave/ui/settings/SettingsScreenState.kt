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
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.utils.LocaleSetting
import io.github.seyud.weave.ui.superuser.isWhitelistMode
import io.github.seyud.weave.core.R as CoreR

internal data class SettingsVisibility(
    val showMagiskSection: Boolean,
    val showZygisk: Boolean,
    val isWhitelistMode: Boolean,
    val showDenyListControls: Boolean,
    val showSuperuserSection: Boolean,
    val showTapjack: Boolean,
    val showReauthenticate: Boolean,
    val showRestrict: Boolean,
)

@Composable
internal fun rememberSettingsVisibility(
    context: Context,
    currentSuperuserListMode: Int,
): SettingsVisibility {
    return remember(
        context.packageName,
        Build.VERSION.SDK_INT,
        Info.env.isActive,
        Info.showSuperUser,
        Info.isZygiskEnabled,
        currentSuperuserListMode,
        Const.USER_ID,
    ) {
        val showMagiskSection = Info.env.isActive
        val whitelistMode = isWhitelistMode(currentSuperuserListMode)
        SettingsVisibility(
            showMagiskSection = showMagiskSection,
            showZygisk = showMagiskSection && Const.Version.atLeast_24_0(),
            isWhitelistMode = whitelistMode,
            showDenyListControls = showMagiskSection && !whitelistMode,
            showSuperuserSection = Info.showSuperUser,
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
) {
    var updateChannelIndex by updateChannelIndexState
    var showCustomChannelDialog by showCustomChannelDialogState
    var customChannelUrl by customChannelUrlState
    var showModuleRepoDialog by showModuleRepoDialogState
    var moduleRepoBaseUrlInput by moduleRepoBaseUrlInputState
    var showDownloadPathDialog by showDownloadPathDialogState
    var downloadPathInput by downloadPathInputState
}

@Composable
internal fun rememberSettingsScreenLocalState(): SettingsScreenLocalState {
    val updateChannelIndexState = rememberSaveable { mutableIntStateOf(Config.updateChannelIndex) }
    val showCustomChannelDialogState = rememberSaveable { mutableStateOf(false) }
    val customChannelUrlState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(Config.customChannelUrl))
    }
    val showModuleRepoDialogState = rememberSaveable { mutableStateOf(false) }
    val moduleRepoBaseUrlInputState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(Config.moduleRepoUrl))
    }
    val showDownloadPathDialogState = rememberSaveable { mutableStateOf(false) }
    val downloadPathInputState = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(Config.downloadDir))
    }

    return remember(
        updateChannelIndexState,
        showCustomChannelDialogState,
        customChannelUrlState,
        showModuleRepoDialogState,
        moduleRepoBaseUrlInputState,
        showDownloadPathDialogState,
        downloadPathInputState,
    ) {
        SettingsScreenLocalState(
            updateChannelIndexState = updateChannelIndexState,
            showCustomChannelDialogState = showCustomChannelDialogState,
            customChannelUrlState = customChannelUrlState,
            showModuleRepoDialogState = showModuleRepoDialogState,
            moduleRepoBaseUrlInputState = moduleRepoBaseUrlInputState,
            showDownloadPathDialogState = showDownloadPathDialogState,
            downloadPathInputState = downloadPathInputState,
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
