package io.github.seyud.weave.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import io.github.seyud.weave.core.R as CoreR

@Composable
internal fun SettingsScreenContent(
    innerPadding: PaddingValues,
    viewModel: SettingsViewModel,
    localState: SettingsScreenLocalState,
    visibility: SettingsVisibility,
    isActive: Boolean,
    contentBottomPadding: Dp,
    nestedScrollConnection: NestedScrollConnection,
    onNavigateToAppLanguage: () -> Unit,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(nestedScrollConnection)
            .padding(horizontal = 12.dp),
        contentPadding = innerPadding,
        overscrollEffect = null,
    ) {
        item(key = "logs_card", contentType = "header") {
            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = stringResource(CoreR.string.logs),
                    startAction = {
                        Icon(
                            Icons.Rounded.BugReport,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    },
                    onClick = { viewModel.onNavigateToLog?.invoke() },
                )
            }
        }

        item(key = "customization_section", contentType = "section") {
            CustomizationSettingsSection(
                viewModel = viewModel,
                visibility = visibility,
                onNavigateToAppLanguage = onNavigateToAppLanguage,
                onAddShortcut = viewModel::addShortcut,
            )
        }
        item(key = "app_section", contentType = "section") {
            AppSettingsSection(
                state = localState,
                visibility = visibility,
            )
        }
        item(key = "magisk_section", contentType = "section") {
            MagiskSettingsSection(
                viewModel = viewModel,
                visibility = visibility,
            )
        }
        item(key = "superuser_section", contentType = "section") {
            SuperuserSettingsSection(
                viewModel = viewModel,
                visibility = visibility,
                isActive = isActive,
            )
        }

        item(key = "bottom_spacer", contentType = "spacer") {
            Spacer(Modifier.height(contentBottomPadding))
        }
    }
}
