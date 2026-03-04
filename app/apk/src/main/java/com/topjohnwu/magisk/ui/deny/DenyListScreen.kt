package com.topjohnwu.magisk.ui.deny

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.databinding.ObservableList
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.magisk.databinding.FilterList
import com.topjohnwu.superuser.Shell
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.topjohnwu.magisk.ui.theme.LocalEnableBlur
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.topjohnwu.magisk.core.R as CoreR

/**
 * DenyList 配置页面
 * 使用纯 Compose 实现排除列表管理界面
 *
 * @param onNavigateBack 返回回调
 * @param modifier Modifier
 */
@Composable
fun DenyListScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DenyListViewModel = viewModel()
    val context = LocalContext.current
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var showOS by rememberSaveable { mutableStateOf(false) }
    var expandedItems by rememberSaveable { mutableStateOf(emptySet<String>()) }
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

    var hasStartedLoading by remember { mutableStateOf(false) }

    LaunchedEffect(hasStartedLoading) {
        if (!hasStartedLoading) {
            hasStartedLoading = true
            viewModel.startLoading()
        }
    }

    LaunchedEffect(query, showSystem, showOS) {
        viewModel.query = query
        viewModel.isShowSystem = showSystem
        viewModel.isShowOS = showOS
    }

    val items = rememberObservableList(viewModel.items)

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
                title = context.getString(CoreR.string.denylist),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 16.dp),
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = {
                            showSystem = !showSystem
                        }
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
                    .then(if (enableBlur) Modifier.hazeSource(state = hazeState) else Modifier)
                    .padding(paddingValues)
            ) {
                SearchBar(
                    inputField = {
                        InputField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = { },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = context.getString(CoreR.string.hide_filter_hint)
                        )
                    },
                    onExpandedChange = { searchExpanded = it },
                    expanded = searchExpanded
                ) {}

                when {
                    viewModel.loading -> {
                        LoadingContent()
                    }
                    items.isEmpty() -> {
                        EmptyContent()
                    }
                    else -> {
                        DenyListContent(
                            items = items,
                            expandedItems = expandedItems,
                            onToggleExpanded = { packageName ->
                                expandedItems = if (expandedItems.contains(packageName)) {
                                    expandedItems - packageName
                                } else {
                                    expandedItems + packageName
                                }
                            },
                            bottomPadding = 16.dp
                        )
                    }
                }
            }
        }
    )
}

/**
 * 将 FilterList 转换为 Compose 可观察的 SnapshotStateList
 * FilterList 的实现类 FilterableDiffObservableList 继承自 DiffObservableList，
 * 而 DiffObservableList 实现了 ObservableList 接口
 */
@Composable
private fun rememberObservableList(filterList: FilterList<DenyListRvItem>): SnapshotStateList<DenyListRvItem> {
    val observableList = filterList as ObservableList<DenyListRvItem>
    val stateList = remember { mutableStateListOf<DenyListRvItem>().apply { addAll(observableList) } }

    DisposableEffect(observableList) {
        val callback = object : ObservableList.OnListChangedCallback<ObservableList<DenyListRvItem>>() {
            override fun onChanged(sender: ObservableList<DenyListRvItem>) {
                stateList.clear()
                stateList.addAll(sender)
            }

            override fun onItemRangeChanged(
                sender: ObservableList<DenyListRvItem>,
                positionStart: Int,
                itemCount: Int
            ) {
                for (i in positionStart until positionStart + itemCount) {
                    if (i < stateList.size && i < sender.size) {
                        stateList[i] = sender[i]
                    }
                }
            }

            override fun onItemRangeInserted(
                sender: ObservableList<DenyListRvItem>,
                positionStart: Int,
                itemCount: Int
            ) {
                for (i in positionStart until positionStart + itemCount) {
                    if (i <= stateList.size) {
                        stateList.add(i, sender[i])
                    }
                }
            }

            override fun onItemRangeMoved(
                sender: ObservableList<DenyListRvItem>,
                fromPosition: Int,
                toPosition: Int,
                itemCount: Int
            ) {
                for (i in 0 until itemCount) {
                    val from = fromPosition + i
                    val to = toPosition + i
                    if (from < stateList.size && to < stateList.size) {
                        val item = stateList.removeAt(from)
                        stateList.add(to, item)
                    }
                }
            }

            override fun onItemRangeRemoved(
                sender: ObservableList<DenyListRvItem>,
                positionStart: Int,
                itemCount: Int
            ) {
                repeat(itemCount) {
                    if (positionStart < stateList.size) {
                        stateList.removeAt(positionStart)
                    }
                }
            }
        }

        observableList.addOnListChangedCallback(callback)
        onDispose {
            observableList.removeOnListChangedCallback(callback)
        }
    }

    return stateList
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
            text = context.getString(CoreR.string.none),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * DenyList 内容列表
 */
@Composable
private fun DenyListContent(
    items: List<DenyListRvItem>,
    expandedItems: Set<String>,
    onToggleExpanded: (String) -> Unit,
    bottomPadding: Dp
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        items(
            items = items,
            key = { it.info.packageName }
        ) { item ->
            DenyListItem(
                item = item,
                isExpanded = expandedItems.contains(item.info.packageName),
                onToggleExpanded = { onToggleExpanded(item.info.packageName) }
            )
        }

        item { Spacer(modifier = Modifier.height(bottomPadding)) }
    }
}

/**
 * 单个应用项组件
 */
@Composable
private fun DenyListItem(
    item: DenyListRvItem,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAlpha = if (item.isChecked) 1f else 0.6f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        cornerRadius = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = item.info.iconImage.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.info.label,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.info.packageName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (isExpanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceContainer
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    item.processes.forEach { processItem ->
                        ProcessItemRow(
                            processItem = processItem,
                            packageName = item.info.packageName
                        )
                    }
                }
            }
        }
    }
}

/**
 * 进程项行组件
 */
@Composable
private fun ProcessItemRow(
    processItem: ProcessRvItem,
    packageName: String,
    modifier: Modifier = Modifier
) {
    var isEnabled by remember { mutableStateOf(processItem.isEnabled) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                isEnabled = !isEnabled
                processItem.toggle()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = { checked ->
                isEnabled = checked
                if (checked) {
                    val name = processItem.process.name
                    Shell.cmd("magisk --denylist add $packageName '$name'").submit()
                    processItem.isEnabled = true
                } else {
                    processItem.toggle()
                }
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = processItem.displayName,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}
