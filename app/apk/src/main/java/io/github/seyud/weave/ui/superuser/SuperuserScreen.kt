package io.github.seyud.weave.ui.superuser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.Slider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.Article
import android.os.Process
import androidx.compose.foundation.lazy.LazyListScope
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.seyud.weave.core.model.su.SuPolicy
import io.github.seyud.weave.ui.component.AppIconImage
import io.github.seyud.weave.dialog.SuperuserRevokeDialog
import io.github.seyud.weave.ui.component.SearchBarFake
import io.github.seyud.weave.ui.component.SearchBox
import io.github.seyud.weave.ui.component.SearchPager
import io.github.seyud.weave.ui.component.SearchStatus
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import io.github.seyud.weave.core.R as CoreR
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
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
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

/**
 * 超级用户页面
 * 使用 Compose 实现超级用户授权管理界面
 *
 * @param viewModel 超级用户 ViewModel
 * @param contentBottomPadding 主页面内容底部留白
 * @param modifier Modifier
 */
@Composable
fun SuperuserScreen(
    viewModel: SuperuserViewModel,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasStartedLoading by rememberSaveable { mutableStateOf(false) }
    val searchAppsLabel = stringResource(CoreR.string.search_apps_label)
    var searchStatus by remember(searchAppsLabel) {
        mutableStateOf(SearchStatus(label = searchAppsLabel))
    }
    var expandedPolicyKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val showTopPopup = remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val density = LocalDensity.current
    val blurBackdrop = rememberBarBlurBackdrop(enableBlur, MiuixTheme.colorScheme.surface)
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val uiSearchStatus = searchStatus.copy(
        resultStatus = when {
            uiState.isLoading -> SearchStatus.ResultStatus.LOAD
            uiState.policies.isEmpty() -> SearchStatus.ResultStatus.EMPTY
            else -> SearchStatus.ResultStatus.SHOW
        }
    )

    // 撤销对话框状态
    val revokeDialogState = uiState.revokeDialogState
    var pendingRevokeKey by rememberSaveable { mutableStateOf<String?>(null) }

    // 监听生命周期，当页面从后台返回前台时刷新数据
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasStartedLoading) {
        if (!hasStartedLoading) {
            hasStartedLoading = true
            viewModel.startLoading()
        }
    }

    LaunchedEffect(searchStatus.searchText) {
        if (uiState.query != searchStatus.searchText) {
            viewModel.setQuery(searchStatus.searchText)
        }
    }

    // 显示撤销权限确认对话框
    SuperuserRevokeDialog(
        state = revokeDialogState,
        context = context,
        onDismiss = { viewModel.dismissRevokeDialog() },
        onConfirm = {
            pendingRevokeKey?.let { key ->
                viewModel.confirmRevoke(key)
                pendingRevokeKey = null
            }
        }
    )

    MiuixTheme {
        Scaffold(
            modifier = modifier,
            popupHost = {
                uiSearchStatus.SearchPager(
                    onSearchStatusChange = { searchStatus = it },
                    defaultResult = {},
                    resultModifier = Modifier.padding(horizontal = 16.dp),
                    resultContentPadding = PaddingValues(top = 8.dp, bottom = contentBottomPadding),
                ) {
                    policyItems(
                        policies = uiState.policies,
                        expandedPolicyKeys = expandedPolicyKeys,
                        onToggleExpanded = { key ->
                            expandedPolicyKeys = if (expandedPolicyKeys.contains(key)) {
                                expandedPolicyKeys - key
                            } else {
                                expandedPolicyKeys + key
                            }
                        },
                        onDelete = { key ->
                            pendingRevokeKey = key
                            viewModel.onRevokePressed(key)
                        },
                        onUpdateNotify = { key -> viewModel.toggleNotifyByKey(key) },
                        onUpdateLogging = { key -> viewModel.toggleLogByKey(key) },
                        onUpdatePolicy = { key, policy -> viewModel.updatePolicyByKey(key, policy) }
                    )
                }
            },
            contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
            topBar = {
                uiSearchStatus.TopAppBarAnim(
                    blurBackdrop = blurBackdrop,
                ) {
                    TopAppBar(
                        color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                        title = context.getString(CoreR.string.superuser),
                        titleColor = MiuixTheme.colorScheme.onBackground,
                        largeTitleColor = MiuixTheme.colorScheme.onBackground,
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (uiSearchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != newOffsetY) {
                                                searchStatus = searchStatus.copy(offsetY = newOffsetY)
                                            }
                                        }
                                    }
                                    .then(
                                        if (uiSearchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    searchStatus = uiSearchStatus.copy(
                                                        current = SearchStatus.Status.EXPANDING
                                                    )
                                                }
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                SearchBarFake(uiSearchStatus.label, dynamicTopPadding)
                            }
                        },
                        actions = {
                            OverlayListPopup(
                                show = showTopPopup.value,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                onDismissRequest = {
                                    showTopPopup.value = false
                                }
                            ) {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = if (uiState.showSystemApps) {
                                            stringResource(CoreR.string.hide_system_app_action)
                                        } else {
                                            stringResource(CoreR.string.show_system_app)
                                        },
                                        isSelected = uiState.showSystemApps,
                                        optionSize = 1,
                                        onSelectedIndexChange = {
                                            viewModel.toggleShowSystemApps()
                                            showTopPopup.value = false
                                        },
                                        index = 0
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    showTopPopup.value = true
                                },
                                holdDownState = showTopPopup.value
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.MoreCircle,
                                    contentDescription = stringResource(CoreR.string.more_options_description)
                                )
                            }
                        }
                    )
                }
            },
            content = { innerPadding ->
                uiSearchStatus.SearchBox(
                    onSearchStatusChange = { searchStatus = it },
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection)
                    ),
                    blurBackdrop = blurBackdrop,
                    renderCollapsedBar = false
                ) { boxHeight ->
                    PullToRefresh(
                        modifier = Modifier
                            .fillMaxSize()
                            .attachBarBlurBackdrop(blurBackdrop),
                        isRefreshing = uiState.isRefreshing,
                        pullToRefreshState = pullToRefreshState,
                        contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + boxHeight.value + 6.dp),
                        topAppBarScrollBehavior = scrollBehavior,
                        refreshTexts = listOf(
                            context.getString(CoreR.string.pull_down_to_refresh),
                            context.getString(CoreR.string.release_to_refresh),
                            context.getString(CoreR.string.refreshing),
                            context.getString(CoreR.string.refreshed_successfully)
                        ),
                        onRefresh = {
                            if (!uiState.isRefreshing) {
                                viewModel.refresh()
                            }
                        }
                    ) {
                        when {
                            uiState.isLoading && uiState.policies.isEmpty() -> {
                                LoadingContent(
                                    modifier = Modifier.padding(
                                        top = innerPadding.calculateTopPadding() + boxHeight.value + 6.dp,
                                        start = innerPadding.calculateStartPadding(layoutDirection),
                                        end = innerPadding.calculateEndPadding(layoutDirection),
                                        bottom = contentBottomPadding
                                    )
                                )
                            }
                            uiState.policies.isEmpty() -> {
                                EmptyContent(
                                    modifier = Modifier.padding(
                                        top = innerPadding.calculateTopPadding() + boxHeight.value + 6.dp,
                                        start = innerPadding.calculateStartPadding(layoutDirection),
                                        end = innerPadding.calculateEndPadding(layoutDirection),
                                        bottom = contentBottomPadding
                                    )
                                )
                            }
                            else -> {
                                PolicyList(
                                    policies = uiState.policies,
                                    viewModel = viewModel,
                                    blurBackdrop = blurBackdrop,
                                    contentBottomPadding = contentBottomPadding,
                                    topContentPadding = innerPadding.calculateTopPadding() + boxHeight.value + 6.dp,
                                    expandedPolicyKeys = expandedPolicyKeys,
                                    onToggleExpanded = { key ->
                                        expandedPolicyKeys = if (expandedPolicyKeys.contains(key)) {
                                            expandedPolicyKeys - key
                                        } else {
                                            expandedPolicyKeys + key
                                        }
                                    },
                                    onDelete = { key ->
                                        pendingRevokeKey = key
                                        viewModel.onRevokePressed(key)
                                    },
                                    nestedScrollConnection = scrollBehavior.nestedScrollConnection
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

/**
 * 加载状态显示
 */
@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InfiniteProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = context.getString(CoreR.string.loading),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 空状态显示
 */
@Composable
private fun EmptyContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = context.getString(CoreR.string.superuser_policy_none),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 策略列表
 *
 * @param policies 策略列表
 * @param viewModel 超级用户 ViewModel
 * @param contentBottomPadding 主页面内容底部留白
 * @param onDelete 删除回调
 * @param nestedScrollConnection 嵌套滚动连接
 * @param blurBackdrop 栏位模糊背景
 */
@Composable
@OptIn(ExperimentalScrollBarApi::class)
private fun PolicyList(
    policies: List<PolicyCardUiState>,
    viewModel: SuperuserViewModel,
    blurBackdrop: LayerBackdrop?,
    contentBottomPadding: Dp,
    topContentPadding: Dp,
    expandedPolicyKeys: List<String>,
    onToggleExpanded: (String) -> Unit,
    onDelete: (String) -> Unit,
    nestedScrollConnection: NestedScrollConnection
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .padding(horizontal = 16.dp)
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(top = topContentPadding, bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null
        ) {
            itemsIndexed(
                items = policies,
                key = { _, it -> it.key },
                contentType = { _, _ -> "policy_item" }
            ) { index, policyItem ->
                val policyKey = policyItem.key
                PolicyItem(
                    item = policyItem,
                    isExpanded = expandedPolicyKeys.contains(policyKey),
                    onToggleExpanded = { onToggleExpanded(policyKey) },
                    onDelete = { onDelete(policyKey) },
                    onUpdateNotify = { viewModel.toggleNotifyByKey(policyKey) },
                    onUpdateLogging = { viewModel.toggleLogByKey(policyKey) },
                    onUpdatePolicy = { policy -> viewModel.updatePolicyByKey(policyKey, policy) },
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

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = PaddingValues(top = topContentPadding, bottom = contentBottomPadding),
        )
    }
}

private fun LazyListScope.policyItems(
    policies: List<PolicyCardUiState>,
    expandedPolicyKeys: List<String>,
    onToggleExpanded: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateNotify: (String) -> Unit,
    onUpdateLogging: (String) -> Unit,
    onUpdatePolicy: (String, Int) -> Unit,
) {
    itemsIndexed(
        items = policies,
        key = { _, it -> it.key },
        contentType = { _, _ -> "policy_item" }
    ) { index, policyItem ->
        val policyKey = policyItem.key
        PolicyItem(
            item = policyItem,
            isExpanded = expandedPolicyKeys.contains(policyKey),
            onToggleExpanded = { onToggleExpanded(policyKey) },
            onDelete = { onDelete(policyKey) },
            onUpdateNotify = { onUpdateNotify(policyKey) },
            onUpdateLogging = { onUpdateLogging(policyKey) },
            onUpdatePolicy = { policy -> onUpdatePolicy(policyKey, policy) },
            modifier = Modifier.zIndex(-index.toFloat())
        )
    }
}

/**
 * 单个策略项组件
 */
@Composable
private fun PolicyItem(
    item: PolicyCardUiState,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDelete: () -> Unit,
    onUpdateNotify: () -> Unit,
    onUpdateLogging: () -> Unit,
    onUpdatePolicy: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardAlpha = if (item.isEnabled) 1f else 0.5f
    // 判断是否已经有明确的 Root 记录（允许或拒绝）
    val hasPolicyRecord = item.policy != SuPolicy.QUERY
    val isAllowed = item.policy >= SuPolicy.ALLOW
    var sliderValue by remember(item.key) { mutableFloatStateOf(policyToSliderValue(item.policy)) }

    LaunchedEffect(item.policy) {
        sliderValue = policyToSliderValue(item.policy)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = cardAlpha },
        cornerRadius = 12.dp,
        onClick = { if (hasPolicyRecord) onToggleExpanded() },
        pressFeedbackType = if (hasPolicyRecord) PressFeedbackType.Sink else PressFeedbackType.None
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    applicationInfo = item.applicationInfo,
                    label = item.appName,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.appName,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.packageName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val policyTag = policyTagText(item.uid, item.isSystemApp)
                    if (policyTag.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = policyTag,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    if (item.showSlider) {
                        Slider(
                            value = sliderValue,
                            onValueChange = { value ->
                                sliderValue = value
                            },
                            onValueChangeFinished = {
                                val snapped = sliderValue.roundToInt().coerceIn(1, 3).toFloat()
                                sliderValue = snapped
                                val newPolicy = sliderValueToPolicy(snapped)
                                if (newPolicy != item.policy) onUpdatePolicy(newPolicy)
                            },
                            valueRange = 1f..3f,
                            steps = 1,
                            modifier = Modifier.width(120.dp)
                        )
                        Text(
                            text = context.getString(policyToTextRes(item.policy)),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Switch(
                            checked = isAllowed,
                            enabled = true,
                            onCheckedChange = { checked ->
                                onUpdatePolicy(if (checked) SuPolicy.ALLOW else SuPolicy.DENY)
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded && hasPolicyRecord,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(260)),
                exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(220))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // 水平排列的三个按钮：通知、日志、撤销
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 通知按钮
                        TextButtonWithIcon(
                            icon = Icons.Filled.Notifications,
                            text = context.getString(CoreR.string.superuser_toggle_notification),
                            isSelected = item.shouldNotify,
                            onClick = onUpdateNotify,
                            modifier = Modifier.weight(1f)
                        )

                        // 垂直分隔线
                        VerticalDivider()

                        // 日志按钮
                        TextButtonWithIcon(
                            icon = Icons.AutoMirrored.Filled.Article,
                            text = context.getString(CoreR.string.logs),
                            isSelected = item.shouldLog,
                            onClick = onUpdateLogging,
                            modifier = Modifier.weight(1f)
                        )

                        // 垂直分隔线
                        VerticalDivider()

                        // 撤销按钮
                        TextButtonWithIcon(
                            icon = MiuixIcons.Delete,
                            text = context.getString(CoreR.string.superuser_toggle_revoke),
                            isError = true,
                            onClick = onDelete,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun policyToSliderValue(policy: Int): Float {
    return when (policy) {
        SuPolicy.DENY -> 1f
        SuPolicy.RESTRICT -> 2f
        SuPolicy.ALLOW -> 3f
        else -> 1f
    }
}

private fun sliderValueToPolicy(value: Float): Int {
    return when (value.roundToInt().coerceIn(1, 3)) {
        1 -> SuPolicy.DENY
        2 -> SuPolicy.RESTRICT
        3 -> SuPolicy.ALLOW
        else -> SuPolicy.DENY
    }
}

private fun policyToTextRes(policy: Int): Int {
    return when {
        policy >= SuPolicy.ALLOW -> CoreR.string.grant
        policy == SuPolicy.RESTRICT -> CoreR.string.restrict
        else -> CoreR.string.deny
    }
}

private fun policyTagText(uid: Int, isSystemApp: Boolean): String {
    return when {
        uid == 0 -> "ROOT"
        uid == Process.SYSTEM_UID -> "SYSTEM"
        uid < Process.FIRST_APPLICATION_UID -> "CUSTOM"
        isSystemApp -> "SYSTEM"
        else -> ""
    }
}

/**
 * 带图标的文本按钮组件
 * 用于卡片展开区域的通知、日志和撤销按钮
 *
 * @param icon 图标
 * @param text 按钮文字
 * @param isSelected 是否选中状态（影响颜色）
 * @param isError 是否错误状态（使用错误色）
 * @param onClick 点击回调
 * @param modifier Modifier
 */
@Composable
private fun TextButtonWithIcon(
    icon: ImageVector,
    text: String,
    isSelected: Boolean = false,
    isError: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = when {
        isError -> MiuixTheme.colorScheme.error
        isSelected -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurfaceContainer
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            color = Color.Transparent
        ),
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = contentColor,
                style = MiuixTheme.textStyles.body2
            )
        }
    }
}

/**
 * 垂直分隔线组件
 * 用于按钮之间的分隔
 *
 * @param modifier Modifier
 */
@Composable
private fun VerticalDivider(
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.VerticalDivider(
        modifier = modifier
            .height(24.dp)
            .padding(horizontal = 4.dp),
        color = MiuixTheme.colorScheme.dividerLine
    )
}