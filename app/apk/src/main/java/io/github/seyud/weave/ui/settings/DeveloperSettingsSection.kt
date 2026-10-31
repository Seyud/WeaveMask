package io.github.seyud.weave.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.Config
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import io.github.seyud.weave.core.R as CoreR

/**
 * Developer switches for module WebUI debugging.
 *
 * Two complementary capabilities, ported from the two upstream implementations:
 * - `enableWebUiDebugging`: KernelSU manager's `WebView.setWebContentsDebuggingEnabled`,
 *   i.e. full DevTools over `chrome://inspect` from a desktop Chrome.
 * - `enableWebUiConsole`: KsuWebUIStandalone's eruda injection, an on-device devtools
 *   panel (console, elements, network) that needs no USB connection.
 */
@Composable
internal fun DeveloperSettingsSection() {
    var webUiDebuggingEnabled by rememberSaveable { mutableStateOf(Config.enableWebUiDebugging) }
    var webUiConsoleEnabled by rememberSaveable { mutableStateOf(Config.enableWebUiConsole) }

    SmallTitle(text = stringResource(CoreR.string.settings_developer_title))
    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = stringResource(CoreR.string.settings_webui_debug_title),
            summary = stringResource(CoreR.string.settings_webui_debug_summary),
            checked = webUiDebuggingEnabled,
            onCheckedChange = {
                Config.enableWebUiDebugging = it
                webUiDebuggingEnabled = it
            },
            startAction = {
                Icon(
                    Icons.Rounded.BugReport,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
        )

        SwitchPreference(
            title = stringResource(CoreR.string.settings_webui_console_title),
            summary = stringResource(CoreR.string.settings_webui_console_summary),
            checked = webUiConsoleEnabled,
            onCheckedChange = {
                Config.enableWebUiConsole = it
                webUiConsoleEnabled = it
            },
            startAction = {
                Icon(
                    Icons.Rounded.Terminal,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
        )
    }
}
