package io.github.seyud.weave.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.defaultBarBlur
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Stable
data class SearchStatus(
    val label: String,
    val searchText: String = "",
    val current: Status = Status.COLLAPSED,
    val offsetY: Dp = 0.dp,
    val resultStatus: ResultStatus = ResultStatus.DEFAULT
) {
    fun isExpand() = current == Status.EXPANDED
    fun isCollapsed() = current == Status.COLLAPSED
    fun shouldExpand() = current == Status.EXPANDED || current == Status.EXPANDING
    fun shouldCollapsed() = current == Status.COLLAPSED || current == Status.COLLAPSING
    fun isAnimatingExpand() = current == Status.EXPANDING

    fun onAnimationComplete(): SearchStatus {
        return when (current) {
            Status.EXPANDING -> copy(current = Status.EXPANDED)
            Status.COLLAPSING -> copy(searchText = "", current = Status.COLLAPSED)
            else -> this
        }
    }

    @Composable
    fun TopAppBarAnim(
        modifier: Modifier = Modifier,
        visible: Boolean = shouldCollapsed(),
        blurBackdrop: LayerBackdrop? = null,
        content: @Composable () -> Unit
    ) {
        val surfaceColor = colorScheme.surface
        val topAppBarAlpha = animateFloatAsState(
            if (visible) 1f else 0f,
            animationSpec = tween(if (visible) 550 else 0, easing = FastOutSlowInEasing),
            label = "TopAppBarAlpha"
        )
        Box(
            modifier = modifier.then(Modifier.defaultBarBlur(blurBackdrop, surfaceColor))
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = topAppBarAlpha.value }
                    .then(if (visible) Modifier else Modifier.pointerInput(Unit) { })
            ) {
                content()
            }
        }
    }

    enum class Status { EXPANDED, EXPANDING, COLLAPSED, COLLAPSING }
    enum class ResultStatus { DEFAULT, EMPTY, LOAD, SHOW }
}

@Composable
fun SearchStatus.SearchBox(
    onSearchStatusChange: (SearchStatus) -> Unit,
    collapseBar: @Composable (SearchStatus, Dp, PaddingValues) -> Unit = { searchStatus, topPadding, innerPadding ->
        SearchBarFake(searchStatus.label, topPadding, innerPadding)
    },
    searchBarTopPadding: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    blurBackdrop: LayerBackdrop? = null,
    renderCollapsedBar: Boolean = true,
    content: @Composable (MutableState<Dp>) -> Unit
) {
    val searchStatus = this
    val density = LocalDensity.current
    val surfaceColor = colorScheme.surface

    val offsetY = remember { mutableIntStateOf(0) }
    val boxHeight = remember { mutableStateOf(0.dp) }

    if (renderCollapsedBar) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
                .graphicsLayer {
                    alpha = if (searchStatus.isCollapsed()) 1f else 0f
                }
                .offset(y = contentPadding.calculateTopPadding())
                .onGloballyPositioned {
                    it.positionInWindow().y.apply {
                        offsetY.intValue = (this@apply * 0.9).toInt()
                        with(density) {
                            val newOffsetY = this@apply.toDp()
                            val newBoxHeight = it.size.height.toDp()
                            if (searchStatus.offsetY != newOffsetY) {
                                onSearchStatusChange(searchStatus.copy(offsetY = newOffsetY))
                            }
                            boxHeight.value = newBoxHeight
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        onSearchStatusChange(searchStatus.copy(current = SearchStatus.Status.EXPANDING))
                    }
                }
                .then(
                    Modifier.defaultBarBlur(blurBackdrop, surfaceColor)
                )
        ) {
            collapseBar(searchStatus, searchBarTopPadding, contentPadding)
        }
    }
    Box {
        AnimatedVisibility(
            visible = searchStatus.shouldCollapsed(),
            enter = fadeIn(tween(300, easing = LinearOutSlowInEasing)) + slideInVertically(
                tween(300, easing = LinearOutSlowInEasing)
            ) { -offsetY.intValue },
            exit = fadeOut(tween(300, easing = LinearOutSlowInEasing)) + slideOutVertically(
                tween(300, easing = LinearOutSlowInEasing)
            ) { -offsetY.intValue }
        ) {
            content(boxHeight)
        }
    }
}

