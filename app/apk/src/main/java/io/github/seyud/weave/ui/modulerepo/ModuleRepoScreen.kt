package io.github.seyud.weave.ui.modulerepo

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.seyud.weave.core.model.module.RepoInstallState
import io.github.seyud.weave.core.model.module.RepoModule
import io.github.seyud.weave.core.model.module.RepoSort
import io.github.seyud.weave.core.model.module.repoInstallStateOf
import io.github.seyud.weave.core.registry.RegistryUrls
import io.github.seyud.weave.ui.component.ScrollToTopOnChange
import io.github.seyud.weave.ui.component.deferredTopPadding
import io.github.seyud.weave.ui.module.ModuleViewModel
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import io.github.seyud.weave.core.R as CoreR

/**
 * Module store list: the registry index of the configured repository.
 *
 * Rows come from [ModuleRepoUiState.visibleModules] (search hits while searching), and the
 * installed / update badges from [ModuleRepoUiState.installedVersions], which is fed by the
 * installed module list of [moduleViewModel].
 */
@Composable
fun ModuleRepoScreen(
    viewModel: ModuleRepoViewModel,
    moduleViewModel: ModuleViewModel,
    onNavigateBack: () -> Unit,
    onOpenModuleDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = viewModel.uiState
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberBarBlurBackdrop(LocalEnableBlur.current, MiuixTheme.colorScheme.surface)
    val pull = rememberPullToRefreshState()
    val layoutDirection = LocalLayoutDirection.current
    val dynamicTopPadding = remember(scrollBehavior) {
        { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    var showSortPopup by remember { mutableStateOf(false) }
    var showSourcePopup by remember { mutableStateOf(false) }
    val refreshTick = remember { mutableIntStateOf(0) }
    val installedModules = moduleViewModel.uiState.modules

    LaunchedEffect(Unit) {
        viewModel.ensureLoaded()
        // Installed modules are not loaded yet (e.g. the module page was never opened): ask once,
        // so the installed / update badges are meaningful from the first row.
        if (installedModules.isEmpty()) moduleViewModel.refresh(resort = false, showProgress = false)
    }
    LaunchedEffect(installedModules) {
        viewModel.setInstalledVersions(installedModules.associate { it.id to it.versionCode })
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBarBlur(blurBackdrop, MiuixTheme.colorScheme.surface),
                color = barBlurContainerColor(blurBackdrop, MiuixTheme.colorScheme.surface),
                title = stringResource(CoreR.string.module_repos),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        val direction = LocalLayoutDirection.current
                        Icon(modifier = Modifier.graphicsLayer { if (direction == LayoutDirection.Rtl) scaleX = -1f }, imageVector = MiuixIcons.Back, contentDescription = null)
                    }
                },
                actions = {
                    OverlayListPopup(show = showSortPopup, popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider, alignment = PopupPositionProvider.Align.TopEnd, onDismissRequest = { showSortPopup = false }) {
                        ListPopupColumn {
                            val sortOptions = listOf(
                                RepoSort.UPDATED to CoreR.string.module_repos_sort_updated,
                                RepoSort.NAME to CoreR.string.module_repos_sort_name,
                                RepoSort.STARS to CoreR.string.module_repos_sort_stars,
                            )
                            sortOptions.forEachIndexed { index, (order, titleRes) ->
                                DropdownImpl(
                                    text = stringResource(titleRes),
                                    optionSize = sortOptions.size,
                                    isSelected = state.sortOrder == order,
                                    onSelectedIndexChange = {
                                        viewModel.setSortOrder(order)
                                        showSortPopup = false
                                    },
                                    index = index,
                                )
                            }
                        }
                    }
                    OverlayListPopup(show = showSourcePopup, popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider, alignment = PopupPositionProvider.Align.TopEnd, onDismissRequest = { showSourcePopup = false }) {
                        ListPopupColumn {
                            Text(
                                text = stringResource(CoreR.string.module_repo_source_title),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = state.baseUrl,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp, vertical = 4.dp)
                            .size(40.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showSortPopup = true },
                                onLongClick = { showSourcePopup = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = MiuixIcons.MoreCircle, contentDescription = stringResource(CoreR.string.more_options_description))
                    }
                },
                bottomContent = {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                            .deferredTopPadding(dynamicTopPadding)
                    ) {
                        InputField(
                            query = state.query,
                            onQueryChange = viewModel::setQuery,
                            label = stringResource(CoreR.string.search_modules_label),
                            modifier = Modifier.fillMaxWidth(),
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                        )
                    }
                },
            )
        },
        popupHost = {},
    ) { innerPadding ->
        val centerModifier = Modifier.padding(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
        )
        PullToRefresh(
            modifier = Modifier.fillMaxSize().attachBarBlurBackdrop(blurBackdrop),
            isRefreshing = state.isRefreshing,
            pullToRefreshState = pull,
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf(stringResource(CoreR.string.pull_down_to_refresh), stringResource(CoreR.string.release_to_refresh), stringResource(CoreR.string.refreshing), stringResource(CoreR.string.refreshed_successfully)),
            onRefresh = {
                viewModel.refresh()
                refreshTick.intValue++
            },
        ) {
            val errorRes = state.error?.let(::repoLoadErrorRes)
            if (!state.hasLoaded && errorRes != null) {
                RepoCenterState(
                    title = stringResource(errorRes),
                    actionLabel = stringResource(CoreR.string.network_retry),
                    onActionClick = viewModel::refresh,
                    modifier = centerModifier,
                )
            } else if (!state.hasLoaded) {
                RepoCenterState(loading = true, modifier = centerModifier)
            } else {
                val modules = state.visibleModules
                val listState = rememberLazyListState()
                val latestModules = rememberUpdatedState(modules)
                val latestRefreshing = rememberUpdatedState(state.isRefreshing)
                ScrollToTopOnChange(
                    listState,
                    state.sortOrder,
                    state.query,
                    refreshTick.intValue,
                    isBusy = { latestRefreshing.value },
                ) { latestModules.value }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = 16.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    overscrollEffect = null,
                ) {
                    if (state.stale) {
                        item(key = "__notice_stale", contentType = "notice") {
                            Text(
                                text = stringResource(CoreR.string.module_repo_using_cache),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    if (modules.isEmpty()) {
                        // A failed refresh of an already loaded list leaves the error for the
                        // empty placeholder, so retrying is still one tap away.
                        item(key = "__empty", contentType = "notice") {
                            RepoCenterState(
                                title = stringResource(errorRes ?: CoreR.string.module_repo_empty),
                                actionLabel = errorRes?.let { stringResource(CoreR.string.network_retry) },
                                onActionClick = errorRes?.let { ({ viewModel.refresh() }) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(modules, key = { it.moduleId }, contentType = { "module" }) { module ->
                        RepoModuleRow(
                            module = module,
                            origin = state.baseUrl,
                            installState = repoInstallStateOf(module, state.installedVersions),
                            onClick = { onOpenModuleDetail(module.moduleId) },
                            onOpenStars = { openWebLink(uriHandler, starsUrl(module.starsRepository)) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold
                                )
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Maps a load failure of the ViewModel to the string that explains it. */
private fun repoLoadErrorRes(error: RepoLoadError): Int = when (error) {
    RepoLoadError.UNSUPPORTED_SCHEMA -> CoreR.string.module_repo_unsupported_schema
    RepoLoadError.OFFLINE -> CoreR.string.network_offline
    RepoLoadError.FAILED -> CoreR.string.module_repo_load_failed
}

@Composable
private fun RepoModuleRow(
    module: RepoModule,
    origin: String,
    installState: RepoInstallState,
    onClick: () -> Unit,
    onOpenStars: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RepoModuleIcon(
                    url = module.iconUrl,
                    origin = origin,
                    modifier = Modifier.padding(end = 8.dp),
                    size = 24.dp,
                )
                Text(
                    text = module.displayName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight(550),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RepoBadges(
                    metamodule = module.metamodule,
                    isScript = module.isScript,
                    installState = installState,
                )
                RepoStarCount(
                    stars = module.stars,
                    onClick = onOpenStars,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = "ID: ${module.moduleId}",
                fontSize = 12.sp,
                fontWeight = FontWeight(550),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (module.authors.isNotEmpty()) {
                Text(
                    text = "${stringResource(CoreR.string.module_author)}: ${module.authors}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
            if (module.summary.isNotEmpty()) {
                Text(
                    text = module.summary,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                thickness = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = module.latestVersion,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                if (module.latestReleaseTime.isNotEmpty()) {
                    Text(
                        text = module.latestReleaseTime,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/** Compact star count; the whole area is the tap target of the repository's stargazers page. */
@Composable
private fun RepoStarCount(stars: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (stars == null || stars <= 0) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.TopDownloads,
            contentDescription = stringResource(CoreR.string.module_repo_stars),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = formatStarCount(stars),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun RepoTag(text: String, background: Color, tint: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = tint,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontWeight = FontWeight(750),
        maxLines = 1,
    )
}

@Composable
private fun RepoBadges(metamodule: Boolean, isScript: Boolean, installState: RepoInstallState) {
    if (metamodule) {
        RepoTag(
            text = "META",
            background = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
            tint = MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
        )
    }
    if (isScript) {
        RepoTag(
            text = stringResource(CoreR.string.module_repo_script_badge),
            background = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
            tint = MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
        )
    }
    when (installState) {
        RepoInstallState.UPDATABLE -> RepoTag(
            text = stringResource(CoreR.string.module_repo_update_available),
            background = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
            tint = MiuixTheme.colorScheme.primary,
        )

        RepoInstallState.INSTALLED -> RepoTag(
            text = stringResource(CoreR.string.module_repo_installed),
            background = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
            tint = MiuixTheme.colorScheme.onSecondaryContainer,
        )

        RepoInstallState.NONE -> {}
    }
}

/** Centered loading / empty / error placeholder of the list screen. */
@Composable
private fun RepoCenterState(
    modifier: Modifier = Modifier,
    title: String? = null,
    loading: Boolean = false,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
            if (loading) InfiniteProgressIndicator()
            if (!title.isNullOrBlank()) Text(text = title, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
            if (!actionLabel.isNullOrBlank() && onActionClick != null) TextButton(text = actionLabel, onClick = onActionClick)
        }
    }
}

/** Hands [url] to the browser, but only after it passed the registry URL validation. */
internal fun openWebLink(uriHandler: UriHandler, url: String?) {
    if (url.isNullOrBlank() || !RegistryUrls.isWebUrl(url)) return
    runCatching { uriHandler.openUri(url) }
}
