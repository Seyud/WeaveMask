package io.github.seyud.weave.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.seyud.weave.core.model.su.SuLog
import io.github.seyud.weave.core.ktx.timeDateFormat
import io.github.seyud.weave.core.ktx.toTime
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberContentReady
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.launch
import io.github.seyud.weave.core.R as CoreR

@Composable
fun LogScreen(
    viewModel: LogViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabTitles = listOf(
        "WeaveMask",
        context.getString(CoreR.string.superuser),
    )

    val loading = viewModel.loadingState
    val suLogs = viewModel.itemsState
    val magiskLogs = viewModel.magiskLogEntriesState
    val enableBlur = LocalEnableBlur.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBarBlurBackdrop(enableBlur, surfaceColor)
    val contentReady = rememberContentReady()
    val settledPage by remember(pagerState) {
        derivedStateOf { pagerState.settledPage }
    }

    LaunchedEffect(Unit) {
        viewModel.ensureLoaded()
    }

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBarBlur(blurBackdrop, surfaceColor),
                title = context.getString(CoreR.string.logs),
                titleColor = MiuixTheme.colorScheme.onBackground,
                largeTitleColor = MiuixTheme.colorScheme.onBackground,
                color = barBlurContainerColor(blurBackdrop, surfaceColor),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { viewModel.saveMagiskLog() }) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = context.getString(CoreR.string.menuSaveLog),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (selectedTab == 0) viewModel.clearMagiskLog()
                            else viewModel.clearLog()
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = context.getString(CoreR.string.menuClearLog),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        popupHost = { },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        val tabRowHeight = 40.dp
        val tabRowTopPadding = padding.calculateTopPadding()
        val tabRowContentPadding = tabRowTopPadding + tabRowHeight + 18.dp
        val contentStartPadding = padding.
        calculateStartPadding(layoutDirection) + 12.dp
        val contentEndPadding = padding.calculateEndPadding(layoutDirection) + 12.dp
        val contentBottomPadding = padding.calculateBottomPadding() + 88.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .defaultBarBlur(blurBackdrop, surfaceColor)
                    .zIndex(1f)
                    .padding(
                        top = tabRowTopPadding + 12.dp,
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = 6.dp,
                    )
                    .padding(horizontal = 12.dp)
            ) {
                TabRow(
                    tabs = tabTitles,
                    selectedTabIndex = selectedTab,
                    onTabSelected = {
                        selectedTab = it
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(it)
                        }
                    },
                    colors = TabRowDefaults.tabRowColors(
                        backgroundColor = barBlurContainerColor(blurBackdrop, surfaceColor)
                    ),
                    height = tabRowHeight,
                )
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .attachBarBlurBackdrop(blurBackdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .attachBarBlurBackdrop(blurBackdrop),
                    beyondViewportPageCount = if (contentReady) 1 else 0,
                    userScrollEnabled = true,
                ) { page ->
                    val isCurrentPage = page == settledPage
                    when (page) {
                        0 -> if (isCurrentPage || contentReady) MagiskLogTab(
                            entries = magiskLogs,
                            scrollBehavior = scrollBehavior,
                            blurBackdrop = blurBackdrop,
                            topPadding = tabRowContentPadding,
                            bottomPadding = contentBottomPadding,
                            startPadding = contentStartPadding,
                            endPadding = contentEndPadding,
                        )
                        else -> if (isCurrentPage || contentReady) SuLogTab(
                            suLogs = suLogs,
                            scrollBehavior = scrollBehavior,
                            blurBackdrop = blurBackdrop,
                            topPadding = tabRowContentPadding,
                            bottomPadding = contentBottomPadding,
                            startPadding = contentStartPadding,
                            endPadding = contentEndPadding,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalScrollBarApi::class)
private fun SuLogTab(
    suLogs: List<SuLog>,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    blurBackdrop: LayerBackdrop?,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    startPadding: androidx.compose.ui.unit.Dp,
    endPadding: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current

    if (suLogs.isEmpty()) {
        EmptyContent(context.getString(CoreR.string.log_data_none))
        return
    }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(start = startPadding, end = endPadding),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = suLogs,
                key = { it.id },
                contentType = { "su_log" },
            ) { log ->
                SuLogCard(log)
            }
        }

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        )
    }
}

@Composable
private fun SuLogCard(log: SuLog) {
    val actionAllowed = log.action >= 2
    val actionText = if (actionAllowed) "Approved" else "Rejected"

    val uidPidText = buildString {
        append("UID: ${log.toUid}  PID: ${log.fromPid}")
        if (log.target != -1) {
            val target = if (log.target == 0) "magiskd" else log.target.toString()
            append("  → $target")
        }
    }

    val details = buildString {
        if (log.context.isNotEmpty()) {
            append("SELinux context: ${log.context}")
        }
        if (log.gids.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("Supplementary group: ${log.gids}")
        }
        if (log.command.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append(log.command)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = false,
        onClick = {},
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.appName,
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = uidPidText,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = log.time.toTime(timeDateFormat),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = actionText,
                        color = if (actionAllowed) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onError,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .background(
                                color = if (actionAllowed) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            if (details.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = details,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalScrollBarApi::class)
private fun MagiskLogTab(
    entries: List<MagiskLogEntry>,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    blurBackdrop: LayerBackdrop?,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    startPadding: androidx.compose.ui.unit.Dp,
    endPadding: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current

    if (entries.isEmpty()) {
        EmptyContent(context.getString(CoreR.string.log_data_magisk_none))
        return
    }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(start = startPadding, end = endPadding),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                count = entries.size,
                key = { it },
                contentType = { index ->
                    if (entries[index].isParsed) "parsed_magisk_log" else "raw_magisk_log"
                },
            ) { index ->
                MagiskLogCard(entry = entries[index])
            }
        }

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        )
    }
}

@Composable
private fun MagiskLogCard(entry: MagiskLogEntry) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = false,
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(),
        ) {
            if (entry.isParsed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LogLevelBadge(entry.level)
                        Text(
                            text = entry.tag,
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.timestamp,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = entry.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LogLevelBadge(level: Char) {
    val (bg, fg) = when (level) {
        'V' -> Color(0xFF9E9E9E) to Color.White
        'D' -> Color(0xFF2196F3) to Color.White
        'I' -> Color(0xFF4CAF50) to Color.White
        'W' -> Color(0xFFFFC107) to Color.Black
        'E' -> Color(0xFFF44336) to Color.White
        'F' -> Color(0xFF9C27B0) to Color.White
        else -> Color(0xFF757575) to Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = fg,
        )
    }
}

@Composable
private fun EmptyContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}