@Composable
fun SearchStatus.SearchPager(
    onSearchStatusChange: (SearchStatus) -> Unit,
    defaultResult: @Composable () -> Unit,
    expandBar: @Composable (SearchStatus, (SearchStatus) -> Unit, Dp) -> Unit = { searchStatus, onStatusChange, padding ->
        SearchBar(searchStatus, onStatusChange, padding)
    },
    searchBarTopPadding: Dp = 12.dp,
    resultModifier: Modifier = Modifier,
    resultContentPadding: PaddingValues = PaddingValues(0.dp),
    result: LazyListScope.() -> Unit
) {
    val searchStatus = this
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val surfaceColor = colorScheme.surface
    val topPadding by animateDpAsState(
        targetValue = if (searchStatus.shouldExpand()) systemBarsPadding + 5.dp else max(searchStatus.offsetY, 0.dp),
        animationSpec = tween(300, easing = LinearOutSlowInEasing),
        label = "SearchPagerTopPadding"
    ) {
        onSearchStatusChange(searchStatus.onAnimationComplete())
    }
    val surfaceAlpha by animateFloatAsState(
        if (searchStatus.shouldExpand()) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "SearchPagerSurfaceAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f)
            .drawBehind {
                if (surfaceAlpha > 0f) {
                    drawRect(surfaceColor, alpha = surfaceAlpha)
                }
            }
            .semantics { onClick { false } }
            .then(if (!searchStatus.isCollapsed()) Modifier.pointerInput(Unit) { } else Modifier)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .drawBehind {
                    if (!searchStatus.isCollapsed()) {
                        drawRect(surfaceColor)
                    }
                },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!searchStatus.isCollapsed()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .drawBehind { drawRect(surfaceColor) }
                ) {
                    expandBar(searchStatus, onSearchStatusChange, searchBarTopPadding)
                }
            }
            AnimatedVisibility(
                visible = searchStatus.isExpand() || searchStatus.isAnimatingExpand(),
                enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it })
            ) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp, end = 16.dp, top = searchBarTopPadding)
                        .clickable(interactionSource = null, enabled = searchStatus.isExpand(), indication = null) {
                            onSearchStatusChange(
                                searchStatus.copy(
                                    searchText = "",
                                    current = SearchStatus.Status.COLLAPSING
                                )
                            )
                        }
                )
                run {
                    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
                    NavigationBackHandler(
                        state = navEventState,
                        isBackEnabled = true,
                        onBackCompleted = {
                            onSearchStatusChange(
                                searchStatus.copy(
                                    searchText = "",
                                    current = SearchStatus.Status.COLLAPSING
                                )
                            )
                        }
                    )
                }
            }
        }
        if (searchStatus.shouldExpand()) {
            when (searchStatus.resultStatus) {
                SearchStatus.ResultStatus.DEFAULT -> defaultResult()
                SearchStatus.ResultStatus.EMPTY -> {}
                SearchStatus.ResultStatus.LOAD -> {}
                SearchStatus.ResultStatus.SHOW -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .then(resultModifier)
                        .overScrollVertical(),
                    contentPadding = resultContentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result()
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    searchStatus: SearchStatus,
    onSearchStatusChange: (SearchStatus) -> Unit,
    searchBarTopPadding: Dp = 12.dp,
) {
    val focusRequester = remember { FocusRequester() }
    var expanded by rememberSaveable { mutableStateOf(false) }

    InputField(
        query = searchStatus.searchText,
        onQueryChange = { onSearchStatusChange(searchStatus.copy(searchText = it)) },
        label = "",
        leadingIcon = {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .padding(start = 16.dp, end = 8.dp),
                tint = colorScheme.onSurfaceContainerHigh,
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                searchStatus.searchText.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.SearchCleanup,
                    tint = colorScheme.onSurface,
                    contentDescription = stringResource(CoreR.string.clear_search_action),
                    modifier = Modifier
                        .size(44.dp)
                        .padding(start = 8.dp, end = 16.dp)
                        .clickable(interactionSource = null, indication = null) {
                            onSearchStatusChange(searchStatus.copy(searchText = ""))
                        },
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = searchBarTopPadding, bottom = 6.dp)
            .focusRequester(focusRequester),
        onSearch = { },
        expanded = searchStatus.shouldExpand(),
        onExpandedChange = {
            onSearchStatusChange(
                searchStatus.copy(
                    current = if (it) SearchStatus.Status.EXPANDED else SearchStatus.Status.COLLAPSED
                )
            )
        }
    )
    LaunchedEffect(Unit) {
        if (!expanded && searchStatus.shouldExpand()) {
            focusRequester.requestFocus()
            expanded = true
        }
    }
}

@Composable
fun SearchBarFake(
    label: String,
    searchBarTopPadding: Dp = 12.dp,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val layoutDirection = LocalLayoutDirection.current
    val enableBlur = LocalEnableBlur.current
    InputField(
        query = "",
        onQueryChange = { },
        label = label,
        leadingIcon = {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .padding(start = 16.dp, end = 8.dp),
                tint = colorScheme.onSurfaceContainerHigh,
            )
        },
        modifier = Modifier
            .let { if (!enableBlur) it.background(colorScheme.surface) else it }
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection)
            )
            .padding(top = searchBarTopPadding, bottom = 6.dp),
        onSearch = { },
        enabled = false,
        expanded = false,
        onExpandedChange = { }
    )
}
