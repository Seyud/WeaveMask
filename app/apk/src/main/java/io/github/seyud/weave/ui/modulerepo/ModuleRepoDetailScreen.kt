package io.github.seyud.weave.ui.modulerepo

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seyud.weave.core.download.DownloadEngine
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.model.module.OnlineModule
import io.github.seyud.weave.core.model.module.RepoInstallState
import io.github.seyud.weave.core.model.module.RepoModuleDetail
import io.github.seyud.weave.core.model.module.RepoRelease
import io.github.seyud.weave.core.model.module.repoInstallState
import io.github.seyud.weave.core.registry.DownloadVerificationException
import io.github.seyud.weave.core.registry.ExpectedFile
import io.github.seyud.weave.core.registry.RegistryUrls
import io.github.seyud.weave.dialog.OnlineModuleInstallDialog
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.ui.component.HtmlText
import io.github.seyud.weave.ui.component.MarkdownText
import io.github.seyud.weave.ui.component.MiuixConfirmDialog
import io.github.seyud.weave.ui.component.deferredTopPadding
import io.github.seyud.weave.ui.flash.FlashRequest
import io.github.seyud.weave.ui.module.ModuleViewModel
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import io.github.seyud.weave.core.R as CoreR

/** The progress channel reports a failed download with this marker (see [DownloadEngine]). */
private const val DOWNLOAD_FAILED_PROGRESS = -2f

/**
 * Verified release ZIPs of this process, keyed by SHA-256: the Install / Run action survives
 * leaving and reopening the detail screen. Only files whose verified download completed are
 * ever put here, and a rejected download never reaches this map (the engine deletes it).
 */
private val verifiedDownloads = ConcurrentHashMap<String, Uri>()

/**
 * Module store detail: README / Releases / Info of one registry entry.
 *
 * A release ZIP is downloaded through [DownloadEngine], which verifies the registry size and
 * SHA-256 before the file is published, so Install (or Run, for a one-shot script) is only
 * offered for a file that passed the integrity check.
 */
