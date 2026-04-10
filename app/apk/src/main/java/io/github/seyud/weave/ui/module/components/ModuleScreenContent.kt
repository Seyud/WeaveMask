package io.github.seyud.weave.ui.module.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.component.SearchBox
import io.github.seyud.weave.ui.component.SearchStatus
import io.github.seyud.weave.ui.module.ModuleInfo
import io.github.seyud.weave.ui.module.ModuleUiState
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ModuleScreenContent(
    innerPadding: PaddingValues,
    uiState: ModuleUiState,
    uiSearchStatus: SearchStatus,
    contentBottomPadding: Dp,
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    onSearchStatusChange: (SearchStatus) -> Unit,
    onRefresh: () -> Unit,
    onInstallPressed: () -> Unit,
    onToggleModule: (ModuleInfo, Boolean) -> Unit,
    onRunAction: (ModuleInfo) -> Unit,
    onOpenWebUi: (ModuleInfo) -> Unit,
    onAddShortcut: (ModuleInfo) -> Unit,
    onDownloadUpdate: (ModuleInfo) -> Unit,
    onToggleModuleRemove: (ModuleInfo) -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val pullToRefreshState = rememberPullToRefreshState()

    uiSearchStatus.SearchBox(
        onSearchStatusChange = onSearchStatusChange,
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
        ),
        blurBackdrop = blurBackdrop,
        renderCollapsedBar = false,
    ) { boxHeight ->
        PullToRefresh(
            modifier = Modifier
                .fillMaxSize()
                .attachBarBlurBackdrop(blurBackdrop),
            isRefreshing = uiState.isRefreshing,
            pullToRefreshState = pullToRefreshState,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + boxHeight.value + 6.dp,
            ),
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf(
                context.getString(CoreR.string.pull_down_to_refresh),
                context.getString(CoreR.string.release_to_refresh),
                context.getString(CoreR.string.refreshing),
                context.getString(CoreR.string.refreshed_successfully),
            ),
            onRefresh = {
                if (!uiState.isRefreshing) {
                    onRefresh()
                }
            },
        ) {
            when {
                uiState.isLoading && uiState.modules.isEmpty() -> {
                    LoadingContent(
                        modifier = Modifier.padding(
                            top = innerPadding.calculateTopPadding() + boxHeight.value + 6.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = contentBottomPadding,
                        ),
                    )
                }

                uiState.modules.isEmpty() -> {
                    EmptyContent(
                        onInstallPressed = onInstallPressed,
                        modifier = Modifier.padding(
                            top = innerPadding.calculateTopPadding() + boxHeight.value + 6.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = contentBottomPadding,
                        ),
                    )
                }

                else -> {
                    ModuleList(
                        modules = uiState.modules,
                        blurBackdrop = blurBackdrop,
                        onInstallPressed = onInstallPressed,
                        onToggleModule = onToggleModule,
                        onRunAction = onRunAction,
                        onOpenWebUi = onOpenWebUi,
                        onAddShortcut = onAddShortcut,
                        onDownloadUpdate = onDownloadUpdate,
                        onToggleModuleRemove = onToggleModuleRemove,
                        topContentPadding = innerPadding.calculateTopPadding() + boxHeight.value + 6.dp,
                        contentBottomPadding = contentBottomPadding,
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    )
                }
            }
        }
    }
}

@Composable
internal fun LoadingContent(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InfiniteProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = context.getString(CoreR.string.loading),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun EmptyContent(
    onInstallPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InstallModuleEntryButton(onClick = onInstallPressed)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = context.getString(CoreR.string.module_empty),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun InstallModuleEntryButton(
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(),
    ) {
        Icon(
            imageVector = MiuixIcons.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = context.getString(CoreR.string.module_action_install_external))
    }
}
