package io.github.seyud.weave.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import io.github.seyud.weave.core.R as CoreR

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentBottomPadding: Dp,
    isActive: Boolean,
    onNavigateToLog: () -> Unit,
    onNavigateToAppLanguage: () -> Unit,
    onNavigateToDenyListConfig: () -> Unit,
    onSuperuserModeChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val surfaceColor = colorScheme.surface
    val blurBackdrop = rememberBarBlurBackdrop(enableBlur, surfaceColor)
    val localState = rememberSettingsScreenLocalState()
    val visibility = rememberSettingsVisibility(context)

    DisposableEffect(viewModel, onNavigateToLog, onNavigateToDenyListConfig, onSuperuserModeChanged) {
        viewModel.onNavigateToLog = onNavigateToLog
        viewModel.onNavigateToDenyListConfig = onNavigateToDenyListConfig
        viewModel.onSuperuserModeChanged = onSuperuserModeChanged
        onDispose {
            viewModel.onNavigateToLog = null
            viewModel.onNavigateToDenyListConfig = null
            viewModel.onSuperuserModeChanged = null
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBarBlur(blurBackdrop, surfaceColor),
                color = barBlurContainerColor(blurBackdrop, surfaceColor),
                title = stringResource(CoreR.string.settings),
                scrollBehavior = scrollBehavior,
            )
        },
        popupHost = { },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .attachBarBlurBackdrop(blurBackdrop),
        ) {
            SettingsScreenContent(
                innerPadding = innerPadding,
                viewModel = viewModel,
                localState = localState,
                visibility = visibility,
                isActive = isActive,
                contentBottomPadding = contentBottomPadding,
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                onNavigateToAppLanguage = onNavigateToAppLanguage,
            )
        }
    }

    SettingsScreenDialogs(
        viewModel = viewModel,
        state = localState,
    )
}
