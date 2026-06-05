package io.github.seyud.weave.ui.modulerepo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.download.DownloadEngine
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.model.module.OnlineModule
import io.github.seyud.weave.core.model.module.RepoModuleDetail
import io.github.seyud.weave.core.model.module.RepoModuleSummary
import io.github.seyud.weave.core.model.module.RepoRelease
import io.github.seyud.weave.core.model.module.RepoReleaseAsset
import io.github.seyud.weave.core.repository.ModuleRepoRepositoryImpl
import io.github.seyud.weave.dialog.OnlineModuleInstallDialog
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.ui.component.HtmlText
import io.github.seyud.weave.ui.component.MiuixConfirmDialog
import io.github.seyud.weave.ui.component.MarkdownText
import io.github.seyud.weave.ui.component.deferredTopPadding
import io.github.seyud.weave.ui.flash.FlashRequest
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import io.github.seyud.weave.core.R as CoreR

@Composable
fun ModuleRepoScreen(viewModel: ModuleRepoViewModel, onNavigateBack: () -> Unit, onOpenModuleDetail: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state = viewModel.uiState
    val locale = Locale.getDefault()
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberBarBlurBackdrop(LocalEnableBlur.current, MiuixTheme.colorScheme.surface)
    val pull = rememberPullToRefreshState()
    val layoutDirection = LocalLayoutDirection.current
    val dynamicTopPadding = remember(scrollBehavior) {
        { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    var showSortPopup by remember { mutableStateOf(false) }
    var showSourcePopup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit, Config.moduleRepoBaseUrl) { viewModel.ensureLoaded() }
    val modules = remember(state.modules, state.sortByName, locale) {
        val collator = Collator.getInstance(locale)
        if (!state.sortByName) state.modules else state.modules.sortedWith(compareBy(collator) { it.displayName })
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
                            DropdownImpl(text = stringResource(CoreR.string.module_repos_sort_name), optionSize = 1, isSelected = state.sortByName, onSelectedIndexChange = { viewModel.toggleSortByName(); showSortPopup = false }, index = 0)
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
        PullToRefresh(
            modifier = Modifier.fillMaxSize().attachBarBlurBackdrop(blurBackdrop),
            isRefreshing = state.isRefreshing,
            pullToRefreshState = pull,
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf(context.getString(CoreR.string.pull_down_to_refresh), context.getString(CoreR.string.release_to_refresh), context.getString(CoreR.string.refreshing), context.getString(CoreR.string.refreshed_successfully)),
            onRefresh = { viewModel.refresh(forceLoading = state.modules.isEmpty()) },
        ) {
            if (state.isLoading && state.modules.isEmpty()) {
                RepoCenterState(title = state.errorMessage, loading = state.errorMessage.isNullOrBlank(), actionLabel = if (state.errorMessage.isNullOrBlank()) null else context.getString(CoreR.string.network_retry), onActionClick = if (state.errorMessage.isNullOrBlank()) null else ({ viewModel.refresh(forceLoading = true) }), modifier = Modifier.padding(top = innerPadding.calculateTopPadding(), start = innerPadding.calculateStartPadding(layoutDirection), end = innerPadding.calculateEndPadding(layoutDirection)))
            } else {
                LazyColumn(
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
                    if (modules.isEmpty()) item { RepoCenterState(title = context.getString(CoreR.string.module_repo_empty), modifier = Modifier.fillMaxWidth()) }
                    items(modules, key = { it.moduleId }, contentType = { "module" }) { module -> RepoModuleSummaryCard(module = module, onClick = { onOpenModuleDetail(module.moduleId) }) }
                }
            }
        }
    }
}

@Composable
private fun RepoModuleSummaryCard(module: RepoModuleSummary, onClick: () -> Unit) {
    val metaBg = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    val metaTint = MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = module.displayName, fontSize = 17.sp, fontWeight = FontWeight(550), modifier = Modifier.weight(1f))
                if (module.metamodule) {
                    Text(text = "META", fontSize = 12.sp, color = metaTint, modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(metaBg).padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight(750), maxLines = 1)
                }
                if (module.stargazerCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.Star, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 8.dp).size(16.dp))
                        Text(text = module.stargazerCount.toString(), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Text(text = "ID: ${module.moduleId}", fontSize = 12.sp, fontWeight = FontWeight(550), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(text = "${stringResource(CoreR.string.module_author)}: ${module.authorsText}", fontSize = 12.sp, fontWeight = FontWeight(550), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (module.summary.isNotEmpty()) Text(text = module.summary, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 4.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (!module.latestRelease?.time.isNullOrBlank()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
                Text(text = module.latestRelease?.time.orEmpty(), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
fun ModuleRepoDetailScreen(moduleId: String, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val repository = remember { ModuleRepoRepositoryImpl() }
    val blurBackdrop = rememberBarBlurBackdrop(LocalEnableBlur.current, MiuixTheme.colorScheme.surface)
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val baseUrl = Config.moduleRepoBaseUrl
    val webUrl = remember(moduleId, baseUrl) { buildRepoModulePageUrl(baseUrl, moduleId) }
    val blurEnabled = blurBackdrop != null
    val dynamicTopPadding = remember(scrollBehavior) {
        { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    var refreshKey by remember(moduleId, baseUrl) { mutableStateOf(0) }
    var showDownloadConfirm by remember { mutableStateOf(false) }
    var downloadConfirmMessage by remember { mutableStateOf("") }
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cachedDetail = remember(moduleId, baseUrl) { repository.getCachedModuleDetail(moduleId = moduleId, baseUrl = baseUrl) }
    val detailState by produceState(initialValue = RepoDetailState(detail = cachedDetail, isLoading = cachedDetail == null), key1 = moduleId, key2 = baseUrl, key3 = refreshKey) {
        val initialDetail = repository.getCachedModuleDetail(moduleId = moduleId, baseUrl = baseUrl)
        if (refreshKey == 0 && initialDetail != null) {
            value = RepoDetailState(detail = initialDetail)
            return@produceState
        }
        value = RepoDetailState(detail = initialDetail, isLoading = initialDetail == null)
        value = repository.fetchModuleDetail(moduleId = moduleId, baseUrl = baseUrl, forceRefresh = refreshKey > 0).fold(
            onSuccess = { RepoDetailState(detail = it) },
            onFailure = {
                if (initialDetail != null) {
                    RepoDetailState(detail = initialDetail)
                } else {
                    RepoDetailState(errorMessage = it.message ?: context.getString(CoreR.string.failure))
                }
            },
        )
    }
    val tabs = listOf(stringResource(CoreR.string.tab_readme), stringResource(CoreR.string.tab_releases), stringResource(CoreR.string.tab_info))
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    MiuixConfirmDialog(
        show = showDownloadConfirm,
        title = stringResource(CoreR.string.module_install),
        summary = downloadConfirmMessage,
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = {
            showDownloadConfirm = false
            pendingDownloadAction = null
        },
        onConfirm = {
            val action = pendingDownloadAction
            showDownloadConfirm = false
            pendingDownloadAction = null
            action?.invoke()
        },
    )
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBarBlur(blurBackdrop, MiuixTheme.colorScheme.surface),
                color = barBlurContainerColor(blurBackdrop, MiuixTheme.colorScheme.surface),
                title = detailState.detail?.displayName ?: moduleId,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        val direction = LocalLayoutDirection.current
                        Icon(modifier = Modifier.graphicsLayer { if (direction == LayoutDirection.Rtl) scaleX = -1f }, imageVector = MiuixIcons.Back, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { openExternalLink(context, webUrl) }) {
                        Icon(imageVector = MiuixIcons.HorizontalSplit, contentDescription = stringResource(CoreR.string.module_repo_open_web))
                    }
                },
                bottomContent = {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                            .deferredTopPadding(dynamicTopPadding)
                    ) {
                        TabRow(
                            tabs = tabs,
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            colors = TabRowDefaults.tabRowColors(
                                backgroundColor = if (blurEnabled) androidx.compose.ui.graphics.Color.Transparent else MiuixTheme.colorScheme.surface
                            ),
                            height = 40.dp,
                        )
                    }
                }
            )
        },
        popupHost = {},
    ) { innerPadding ->
        when {
            detailState.isLoading -> RepoCenterState(loading = true, modifier = Modifier.padding(top = innerPadding.calculateTopPadding(), start = innerPadding.calculateStartPadding(layoutDirection), end = innerPadding.calculateEndPadding(layoutDirection)))
            detailState.detail == null -> RepoCenterState(title = detailState.errorMessage, actionLabel = context.getString(CoreR.string.network_retry), onActionClick = { refreshKey += 1 }, modifier = Modifier.padding(top = innerPadding.calculateTopPadding(), start = innerPadding.calculateStartPadding(layoutDirection), end = innerPadding.calculateEndPadding(layoutDirection)))
            else -> {
                val detail = detailState.detail ?: return@Scaffold
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().attachBarBlurBackdrop(blurBackdrop),
                    beyondViewportPageCount = tabs.lastIndex,
                ) { page ->
                    val pagePadding = PaddingValues(top = innerPadding.calculateTopPadding(), start = innerPadding.calculateStartPadding(layoutDirection), end = innerPadding.calculateEndPadding(layoutDirection), bottom = innerPadding.calculateBottomPadding() + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding())
                    when (page) {
                        0 -> RepoReadmePage(detail, pagePadding, scrollBehavior)
                        1 -> RepoReleasesPage(
                            detail = detail,
                            innerPadding = pagePadding,
                            scrollBehavior = scrollBehavior,
                            activity = activity,
                            onRequestDownloadConfirm = { message, action ->
                                downloadConfirmMessage = message
                                pendingDownloadAction = action
                                showDownloadConfirm = true
                            },
                        )
                        else -> RepoInfoPage(detail, pagePadding, scrollBehavior)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoReadmePage(
    detail: RepoModuleDetail,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 12.dp,
        ),
        overscrollEffect = null,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    when {
                        detail.readmeHtml.isNotBlank() -> {
                            var loaded by remember(detail.readmeHtml) { mutableStateOf(false) }
                            val alpha by animateFloatAsState(
                                targetValue = if (loaded) 1f else 0f,
                                animationSpec = tween(durationMillis = 300),
                                label = "ReadmeAlpha",
                            )
                            val placeholderAlpha by animateFloatAsState(
                                targetValue = if (loaded) 0f else 1f,
                                animationSpec = tween(durationMillis = 150),
                                label = "ReadmePlaceholderAlpha",
                            )
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                                    HtmlText(
                                        content = detail.readmeHtml,
                                        baseUrl = detail.sourceUrl.ifBlank { buildRepoModulePageUrl(Config.moduleRepoBaseUrl, detail.moduleId) },
                                        modifier = Modifier.fillMaxWidth(),
                                        onLoadingChange = { loaded = !it },
                                    )
                                }
                                if (placeholderAlpha > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp)
                                            .graphicsLayer { this.alpha = placeholderAlpha },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        InfiniteProgressIndicator()
                                    }
                                }
                            }
                        }
                        detail.readme.isNotBlank() -> MarkdownText(content = detail.readme, modifier = Modifier.fillMaxWidth())
                        else -> Text(text = stringResource(CoreR.string.no_info_provided), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoReleasesPage(
    detail: RepoModuleDetail,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    activity: MainActivity?,
    onRequestDownloadConfirm: (String, () -> Unit) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = null,
    ) {
        items(detail.releases, key = { it.tagName }) { release ->
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = release.name.ifBlank { release.tagName }, fontSize = 16.sp, fontWeight = FontWeight(600))
                            Text(text = release.tagName, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        Text(text = release.publishedAt, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (release.descriptionHtml.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
                        var descLoaded by remember(release.descriptionHtml) { mutableStateOf(false) }
                        val descAlpha by animateFloatAsState(
                            targetValue = if (descLoaded) 1f else 0f,
                            animationSpec = tween(durationMillis = 300),
                            label = "ReleaseDescAlpha",
                        )
                        val descPlaceholderAlpha by animateFloatAsState(
                            targetValue = if (descLoaded) 0f else 1f,
                            animationSpec = tween(durationMillis = 150),
                            label = "ReleaseDescPlaceholderAlpha",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(animationSpec = tween(durationMillis = 300)),
                        ) {
                            Box(modifier = Modifier.graphicsLayer { this.alpha = descAlpha }) {
                                HtmlText(
                                    content = release.descriptionHtml,
                                    baseUrl = detail.sourceUrl.ifBlank { detail.url },
                                    modifier = Modifier.fillMaxWidth(),
                                    onLoadingChange = { descLoaded = !it },
                                )
                            }
                            if (descPlaceholderAlpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .graphicsLayer { this.alpha = descPlaceholderAlpha },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    InfiniteProgressIndicator()
                                }
                            }
                        }
                    }
                    if (release.releaseAssets.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
                    release.releaseAssets.forEachIndexed { index, asset ->
                        ReleaseAssetRow(
                            detail = detail,
                            release = release,
                            asset = asset,
                            activity = activity,
                            onRequestDownloadConfirm = onRequestDownloadConfirm,
                        )
                        if (index != release.releaseAssets.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseAssetRow(
    detail: RepoModuleDetail,
    release: RepoRelease,
    asset: RepoReleaseAsset,
    activity: MainActivity?,
    onRequestDownloadConfirm: (String, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val actionIconTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val metaText = remember(asset.size, asset.downloadCount) { "${formatFileSize(asset.size)} · ${asset.downloadCount} downloads" }
    val installVersion = release.version.ifBlank { release.name }
    val downloadSubject = remember(detail.moduleId, detail.displayName, installVersion, release.versionCode, asset.downloadUrl) {
        val onlineModule = OnlineModule(
            id = detail.moduleId,
            name = detail.displayName,
            version = installVersion.ifBlank { "latest" },
            versionCode = release.versionCode ?: 0,
            zipUrl = asset.downloadUrl,
            changelog = "",
        )
        OnlineModuleInstallDialog.Module(onlineModule, false)
    }
    var isDownloading by remember(downloadSubject.notifyId) { mutableStateOf(false) }
    var progress by remember(downloadSubject.notifyId) { mutableStateOf(0f) }
    var downloadedUri by remember(downloadSubject.notifyId) { mutableStateOf<Uri?>(null) }

    LaunchedEffect(lifecycleOwner, downloadSubject.notifyId) {
        DownloadEngine.observeProgress(lifecycleOwner) { currentProgress, subject ->
            if (subject.notifyId != downloadSubject.notifyId) return@observeProgress
            when {
                currentProgress >= 1f -> {
                    isDownloading = false
                    progress = 1f
                    downloadedUri = downloadSubject.file
                }
                currentProgress < 0f -> {
                    isDownloading = currentProgress == -1f
                }
                else -> {
                    isDownloading = true
                    progress = currentProgress
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = asset.name, fontSize = 14.sp)
            Text(text = metaText, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 2.dp))
        }
        IconButton(
            backgroundColor = secondaryContainer,
            minHeight = 35.dp,
            minWidth = 88.dp,
            enabled = !isDownloading,
            onClick = {
                val existingUri = downloadedUri
                if (existingUri != null) {
                    FlashRequest.install(existingUri).toPendingIntent(context).send()
                } else {
                    onRequestDownloadConfirm(
                        context.getString(CoreR.string.module_start_downloading, asset.name)
                    ) {
                        isDownloading = true
                        if (activity != null) {
                            DownloadEngine.startWithActivity(activity, downloadSubject)
                        } else {
                            DownloadEngine.start(context.applicationContext, downloadSubject)
                        }
                    }
                }
            }
        ) {
            if (isDownloading) {
                Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(progress = progress, size = 20.dp, strokeWidth = 2.dp)
                    Text(
                        text = stringResource(CoreR.string.download),
                        modifier = Modifier.padding(start = 4.dp, end = 2.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = actionIconTint,
                        maxLines = 1,
                    )
                }
            } else {
                Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = MiuixIcons.FileDownloads,
                        tint = actionIconTint,
                        contentDescription = if (downloadedUri != null) stringResource(CoreR.string.module_install) else stringResource(CoreR.string.download),
                    )
                    Text(
                        text = if (downloadedUri != null) stringResource(CoreR.string.module_install) else stringResource(CoreR.string.download),
                        modifier = Modifier.padding(start = 4.dp, end = 2.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = actionIconTint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoInfoPage(
    detail: RepoModuleDetail,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    val layoutDirection = LocalLayoutDirection.current
    val context = LocalContext.current
    val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val actionIconTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + 12.dp,
        ),
        overscrollEffect = null,
    ) {
        if (detail.authors.isNotEmpty()) {
            item {
                SmallTitle(text = stringResource(CoreR.string.module_author), modifier = Modifier.padding(top = 6.dp))
                Card(modifier = Modifier.padding(horizontal = 12.dp), cornerRadius = 18.dp) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        detail.authors.forEachIndexed { index, author ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = author.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                IconButton(backgroundColor = secondaryContainer, minHeight = 35.dp, minWidth = 35.dp, enabled = author.link.isNotBlank(), onClick = { if (author.link.isNotBlank()) openExternalLink(context, author.link) }) {
                                    Icon(modifier = Modifier.size(20.dp), imageVector = MiuixIcons.Link, tint = if (author.link.isNotBlank()) actionIconTint else actionIconTint.copy(alpha = 0.35f), contentDescription = null)
                                }
                            }
                            if (index != detail.authors.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
        if (detail.sourceUrl.isNotBlank()) {
            item {
                SmallTitle(text = stringResource(CoreR.string.module_repos_source_code), modifier = Modifier.padding(top = 6.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), cornerRadius = 18.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = detail.sourceUrl, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(backgroundColor = secondaryContainer, minHeight = 35.dp, minWidth = 35.dp, onClick = { openExternalLink(context, detail.sourceUrl) }) {
                            Icon(modifier = Modifier.size(20.dp), imageVector = MiuixIcons.Link, tint = actionIconTint, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoCenterState(modifier: Modifier = Modifier, title: String? = null, loading: Boolean = false, actionLabel: String? = null, onActionClick: (() -> Unit)? = null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
            if (loading) InfiniteProgressIndicator()
            if (!title.isNullOrBlank()) Text(text = title, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (!actionLabel.isNullOrBlank() && onActionClick != null) TextButton(text = actionLabel, onClick = onActionClick)
        }
    }
}

private data class RepoDetailState(val isLoading: Boolean = false, val detail: RepoModuleDetail? = null, val errorMessage: String? = null)

private fun buildRepoModulePageUrl(
    baseUrl: String,
    moduleId: String,
): String {
    val normalizedBaseUrl = Config.normalizeModuleRepoBaseUrl(baseUrl)
        ?: "https://modules.kernelsu.org"
    return "$normalizedBaseUrl/module/${Uri.encode(moduleId)}/"
}

private fun formatFileSize(size: Long): String = when {
    size >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", size / (1024f * 1024f * 1024f))
    size >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", size / (1024f * 1024f))
    size >= 1024L -> String.format(Locale.US, "%.0f KB", size / 1024f)
    else -> "$size B"
}

private fun startRepoAssetTransfer(context: Context, activity: MainActivity?, moduleId: String, moduleName: String, version: String, versionCode: Int, downloadUrl: String, autoLaunch: Boolean) {
    val onlineModule = OnlineModule(id = moduleId, name = moduleName, version = version.ifBlank { "latest" }, versionCode = versionCode, zipUrl = downloadUrl, changelog = "")
    val subject = OnlineModuleInstallDialog.Module(onlineModule, autoLaunch)
    if (activity != null) DownloadEngine.startWithActivity(activity, subject) else DownloadEngine.start(context.applicationContext, subject)
}

private fun openExternalLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.toast(CoreR.string.open_link_failed_toast, Toast.LENGTH_SHORT)
    }
}