@Composable
fun ModuleRepoDetailScreen(
    moduleId: String,
    moduleViewModel: ModuleViewModel,
    onNavigateBack: () -> Unit,
    onRunScript: (zipFile: Uri, registryId: String, displayName: String, expected: ExpectedFile?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ModuleRepoDetailViewModel = viewModel()
    val state = viewModel.uiState
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberBarBlurBackdrop(LocalEnableBlur.current, MiuixTheme.colorScheme.surface)
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val blurEnabled = blurBackdrop != null
    val dynamicTopPadding = remember(scrollBehavior) {
        { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val installedVersionCode = moduleViewModel.uiState.modules.firstOrNull { it.id == moduleId }?.versionCode
    var showDownloadConfirm by remember { mutableStateOf(false) }
    var downloadConfirmMessage by remember { mutableStateOf("") }
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(moduleId) { viewModel.load(moduleId) }
    LaunchedEffect(Unit) {
        // The installed module list may not be loaded yet; both screens share this ViewModel.
        if (moduleViewModel.uiState.modules.isEmpty()) moduleViewModel.refresh(resort = false, showProgress = false)
    }
    LaunchedEffect(installedVersionCode) { viewModel.setInstalledVersionCode(installedVersionCode) }

    val detail = state.detail
    val uriHandler = LocalUriHandler.current
    val onRetry: () -> Unit = { viewModel.load(moduleId, force = true) }
    val onOpenExternal: (String) -> Unit = { url -> openWebLink(uriHandler, url) }
    val tabs = listOf(stringResource(CoreR.string.tab_readme), stringResource(CoreR.string.tab_releases), stringResource(CoreR.string.tab_info))
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })

    MiuixConfirmDialog(
        show = showDownloadConfirm,
        title = stringResource(if (state.isScript) CoreR.string.script_run else CoreR.string.module_install),
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
                title = detail?.module?.displayName ?: moduleId,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        val direction = LocalLayoutDirection.current
                        Icon(modifier = Modifier.graphicsLayer { if (direction == LayoutDirection.Rtl) scaleX = -1f }, imageVector = MiuixIcons.Back, contentDescription = null)
                    }
                },
                actions = {
                    val webUrl = detail?.webUrl
                    if (webUrl != null && RegistryUrls.isWebUrl(webUrl)) {
                        IconButton(onClick = { onOpenExternal(webUrl) }) {
                            Icon(imageVector = MiuixIcons.HorizontalSplit, contentDescription = stringResource(CoreR.string.module_repo_open_web))
                        }
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
                                backgroundColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface
                            ),
                            height = 40.dp,
                        )
                    }
                }
            )
        },
        popupHost = {},
    ) { innerPadding ->
        val pagePadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        )
        val centerModifier = Modifier.padding(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
        )
        when {
            detail != null -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().attachBarBlurBackdrop(blurBackdrop),
                beyondViewportPageCount = tabs.lastIndex,
            ) { page ->
                when (page) {
                    0 -> RepoReadmePage(
                        detail = detail,
                        innerPadding = pagePadding,
                        scrollBehavior = scrollBehavior,
                        onRetry = onRetry,
                        onOpenExternal = onOpenExternal,
                    )

                    1 -> RepoReleasesPage(
                        detail = detail,
                        isScript = state.isScript,
                        canDownload = state.canInstall,
                        innerPadding = pagePadding,
                        scrollBehavior = scrollBehavior,
                        context = context,
                        activity = activity,
                        onRequestDownloadConfirm = { message, action ->
                            downloadConfirmMessage = message
                            pendingDownloadAction = action
                            showDownloadConfirm = true
                        },
                        onInstall = { uri -> FlashRequest.install(uri).toPendingIntent(context).send() },
                        onRunScript = { uri, release ->
                            onRunScript(
                                uri,
                                moduleId,
                                detail.module.displayName,
                                ExpectedFile(release.zipSize, release.sha256),
                            )
                        },
                        onOpenExternal = onOpenExternal,
                    )

                    else -> RepoInfoPage(
                        detail = detail,
                        installedVersionCode = state.installedVersionCode,
                        isScript = state.isScript,
                        innerPadding = pagePadding,
                        scrollBehavior = scrollBehavior,
                        onOpenExternal = onOpenExternal,
                    )
                }
            }

            state.notInIndex -> RepoDetailCenterState(title = stringResource(CoreR.string.module_repo_not_in_index), modifier = centerModifier)
            state.loaded -> RepoDetailCenterState(
                title = stringResource(CoreR.string.module_repo_load_failed),
                actionLabel = stringResource(CoreR.string.network_retry),
                onActionClick = onRetry,
                modifier = centerModifier,
            )

            else -> RepoDetailCenterState(loading = true, modifier = centerModifier)
        }
    }
}

