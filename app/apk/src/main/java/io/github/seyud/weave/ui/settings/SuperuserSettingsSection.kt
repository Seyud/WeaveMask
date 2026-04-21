package io.github.seyud.weave.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppBlocking
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Security
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Info
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import io.github.seyud.weave.core.R as CoreR

@Composable
internal fun SuperuserSettingsSection(
    viewModel: SettingsViewModel,
    visibility: SettingsVisibility,
) {
    if (!visibility.showSuperuserSection) {
        return
    }

    val res = LocalContext.current.resources

    SmallTitle(text = stringResource(CoreR.string.superuser))
    Card(modifier = Modifier.fillMaxWidth()) {
        SuperuserModeSelectorItem(res = res)

        if (visibility.showTapjack) {
            var tapjackEnabled by rememberSaveable { mutableStateOf(Config.suTapjack) }
            SwitchPreference(
                title = stringResource(CoreR.string.settings_su_tapjack_title),
                summary = stringResource(CoreR.string.settings_su_tapjack_summary),
                checked = tapjackEnabled,
                onCheckedChange = {
                    Config.suTapjack = it
                    tapjackEnabled = it
                },
                startAction = {
                    Icon(
                        Icons.Rounded.Security,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = null,
                        tint = colorScheme.onBackground,
                    )
                },
            )
        }

        var authEnabled by rememberSaveable { mutableStateOf(Config.suAuth) }
        val authEnabledState = remember { mutableStateOf(Info.isDeviceSecure) }
        val authSummary = if (authEnabledState.value) {
            stringResource(CoreR.string.settings_su_auth_summary)
        } else {
            stringResource(CoreR.string.settings_su_auth_insecure)
        }
        SwitchPreference(
            title = stringResource(CoreR.string.settings_su_auth_title),
            summary = authSummary,
            checked = authEnabled,
            onCheckedChange = { checked ->
                viewModel.authenticateAndToggle(checked) { success ->
                    if (success) {
                        Config.suAuth = checked
                        authEnabled = checked
                    }
                }
            },
            enabled = authEnabledState.value,
            startAction = {
                Icon(
                    Icons.Rounded.Fingerprint,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
        )

        AccessModeSelectorItem(res = res)
        MultiuserModeSelectorItem(res = res)
        MountNamespaceModeSelectorItem(res = res)
        AutomaticResponseSelectorItem(res = res, viewModel = viewModel)
        RequestTimeoutSelectorItem(res = res)
        SUNotificationSelectorItem(res = res)

        if (visibility.showReauthenticate) {
            var reauthEnabled by rememberSaveable { mutableStateOf(Config.suReAuth) }
            SwitchPreference(
                title = stringResource(CoreR.string.settings_su_reauth_title),
                summary = stringResource(CoreR.string.settings_su_reauth_summary),
                checked = reauthEnabled,
                onCheckedChange = {
                    Config.suReAuth = it
                    reauthEnabled = it
                },
                startAction = {
                    Icon(
                        Icons.Rounded.LockReset,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = null,
                        tint = colorScheme.onBackground,
                    )
                },
            )
        }

        if (visibility.showRestrict) {
            var restrictEnabled by rememberSaveable { mutableStateOf(Config.suRestrict) }
            SwitchPreference(
                title = stringResource(CoreR.string.settings_su_restrict_title),
                summary = stringResource(CoreR.string.settings_su_restrict_summary),
                checked = restrictEnabled,
                onCheckedChange = {
                    Config.suRestrict = it
                    restrictEnabled = it
                },
                startAction = {
                    Icon(
                        Icons.Rounded.AppBlocking,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = null,
                        tint = colorScheme.onBackground,
                    )
                },
            )
        }
    }
}
