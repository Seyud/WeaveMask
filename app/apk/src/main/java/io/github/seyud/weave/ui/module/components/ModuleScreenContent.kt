package io.github.seyud.weave.ui.module.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.component.ScrollToTopOnChange
import io.github.seyud.weave.ui.component.SearchBox
import io.github.seyud.weave.ui.component.SearchStatus
import io.github.seyud.weave.ui.module.ModuleInfo
import io.github.seyud.weave.ui.module.ModuleUiState
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
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
    onToggleModule: (ModuleInfo, Boolean) -> Unit,
    onRunAction: (ModuleInfo) -> Unit,
    onOpenWebUi: (ModuleInfo) -> Unit,
    onAddShortcut: (ModuleInfo) -> Unit,
    onDownloadUpdate: (ModuleInfo) -> Unit,
    onToggleModuleRemove: (ModuleInfo) -> Unit,
    onHideModule: ((ModuleInfo) -> Unit)?,
    onRevealModule: ((ModuleInfo) -> Unit)?,
) {
    val layoutDirection = LocalLayoutDirection.current
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }

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
                top = innerPadding.calculateTopPadding() + boxHeight + 6.dp,
            ),
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf(
                stringResource(CoreR.string.pull_down_to_refresh),
                stringResource(CoreR.string.release_to_refresh),
                stringResource(CoreR.string.refreshing),
                stringResource(CoreR.string.refreshed_successfully),
            ),
            onRefresh = {
                if (!uiState.isRefreshing) {
                    onRefresh()
                    refreshTick.intValue++
                }
            },
        ) {
            when {
                uiState.isLoading && uiState.modules.isEmpty() -> {
                    LoadingContent(
                        modifier = Modifier.padding(
                            top = innerPadding.calculateTopPadding() + boxHeight + 6.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = contentBottomPadding,
                        ),
                    )
                }

                uiState.modules.isEmpty() -> {
                    EmptyContent(
                        modifier = Modifier.padding(
                            top = innerPadding.calculateTopPadding() + boxHeight + 6.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = contentBottomPadding,
                        ),
                    )
                }

                else -> {
                    val latestModules = rememberUpdatedState(uiState.modules)
                    val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
                    ScrollToTopOnChange(
                        listState,
                        uiState.sortEnabledFirst,
                        uiState.sortUpdateFirst,
                        uiState.sortExecutableFirst,
                        refreshTick.intValue,
                        isBusy = { latestRefreshing.value },
                    ) { latestModules.value }
                    ModuleList(
                        modules = uiState.modules,
                        blurBackdrop = blurBackdrop,
                        onToggleModule = onToggleModule,
                        onRunAction = onRunAction,
                        onOpenWebUi = onOpenWebUi,
                        onAddShortcut = onAddShortcut,
                        onDownloadUpdate = onDownloadUpdate,
                        onToggleModuleRemove = onToggleModuleRemove,
                        onHideModule = onHideModule,
                        onRevealModule = onRevealModule,
                        topContentPadding = innerPadding.calculateTopPadding() + boxHeight + 6.dp,
                        contentBottomPadding = contentBottomPadding,
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        listState = listState,
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
                text = stringResource(CoreR.string.loading),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun EmptyContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(CoreR.string.module_empty),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
        )
    }
}