@Composable
private fun RepoReadmePage(
    detail: RepoModuleDetail,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onRetry: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val readmeHtml = detail.readmeHtml
    val readmeMarkdown = detail.readmeMarkdown
    val notice = detailNotice(detail)
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
        notice?.let { text ->
            item(key = "__notice", contentType = "notice") {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    when {
                        readmeHtml != null -> {
                            var loaded by remember(readmeHtml) { mutableStateOf(false) }
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
                                        content = readmeHtml,
                                        baseUrl = detail.readmeBaseUrl,
                                        restrictOrigin = detail.readmeBaseUrl,
                                        onOpenExternal = onOpenExternal,
                                        modifier = Modifier.fillMaxWidth(),
                                        onLoadingChange = { loading -> loaded = !loading },
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

                        // Magisk modules repositories only publish a Markdown README.
                        readmeMarkdown != null -> MarkdownText(
                            content = readmeMarkdown,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        detail.readmeFailed -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(CoreR.string.module_repo_readme_failed),
                                    fontSize = 16.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    textAlign = TextAlign.Center,
                                )
                                TextButton(text = stringResource(CoreR.string.network_retry), onClick = onRetry)
                            }
                        }

                        else -> Text(
                            text = stringResource(CoreR.string.module_repo_no_readme),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoReleasesPage(
    detail: RepoModuleDetail,
    isScript: Boolean,
    canDownload: Boolean,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    context: Context,
    activity: MainActivity?,
    onRequestDownloadConfirm: (String, () -> Unit) -> Unit,
    onInstall: (Uri) -> Unit,
    onRunScript: (Uri, RepoRelease) -> Unit,
    onOpenExternal: (String) -> Unit,
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
        if (isScript) {
            item(key = "__script_notice", contentType = "notice") {
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Text(
                        text = stringResource(CoreR.string.script_notice),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
        if (detail.fromCache) {
            item(key = "__notice_cached", contentType = "notice") {
                Text(
                    text = stringResource(CoreR.string.module_repo_using_cache),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (detail.releases.isEmpty()) {
            item(key = "__no_release", contentType = "notice") {
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                    Text(
                        text = stringResource(CoreR.string.module_repo_no_release_assets),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
        items(detail.releases, key = { it.tag }, contentType = { "release" }) { release ->
            ReleaseCard(
                moduleId = detail.module.moduleId,
                displayName = detail.module.displayName,
                release = release,
                readmeBaseUrl = detail.readmeBaseUrl,
                isScript = isScript,
                canDownload = canDownload,
                context = context,
                activity = activity,
                onRequestDownloadConfirm = onRequestDownloadConfirm,
                onInstall = onInstall,
                onRunScript = onRunScript,
                onOpenExternal = onOpenExternal,
            )
        }
    }
}

@Composable
private fun ReleaseCard(
    moduleId: String,
    displayName: String,
    release: RepoRelease,
    readmeBaseUrl: String,
    isScript: Boolean,
    canDownload: Boolean,
    context: Context,
    activity: MainActivity?,
    onRequestDownloadConfirm: (String, () -> Unit) -> Unit,
    onInstall: (Uri) -> Unit,
    onRunScript: (Uri, RepoRelease) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handle = rememberReleaseDownload(release)
    val subject = rememberReleaseSubject(moduleId, displayName, release, asModuleInstaller = !isScript)
    val startDownloadingText = stringResource(CoreR.string.module_start_downloading, release.zipName)
    val startDownload: () -> Unit = {
        handle.isDownloading = true
        handle.progress = -1f
        if (activity != null) {
            DownloadEngine.startWithActivity(activity, subject)
        } else {
            DownloadEngine.start(context.applicationContext, subject)
        }
    }

    LaunchedEffect(lifecycleOwner, subject.notifyId) {
        DownloadEngine.observeProgress(lifecycleOwner) { progress, downloaded ->
            if (downloaded.notifyId != subject.notifyId) return@observeProgress
            when {
                // A verified download finished: only now is there a file worth installing.
                progress >= 1f -> {
                    handle.isDownloading = false
                    handle.progress = 1f
                    handle.setDownloaded(downloaded.file)
                }

                progress <= DOWNLOAD_FAILED_PROGRESS -> {
                    handle.isDownloading = false
                    handle.progress = -1f
                }

                // Negative (but not the failure marker): the server sent no usable length.
                progress < 0f -> handle.isDownloading = true

                else -> {
                    handle.isDownloading = true
                    handle.progress = progress
                }
            }
        }
    }
    LaunchedEffect(lifecycleOwner, subject.notifyId) {
        DownloadEngine.observeFailure(lifecycleOwner) { failed, reason, _ ->
            if (failed.notifyId != subject.notifyId) return@observeFailure
            handle.isDownloading = false
            handle.progress = -1f
            handle.setDownloaded(null)
            context.toast(downloadFailureMessage(context, reason, release.zipName), Toast.LENGTH_LONG)
        }
    }

    val changelog = release.changelogHtml
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.name.ifBlank { release.tag },
                        fontSize = 17.sp,
                        fontWeight = FontWeight(550),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(CoreR.string.module_repo_version_meta, release.version, release.versionCode),
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (release.publishedAt.isNotEmpty()) {
                    Text(
                        text = release.publishedAt,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            if (!changelog.isNullOrBlank()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
                var loaded by remember(changelog) { mutableStateOf(false) }
                val alpha by animateFloatAsState(
                    targetValue = if (loaded) 1f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "ReleaseChangelogAlpha",
                )
                val placeholderAlpha by animateFloatAsState(
                    targetValue = if (loaded) 0f else 1f,
                    animationSpec = tween(durationMillis = 150),
                    label = "ReleaseChangelogPlaceholderAlpha",
                )
                Box(modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(durationMillis = 300))) {
                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                        HtmlText(
                            content = changelog,
                            baseUrl = readmeBaseUrl,
                            restrictOrigin = readmeBaseUrl,
                            onOpenExternal = onOpenExternal,
                            modifier = Modifier.fillMaxWidth(),
                            onLoadingChange = { loading -> loaded = !loading },
                        )
                    }
                    if (placeholderAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .graphicsLayer { this.alpha = placeholderAlpha },
                            contentAlignment = Alignment.Center,
                        ) {
                            InfiniteProgressIndicator()
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
            Text(text = release.zipName, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (release.zipSize > 0) {
                Text(
                    text = formatFileSize(release.zipSize),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (release.immutable) {
                Text(
                    text = stringResource(CoreR.string.module_repo_immutable_release),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (release.sha256.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = "SHA-256: ${shortHash(release.sha256)}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                // Magisk repository format: no size and no hash are published, so the download
                // cannot be checked and the user is told instead of being given a false sense of
                // integrity.
                Text(
                    text = stringResource(CoreR.string.module_repo_no_integrity),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ReleaseActionRow(
                handle = handle,
                isScript = isScript,
                canDownload = canDownload,
                startDownloadingText = startDownloadingText,
                onRequestDownloadConfirm = onRequestDownloadConfirm,
                startDownload = startDownload,
                onInstall = onInstall,
                onRunScript = onRunScript,
                release = release,
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
            InfoRow(
                title = stringResource(CoreR.string.module_repo_release_page),
                value = release.tagCommit.takeIf { it.isNotBlank() }?.let { shortHash(it) },
                url = release.releaseUrl,
                onOpen = onOpenExternal,
            )
            release.attestationUrl?.let { url ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
                InfoRow(
                    title = stringResource(CoreR.string.module_repo_attestation),
                    url = url,
                    onOpen = onOpenExternal,
                )
            }
        }
    }
}

/**
 * Download / Install / Run action of one release. Install (or Run for a script) is only
 * reachable once [handle] holds a file whose verified download completed.
 */
@Composable
private fun ReleaseActionRow(
    handle: ReleaseDownloadHandle,
    isScript: Boolean,
    canDownload: Boolean,
    startDownloadingText: String,
    onRequestDownloadConfirm: (String, () -> Unit) -> Unit,
    startDownload: () -> Unit,
    onInstall: (Uri) -> Unit,
    onRunScript: (Uri, RepoRelease) -> Unit,
    release: RepoRelease,
) {
    val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val actionIconTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val downloadedUri = handle.downloadedUri
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
        if (!canDownload) return@Row
        val label = when {
            handle.isDownloading -> stringResource(CoreR.string.download)
            downloadedUri == null -> stringResource(CoreR.string.download)
            isScript -> stringResource(CoreR.string.script_run)
            else -> stringResource(CoreR.string.module_install)
        }
        IconButton(
            backgroundColor = secondaryContainer,
            minHeight = 35.dp,
            minWidth = 88.dp,
            enabled = downloadedUri != null || !handle.isDownloading,
            onClick = {
                when {
                    downloadedUri != null && isScript -> onRunScript(downloadedUri, release)
                    downloadedUri != null -> onInstall(downloadedUri)
                    else -> onRequestDownloadConfirm(startDownloadingText, startDownload)
                }
            },
        ) {
            if (handle.isDownloading) {
                Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = if (handle.progress > 0f) handle.progress.coerceAtMost(1f) else null,
                        size = 20.dp,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = label,
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
                        imageVector = if (downloadedUri != null && isScript) MiuixIcons.Play else MiuixIcons.FileDownloads,
                        tint = actionIconTint,
                        contentDescription = label,
                    )
                    Text(
                        text = label,
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
    installedVersionCode: Int?,
    isScript: Boolean,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onOpenExternal: (String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val module = detail.module
    val notice = detailNotice(detail)
    // Update state of an installed module: a script is never installed, so it has none.
    val installTag: Pair<Int, Color>? = if (isScript || installedVersionCode == null) {
        null
    } else {
        when (repoInstallState(installedVersionCode, module.latestVersionCode)) {
            RepoInstallState.UPDATABLE -> CoreR.string.module_repo_update_available to MiuixTheme.colorScheme.primary
            RepoInstallState.INSTALLED -> CoreR.string.module_repo_installed to MiuixTheme.colorScheme.onSecondaryContainer
            RepoInstallState.NONE -> null
        }
    }
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
        notice?.let { text ->
            item(key = "__notice", contentType = "notice") {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
        }
        installTag?.let { tag ->
            item(key = "__install_state", contentType = "info") {
                Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RepoDetailTag(
                        text = stringResource(tag.first),
                        background = if (tag.first == CoreR.string.module_repo_update_available) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                        },
                        tint = tag.second,
                    )
                }
            }
        }
        if (module.authorList.isNotEmpty()) {
            item(key = "__authors", contentType = "info") {
                InfoCard(
                    title = stringResource(CoreR.string.module_author),
                    rows = module.authorList.map { author ->
                        {
                            InfoRow(
                                title = author.name,
                                url = author.link,
                                onOpen = onOpenExternal,
                            )
                        }
                    },
                )
            }
        }
        item(key = "__source", contentType = "info") {
            InfoCard(
                title = stringResource(CoreR.string.module_repos_source_code),
                rows = listOf {
                    InfoRow(
                        title = detail.sourceFullName,
                        value = stringResource(CoreR.string.module_repo_source_commit, shortHash(detail.sourceCommit)),
                        url = detail.sourceUrl,
                        onOpen = onOpenExternal,
                    )
                },
            )
        }
        item(key = "__meta", contentType = "info") {
            InfoCard(
                title = stringResource(CoreR.string.tab_info),
                rows = buildList {
                    if (module.tags.isNotEmpty()) {
                        add {
                            InfoRow(
                                title = stringResource(CoreR.string.module_repo_tags),
                                value = module.tags.joinToString(", "),
                                onOpen = onOpenExternal,
                            )
                        }
                    }
                    module.stars?.let { stars ->
                        add {
                            InfoRow(
                                title = stringResource(CoreR.string.module_repo_stars),
                                value = listOfNotNull(stars.toString(), module.starsRepository).joinToString(" · "),
                                url = starsUrl(module.starsRepository),
                                onOpen = onOpenExternal,
                            )
                        }
                    }
                    detail.license?.let { license ->
                        add {
                            InfoRow(
                                title = stringResource(CoreR.string.module_repo_license),
                                value = license,
                                onOpen = onOpenExternal,
                            )
                        }
                    }
                    detail.homepage?.let { url ->
                        add {
                            InfoRow(
                                title = stringResource(CoreR.string.module_repo_homepage),
                                value = url,
                                url = url,
                                onOpen = onOpenExternal,
                            )
                        }
                    }
                    detail.support?.let { url ->
                        add {
                            InfoRow(
                                title = stringResource(CoreR.string.module_repo_support),
                                value = url,
                                url = url,
                                onOpen = onOpenExternal,
                            )
                        }
                    }
                    add {
                        // Magisk repository indexes carry no size or hash, so the claim below
                        // would be false; those repositories get the honest variant.
                        val verified = detail.releases.any { it.sha256.isNotBlank() }
                        InfoRow(
                            title = stringResource(CoreR.string.module_repo_integrity),
                            value = stringResource(
                                if (verified) CoreR.string.module_repo_integrity_desc
                                else CoreR.string.module_repo_no_integrity
                            ),
                            onOpen = onOpenExternal,
                        )
                    }
                },
            )
        }
        item(key = "__web", contentType = "info") {
            InfoCard(
                title = stringResource(CoreR.string.module_repo_open_web),
                rows = listOf {
                    InfoRow(
                        title = module.displayName,
                        value = detail.webUrl,
                        url = detail.webUrl,
                        onOpen = onOpenExternal,
                    )
                },
            )
        }
    }
}

/**
 * Banner text of the detail pages: the entry was not synced recently, or the detail shown is the
 * last good cached copy.
 */
@Composable
private fun detailNotice(detail: RepoModuleDetail): String? {
    val parts = buildList {
        if (detail.healthStale) add(stringResource(CoreR.string.module_repo_health_stale))
        if (detail.fromCache) add(stringResource(CoreR.string.module_repo_using_cache))
    }
    return if (parts.isEmpty()) null else parts.joinToString("\n")
}

@Composable
private fun InfoCard(title: String, rows: List<@Composable () -> Unit>) {
    if (rows.isEmpty()) return
    SmallTitle(text = title, modifier = Modifier.padding(top = 6.dp))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp), cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            rows.forEachIndexed { index, row ->
                row()
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/** One label / value row with an optional "open in browser" action. */
@Composable
private fun InfoRow(
    title: String,
    value: String? = null,
    url: String? = null,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val actionIconTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val openUrl = url?.takeIf { RegistryUrls.isWebUrl(it) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (openUrl != null) {
            IconButton(
                backgroundColor = secondaryContainer,
                minHeight = 35.dp,
                minWidth = 35.dp,
                onClick = { onOpen(openUrl) },
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = MiuixIcons.Link,
                    tint = actionIconTint,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun RepoDetailTag(text: String, background: Color, tint: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontWeight = FontWeight(750),
        maxLines = 1,
    )
}

@Composable
private fun RepoDetailCenterState(
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

/** Download state of one release ZIP and the verified file of this process, if any. */
private class ReleaseDownloadHandle(private val sha256: String, verifiedUri: Uri?) {
    var isDownloading by mutableStateOf(false)
    var progress by mutableFloatStateOf(-1f)
    var downloadedUri by mutableStateOf(verifiedUri)
        private set

    fun setDownloaded(uri: Uri?) {
        downloadedUri = uri
        if (uri != null) verifiedDownloads[sha256] = uri else verifiedDownloads.remove(sha256)
    }
}

@Composable
private fun rememberReleaseDownload(release: RepoRelease): ReleaseDownloadHandle =
    remember(release.sha256) { ReleaseDownloadHandle(release.sha256, verifiedDownloads[release.sha256]) }

/**
 * The download subject of one release: the registry size + SHA-256 are carried through
 * [OnlineModule] into [io.github.seyud.weave.core.download.Subject.Module], so the engine can
 * verify the ZIP. [OnlineModuleInstallDialog.Module] is that `Subject.Module`; it never
 * auto-launches anything, the file is installed only when the user presses the button.
 */
@Composable
private fun rememberReleaseSubject(
    moduleId: String,
    displayName: String,
    release: RepoRelease,
    asModuleInstaller: Boolean,
): OnlineModuleInstallDialog.Module =
    remember(moduleId, displayName, release.zipUrl, release.sha256, release.zipSize, asModuleInstaller) {
        OnlineModuleInstallDialog.Module(
            module = OnlineModule(
                id = moduleId,
                name = displayName,
                version = release.version.ifBlank { release.tag },
                versionCode = release.versionCode,
                zipUrl = release.zipUrl,
                changelog = "",
                zipSize = release.zipSize,
                zipSha256 = release.sha256,
            ),
            autoLaunch = false,
            asModuleInstaller = asModuleInstaller,
        )
    }

/** User facing text of a failed release download; verification failures get their own one. */
private fun downloadFailureMessage(context: Context, reason: DownloadVerificationException.Reason?, fileName: String): CharSequence =
    when (reason) {
        DownloadVerificationException.Reason.URL_NOT_ALLOWED -> context.getString(CoreR.string.module_repo_download_url_blocked)
        DownloadVerificationException.Reason.SIZE_MISMATCH,
        DownloadVerificationException.Reason.SHA256_MISMATCH -> context.getString(CoreR.string.module_repo_download_verify_failed, fileName)

        null -> context.getString(CoreR.string.download_file_error)
    }
