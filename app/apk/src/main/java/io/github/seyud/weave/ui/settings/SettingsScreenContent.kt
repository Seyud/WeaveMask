package io.github.seyud.weave.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    currentSuperuserListMode: Int,
    isActive: Boolean,
    contentBottomPadding: Dp,
    nestedScrollConnection: NestedScrollConnection,
    onNavigateToAppLanguage: () -> Unit,
    onNavigateToColorPalette: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .scrollEndHaptic()
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(horizontal = 12.dp),
    ) {
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

        CustomizationSettingsSection(
            viewModel = viewModel,
            visibility = visibility,
            onNavigateToAppLanguage = onNavigateToAppLanguage,
            onNavigateToColorPalette = onNavigateToColorPalette,
            onAddShortcut = viewModel::addShortcut,
        )
        AppSettingsSection(
            state = localState,
            visibility = visibility,
        )
        MagiskSettingsSection(
            viewModel = viewModel,
            visibility = visibility,
        )
        SuperuserSettingsSection(
            viewModel = viewModel,
            visibility = visibility,
            currentSuperuserListMode = currentSuperuserListMode,
            isActive = isActive,
        )

        Spacer(Modifier.height(contentBottomPadding))
    }
}
