package io.github.seyud.weave.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.R as CoreR

internal fun LazyListScope.classicHomeContent(
    viewModel: HomeViewModel,
    magiskInstalledVersion: String,
    managerRemoteVersion: String,
    isMagiskCardExpanded: Boolean,
    onMagiskExpandedChange: (Boolean) -> Unit,
    isManagerCardExpanded: Boolean,
    onManagerExpandedChange: (Boolean) -> Unit,
    onNavigateToInstall: () -> Unit,
    cardActionEnter: EnterTransition,
    cardActionExit: ExitTransition,
) {
    item {
        val context = LocalContext.current
        Column {
            ClassicMagiskCard(
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
                        usePrimaryBlockStyleWhenNonMonet = true,
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
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ZygiskCard(
                isEnabled = Info.isZygiskEnabled,
                modifier = Modifier.weight(1f)
            )
            RamdiskCard(
                isAvailable = Info.ramdisk,
                modifier = Modifier.weight(1f)
            )
        }
    }

    item {
        Column {
            ManagerCard(
                appState = viewModel.appState,
                remoteVersion = managerRemoteVersion,
                installedVersion = viewModel.managerInstalledVersion,
                packageName = viewModel.managerPackageName,
                progress = viewModel.stateManagerProgress,
                expanded = isManagerCardExpanded,
                onCardClick = { onManagerExpandedChange(!isManagerCardExpanded) },
                onInstallClick = { viewModel.onManagerPressed() }
            )

            AnimatedVisibility(
                visible = isManagerCardExpanded &&
                    viewModel.appState != HomeViewModel.State.LOADING &&
                    viewModel.appState != HomeViewModel.State.INVALID,
                enter = cardActionEnter,
                exit = cardActionExit
            ) {
                InstallActionButton(
                    appState = viewModel.appState,
                    onClick = { viewModel.onManagerPressed() }
                )
            }
        }
    }

    item {
        SupportCard(
            onLinkPressed = { link ->
                viewModel.onLinkPressed(link)
            }
        )
    }

    item {
        FollowCard(
            onLinkPressed = { link ->
                viewModel.onLinkPressed(link)
            }
        )
    }
}
