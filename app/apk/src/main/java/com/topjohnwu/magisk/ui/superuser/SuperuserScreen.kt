package com.topjohnwu.magisk.ui.superuser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Article
import android.os.Process
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.dialog.SuperuserRevokeDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.topjohnwu.magisk.ui.theme.LocalEnableBlur
import com.topjohnwu.magisk.core.R as CoreR
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.roundToInt

/**
 * 超级用户页面
 * 使用 Compose 实现超级用户授权管理界面
 *
 * @param viewModel 超级用户 ViewModel
 * @param bottomPadding 底部内边距，用于避免内容被底部导航栏遮挡
 * @param modifier Modifier
 */
@Composable
fun SuperuserScreen(
    viewModel: SuperuserViewModel,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var hasStartedLoading by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedPolicyKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val showTopPopup = remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val hazeState = remember { HazeState() }
    val hazeStyle = if (enableBlur) {
        HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
        )
    } else {
        HazeStyle.Unspecified
    }

    // 撤销对话框状态
    val revokeDialogState = uiState.revokeDialogState
    var pendingRevokeKey by rememberSaveable { mutableStateOf<String?>(null) }

    // 监听生命周期，当页面从后台返回前台时刷新数据
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // 初始加载
        if (!hasStartedLoading) {
            hasStartedLoading = true
            viewModel.startLoading()
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
            topBar = {
                TopAppBar(
                    modifier = if (enableBlur) {
                        Modifier.hazeEffect(hazeState) {
                            style = hazeStyle
                            blurRadius = 30.dp
                            noiseFactor = 0f
                        }
                    } else Modifier,
                    color = if (enableBlur) Color.Transparent else MiuixTheme.colorScheme.surface,
                    title = context.getString(CoreR.string.superuser),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        SuperListPopup(
                            show = showTopPopup,
                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = {
                                showTopPopup.value = false
                            }
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = if (uiState.showSystemApps) "隐藏系统应用" else "显示系统应用",
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
                            modifier = Modifier.padding(end = 16.dp),
                            onClick = {
                                showTopPopup.value = true
                            },
                            holdDownState = showTopPopup.value
                        ) {
                            Icon(
                                imageVector = MiuixIcons.MoreCircle,
                                contentDescription = null
                            )
                        }
                    }
                )
            },
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    SearchBar(
                        inputField = {
                            InputField(
                                query = uiState.query,
                                onQueryChange = viewModel::setQuery,
                                onSearch = { },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = "搜索应用"
                            )
                        },
                        insideMargin = DpSize(12.dp, 12.dp),
                        onExpandedChange = { searchExpanded = it },
                        expanded = searchExpanded
                    ) {
                    }

                    when {
                        uiState.isLoading && uiState.policies.isEmpty() -> {
                            LoadingContent()
                        }
                        else -> {
                            PullToRefresh(
                                isRefreshing = uiState.isRefreshing,
                                pullToRefreshState = pullToRefreshState,
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
                                if (uiState.policies.isEmpty()) {
                                    EmptyContent()
                                } else {
                                    PolicyList(
                                        policies = uiState.policies,
                                        viewModel = viewModel,
                                        bottomPadding = bottomPadding,
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
                                        nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                                        hazeState = hazeState
                                    )
                                }
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
private fun LoadingContent() {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = context.getString(CoreR.string.loading),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator()
        }
    }
}

/**
 * 空状态显示
 */
@Composable
private fun EmptyContent() {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = context.getString(CoreR.string.superuser_policy_none),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 策略列表
 *
 * @param policies 策略列表
 * @param viewModel 超级用户 ViewModel
 * @param bottomPadding 底部内边距
 * @param onDelete 删除回调
 * @param nestedScrollConnection 嵌套滚动连接
 * @param hazeState Haze 模糊状态
 */
@Composable
private fun PolicyList(
    policies: List<PolicyCardUiState>,
    viewModel: SuperuserViewModel,
    bottomPadding: Dp,
    expandedPolicyKeys: List<String>,
    onToggleExpanded: (String) -> Unit,
    onDelete: (String) -> Unit,
    nestedScrollConnection: NestedScrollConnection,
    hazeState: HazeState
) {
    val enableBlur = LocalEnableBlur.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .then(if (enableBlur) Modifier.hazeSource(state = hazeState) else Modifier)
            .nestedScroll(nestedScrollConnection),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        items(
            items = policies,
            key = { it.key }
        ) { policyItem ->
            val policyKey = policyItem.key
            PolicyItem(
                item = policyItem,
                isExpanded = expandedPolicyKeys.contains(policyKey),
                onToggleExpanded = { onToggleExpanded(policyKey) },
                onDelete = { onDelete(policyKey) },
                onUpdateNotify = { viewModel.toggleNotifyByKey(policyKey) },
                onUpdateLogging = { viewModel.toggleLogByKey(policyKey) },
                onUpdatePolicy = { policy -> viewModel.updatePolicyByKey(policyKey, policy) }
            )
        }

        // 底部间距 - 使用传入的 bottomPadding 确保最后一个卡片内容可以正常显示
        item {
            Spacer(modifier = Modifier.height(bottomPadding))
        }
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
    val isAllowed = item.policy >= SuPolicy.ALLOW
    var sliderValue by remember(item.key) { mutableFloatStateOf(policyToSliderValue(item.policy)) }

    LaunchedEffect(item.policy) {
        sliderValue = policyToSliderValue(item.policy)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        cornerRadius = 12.dp,
        onClick = onToggleExpanded,
        pressFeedbackType = PressFeedbackType.Sink
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
                androidx.compose.foundation.Image(
                    bitmap = item.icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
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

                    val policyTag = policyTagText(item.uid)
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
                visible = isExpanded,
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
                            icon = Icons.Filled.Article,
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

private fun policyTagText(uid: Int): String {
    return when {
        uid == 0 -> "ROOT"
        uid == Process.SYSTEM_UID -> "SYSTEM"
        uid < Process.FIRST_APPLICATION_UID -> "CUSTOM"
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
