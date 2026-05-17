package io.github.seyud.weave.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.integration.AppIconManager
import io.github.seyud.weave.core.integration.AppIconVariant
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import io.github.seyud.weave.core.R as CoreR

@Composable
internal fun CustomizationSettingsSection(
    viewModel: SettingsViewModel,
    visibility: SettingsVisibility,
    onNavigateToAppLanguage: () -> Unit,
    onNavigateToColorPalette: () -> Unit,
    onAddShortcut: () -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    var homeLayoutMode by rememberSaveable { mutableIntStateOf(Config.homeLayoutMode) }
    val homeLayoutClassic = stringResource(CoreR.string.settings_home_layout_classic)
    val homeLayoutWeavsk = stringResource(CoreR.string.settings_home_layout_weavsk)
    val iconCurrent = stringResource(CoreR.string.settings_app_icon_current)
    val iconLegacyWeave = stringResource(CoreR.string.settings_app_icon_legacy_weave)
    val iconLegacyMask = stringResource(CoreR.string.settings_app_icon_legacy_mask)
    val homeLayoutItems = remember(homeLayoutClassic, homeLayoutWeavsk) {
        listOf(homeLayoutClassic, homeLayoutWeavsk)
    }
    val appIconVariants = remember { AppIconVariant.entries }
    val appIconItems = remember(iconCurrent, iconLegacyWeave, iconLegacyMask) {
        listOf(iconCurrent, iconLegacyWeave, iconLegacyMask)
    }
    var appIconIndex by rememberSaveable {
        mutableIntStateOf(appIconVariants.indexOf(AppIconManager.currentVariant()).coerceAtLeast(0))
    }
    val supportsAppIconSelection = remember(context.packageName) {
        AppIconManager.isSupported(context)
    }

    SmallTitle(text = stringResource(CoreR.string.settings_customization))
    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = stringResource(CoreR.string.settings_theme),
            summary = stringResource(CoreR.string.settings_theme_summary),
            startAction = {
                Icon(
                    Icons.Rounded.Palette,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
            onClick = onNavigateToColorPalette,
        )

        OverlayDropdownPreference(
            title = stringResource(CoreR.string.settings_home_layout),
            summary = stringResource(CoreR.string.settings_home_layout_summary),
            items = homeLayoutItems,
            startAction = {
                Icon(
                    Icons.Rounded.Home,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
            selectedIndex = homeLayoutMode.coerceIn(0, homeLayoutItems.lastIndex),
            onSelectedIndexChange = { index ->
                Config.homeLayoutMode = index
                homeLayoutMode = Config.homeLayoutMode
            },
        )

        if (supportsAppIconSelection) {
            OverlayDropdownPreference(
                title = stringResource(CoreR.string.settings_app_icon_title),
                summary = stringResource(CoreR.string.settings_app_icon_summary),
                items = appIconItems,
                startAction = {
                    Icon(
                        Icons.Rounded.Apps,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = null,
                        tint = colorScheme.onBackground,
                    )
                },
                selectedIndex = appIconIndex.coerceIn(0, appIconItems.lastIndex),
                onSelectedIndexChange = { index ->
                    val variant = appIconVariants[index]
                    if (viewModel.updateAppIcon(context, variant)) {
                        appIconIndex = index
                    }
                },
            )
        }

        ArrowPreference(
            title = stringResource(CoreR.string.language),
            summary = appLanguageSummary(res),
            startAction = {
                Icon(
                    Icons.Rounded.Language,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            },
            onClick = onNavigateToAppLanguage,
        )

        if (visibility.showAddShortcut) {
            ArrowPreference(
                title = stringResource(CoreR.string.add_shortcut_title),
                summary = stringResource(CoreR.string.setting_add_shortcut_summary),
                startAction = {
                    Icon(
                        Icons.AutoMirrored.Rounded.AddToHomeScreen,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = null,
                        tint = colorScheme.onBackground,
                    )
                },
                onClick = onAddShortcut,
            )
        }
    }
}
