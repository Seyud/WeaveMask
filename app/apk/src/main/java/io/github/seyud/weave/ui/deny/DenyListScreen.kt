package io.github.seyud.weave.ui.deny

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.asImageBitmap

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeSource
import io.github.seyud.weave.ui.util.defaultHazeEffect
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.utils.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Checkbox
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import io.github.seyud.weave.core.R as CoreR
import java.util.Locale

@Composable
fun DenyListScreen(
    viewModel: DenyListViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadCompleted by viewModel.loadCompleted.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showSystem by viewModel.showSystem.collectAsStateWithLifecycle()
    val showOS by viewModel.showOS.collectAsStateWithLifecycle()

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedItems by rememberSaveable { mutableStateOf(setOf<String>()) }
    val showTopPopup = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var stableOrder by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(viewModel) {
        viewModel.startLoading()
    }

    LaunchedEffect(query, showSystem, showOS, loadCompleted, items.isNotEmpty()) {
        if (loadCompleted || items.isNotEmpty()) {
            stableOrder = buildStableOrder(items)
        }
    }

    val enableBlur = LocalEnableBlur.current
    val hazeState = remember { HazeState() }
    val hazeStyle = if (enableBlur) {
        HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(alpha = 0.84f)),
        )
    } else {
        HazeStyle.Unspecified
    }
    val scrollBehavior = MiuixScrollBehavior()
    val displayItems = remember(items, stableOrder) {
        val fallbackOrder = buildStableOrder(items)
        items.sortedBy { item ->
            stableOrder[item.info.packageName] ?: fallbackOrder[item.info.packageName] ?: Int.MAX_VALUE
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                modifier = if (enableBlur) {
                    Modifier.defaultHazeEffect(hazeState, hazeStyle)
                } else {
                    Modifier
                },
                color = if (enableBlur) Color.Transparent else MiuixTheme.colorScheme.surface,
                title = context.getString(CoreR.string.denylist),
                largeTitle = context.getString(CoreR.string.denylist),
                scrollBehavior = scrollBehavior,
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
                    OverlayListPopup(
                        show = showTopPopup.value,
                        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                        alignment = PopupPositionProvider.Align.TopEnd,
                        onDismissRequest = {
                            showTopPopup.value = false
                        },
                    ) {
                        val optionSize = if (showSystem) 2 else 1
                        ListPopupColumn {
                            DropdownImpl(
                                text = context.getString(CoreR.string.show_system_app),
                                isSelected = showSystem,
                                optionSize = optionSize,
                                onSelectedIndexChange = {
                                    viewModel.isShowSystem = !showSystem
                                    showTopPopup.value = false
                                },
                                index = 0,
                            )
                            if (showSystem) {
                                DropdownImpl(
                                    text = context.getString(CoreR.string.show_os_app),
                                    isSelected = showOS,
                                    optionSize = optionSize,
                                    onSelectedIndexChange = {
                                        viewModel.isShowOS = !showOS
                                        showTopPopup.value = false
                                    },
                                    index = 1,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            showTopPopup.value = true
                        },
                        holdDownState = showTopPopup.value,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.MoreCircle,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
        content = { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .then(if (enableBlur) Modifier.hazeSource(hazeState) else Modifier),
                    contentPadding = paddingValues,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    overscrollEffect = null,
                ) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    item {
                        SearchBar(
                            insideMargin = DpSize(16.dp, 0.dp),
                            inputField = {
                                InputField(
                                    query = query,
                                    onQueryChange = { viewModel.query = it },
                                    onSearch = { },
                                    expanded = searchExpanded,
                                    onExpandedChange = { searchExpanded = it },
                                    label = context.getString(CoreR.string.hide_filter_hint),
                                )
                            },
                            onExpandedChange = { searchExpanded = it },
                            expanded = searchExpanded,
                        ) {}
                    }

                    when {
                        loadCompleted && items.isEmpty() -> {
                            item {
                                EmptyContent()
                            }
                        }
                        else -> {
                            itemsIndexed(
                                items = displayItems,
                                key = { _, item -> item.info.packageName },
                                contentType = { _, _ -> "denylist_app" }
                            ) { index, item ->
                                DenyListItem(
                                    item = item,
                                    isExpanded = expandedItems.contains(item.info.packageName),
                                    onToggleExpanded = {
                                        val packageName = item.info.packageName
                                        expandedItems = if (expandedItems.contains(packageName)) {
                                            expandedItems - packageName
                                        } else {
                                            viewModel.loadProcesses(packageName)
                                            expandedItems + packageName
                                        }
                                    },
                                    onToggleApp = {
                                        viewModel.toggleApp(
                                            packageName = item.info.packageName,
                                            includeAllProcesses = true,
                                            disableIndeterminate = item.toggleState == ToggleableState.Indeterminate
                                        )
                                    },
                                    onToggleProcess = { process, enabled ->
                                        viewModel.toggleProcess(item.info.packageName, process, enabled) 
 },
                                    modifier = Modifier
                                        .zIndex(-index.toFloat())
                                        .animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null,
                                            placementSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                visibilityThreshold = IntOffset.VisibilityThreshold
                                            )
                                        )
                                )
                            }
                        }
                    }

                    item {
                        Spacer(
                            modifier = Modifier
                                .height(24.dp)
                                .navigationBarsPadding(),
                        )
                    }
                }

                if (loading && items.isEmpty()) {
                    LoadingContent(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        },
    )
}

private fun buildStableOrder(items: List<DenyListAppUiModel>): Map<String, Int> {
    return items
        .sortedWith(
            compareBy<DenyListAppUiModel>(
                { toggleSortRank(it.toggleState) },
                { it.info.label.lowercase(Locale.ROOT) },
                { it.info.packageName },
            ),
        )
        .mapIndexed { index, item -> item.info.packageName to index }
        .toMap()
}

private fun toggleSortRank(state: ToggleableState): Int = when (state) {
    ToggleableState.On -> 0
    ToggleableState.Indeterminate -> 1
    ToggleableState.Off -> 2
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InfiniteProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = context.getString(CoreR.string.loading),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
            )
        }
    }
}

