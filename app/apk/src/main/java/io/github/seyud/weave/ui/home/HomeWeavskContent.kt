package io.github.seyud.weave.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.platform.LocalContext
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.R as CoreR

internal fun LazyListScope.weavskHomeContent(
    viewModel: HomeViewModel,
    magiskInstalledVersion: String,
    isMagiskCardExpanded: Boolean,
    onMagiskExpandedChange: (Boolean) -> Unit,
    isSupportCardExpanded: Boolean,
    onSupportExpandedChange: (Boolean) -> Unit,
    onNavigateToInstall: () -> Unit,
    onNavigateToAbout: () -> Unit,
    cardActionEnter: EnterTransition,
    cardActionExit: ExitTransition,
) {
    item {
        val context = LocalContext.current
        Column {
            WeavskMagiskCard(
                magiskState = viewModel.magiskState,
                installedVersion = magiskInstalledVersion,
                expanded = isMagiskCardExpanded,
                onCardClick = { onMagiskExpandedChange(!isMagiskCardExpanded) },
                onInstallClick = onNavigateToInstall
            )

            AnimatedVisibility(
                visible = isMagiskCardExpanded,
                enter = cardActionEnter,
                exit = cardActionExit
            ) {
                Column {
                    InstallActionButton(
                        appState = viewModel.magiskState,
                        matchUninstallMetrics = true,
                        onClick = onNavigateToInstall
                    )
                    if (Info.env.isActive) {
                        UninstallButton(
                            text = context.getString(CoreR.string.home_uninstall_weavemask),
                            onPressed = { viewModel.onDeletePressed() }
                        )
                    }
                }
            }
        }
    }

    item {
        WeavskStatusCard(
            appState = viewModel.appState,
            installedVersion = viewModel.managerInstalledVersion,
            packageName = viewModel.managerPackageName,
            progress = viewModel.stateManagerProgress
        )
    }

    item {
        SupportCard(
            expanded = isSupportCardExpanded,
            onExpandedChange = onSupportExpandedChange,
            onLinkPressed = { link ->
                viewModel.onLinkPressed(link)
            }
        )
    }

    item {
        FollowCard(
            onAboutClick = onNavigateToAbout,
            onLinkPressed = { link ->
                viewModel.onLinkPressed(link)
            }
        )
    }
}