@Composable
private fun EmptyContent() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(CoreR.string.none),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DenyListItem(
    item: DenyListAppUiModel,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleApp: () -> Unit,
    onToggleProcess: (ProcessInfo, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Entry animation: fade-in + slide-up effect (applied in draw phase via graphicsLayer)
    val animationState = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animationState.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
    }

    val currentContext = LocalContext.current
    val density = LocalDensity.current
    val iconSizePx = remember(density) { with(density) { 42.dp.roundToPx() } }
    val headerInteractionSource = remember { MutableInteractionSource() }

    // Async icon loading - no fallback on main thread!
    var iconBitmap by remember(item.info.packageName) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(item.info.packageName) {
        launch(Dispatchers.IO) {
            iconBitmap = AppIconCache.loadIconBitmap(currentContext, item.info.applicationInfo, iconSizePx)
        }
    }

    val cardAlpha = if (item.toggleState == ToggleableState.Off) 0.72f else 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                // Apply transformations in the draw phase to avoid relayout
                val progress = animationState.value
                this.alpha = progress * cardAlpha
                this.translationY = 50f * (1f - progress)
            },
        cornerRadius = 18.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .pressable(
                            interactionSource = headerInteractionSource,
                            indication = SinkFeedback(),
                            delay = null,
                        )
                        .clickable(
                            interactionSource = headerInteractionSource,
                            indication = null,
                            onClick = onToggleExpanded,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                        )
                    } else {
                        // Simple placeholder - no main thread loading!
                        Box(modifier = Modifier.size(42.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.info.label,
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.info.packageName,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(
                    state = item.toggleState,
                    onClick = onToggleApp,
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider()
                    when {
                        item.isLoadingProcesses -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentContext.getString(CoreR.string.loading),
                                    style = MiuixTheme.textStyles.body2,
                                )
                            }
                        }
                        item.hasLoadedProcesses && item.processes.isEmpty() -> {
                            Text(
                                text = currentContext.getString(CoreR.string.none),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                            )
                        }
                        else -> {
                            item.processes.forEach { process ->
                                ProcessItemRow(
                                    process = process,
                                    onCheckedChange = { enabled -> onToggleProcess(process, enabled) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessItemRow(
    process: ProcessInfo,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onCheckedChange(!process.isEnabled) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            state = if (process.isEnabled) ToggleableState.On else ToggleableState.Off,
            onClick = { onCheckedChange(!process.isEnabled) },
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = process.displayName,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
