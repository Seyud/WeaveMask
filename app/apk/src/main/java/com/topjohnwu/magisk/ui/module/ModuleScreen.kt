package com.topjohnwu.magisk.ui.module

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Box
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.topjohnwu.magisk.core.R as CoreR
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 模块列表页面
 * 使用纯 Compose 实现，遵循 Miuix 设计哲学
 *
 * @param viewModel 模块 ViewModel
 * @param bottomPadding 底部内边距，用于避免内容被底部导航栏遮挡
 * @param modifier Modifier
 */
@Composable
fun ModuleScreen(
    viewModel: ModuleViewModel,
    bottomPadding: Dp,
    onInstallModuleFromLocal: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState = viewModel.uiState
    var hasStartedLoading by rememberSaveable { mutableStateOf(false) }
    val showTopPopup = remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val localModulePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val copied = runCatching {
                    withContext(Dispatchers.IO) {
                        val originalName = context.contentResolver.query(
                            it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                                )
                            } else null
                        } ?: "install.zip"
                        val cacheDir = File(context.cacheDir, "module_install").apply {
                            deleteRecursively()
                            mkdirs()
                        }
                        val target = File(cacheDir, originalName)
                        val input = context.contentResolver.openInputStream(it)
                            ?: throw IOException("Cannot read selected file")
                        input.use { source ->
                            target.outputStream().use { sink ->
                                source.copyTo(sink)
                            }
                        }
                        target.toUri()
                    }
                }
                copied
                    .onSuccess { localUri ->
                        onInstallModuleFromLocal(localUri)
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: context.getString(CoreR.string.failure),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.surface,
        tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
    )
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(hasStartedLoading) {
        if (!hasStartedLoading) {
            hasStartedLoading = true
            viewModel.startLoading()
        }
    }

    MiuixTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                // 使用 Box 包裹 TopAppBar 来实现 haze 模糊效果
                Box {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .hazeEffect(hazeState) {
                                style = hazeStyle
                                blurRadius = 30.dp
                                noiseFactor = 0f
                            }
                    )
                    TopAppBar(
                        color = Color.Transparent,
                        title = context.getString(CoreR.string.modules),
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
                                        text = "按名称排序",
                                        isSelected = uiState.sortMode == ModuleSortMode.NAME,
                                        optionSize = 3,
                                        onSelectedIndexChange = {
                                            viewModel.setSortMode(ModuleSortMode.NAME)
                                            showTopPopup.value = false
                                        },
                                        index = 0
                                    )
                                    DropdownImpl(
                                        text = "已启用优先",
                                        isSelected = uiState.sortMode == ModuleSortMode.ENABLED_FIRST,
                                        optionSize = 3,
                                        onSelectedIndexChange = {
                                            viewModel.setSortMode(ModuleSortMode.ENABLED_FIRST)
                                            showTopPopup.value = false
                                        },
                                        index = 1
                                    )
                                    DropdownImpl(
                                        text = "可更新优先",
                                        isSelected = uiState.sortMode == ModuleSortMode.UPDATE_FIRST,
                                        optionSize = 3,
                                        onSelectedIndexChange = {
                                            viewModel.setSortMode(ModuleSortMode.UPDATE_FIRST)
                                            showTopPopup.value = false
                                        },
                                        index = 2
                                    )
                                }
                            }

                            IconButton(
                                modifier = Modifier.padding(end = 8.dp),
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

                            RebootListPopup(
                                modifier = Modifier.padding(end = 16.dp),
                                alignment = PopupPositionProvider.Align.TopEnd,
                            )
                        }
                    )
                }
            },
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .padding(paddingValues)
                ) {
                    // 搜索框放在内容区域，带有水平间距
                    InputField(
                        query = uiState.query,
                        onQueryChange = viewModel::setQuery,
                        onSearch = { },
                        expanded = false,
                        onExpandedChange = { },
                        label = "搜索模块",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )

                    when {
                        uiState.isLoading && uiState.modules.isEmpty() -> {
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
                                if (uiState.modules.isEmpty()) {
                                    EmptyContent(
                                        onInstallPressed = {
                                            localModulePicker.launch(
                                                arrayOf("application/zip", "application/octet-stream")
                                            )
                                        }
                                    )
                                } else {
                                    ModuleList(
                                        viewModel = viewModel,
                                        modules = uiState.modules,
                                        onInstallPressed = {
                                            localModulePicker.launch(
                                                arrayOf("application/zip", "application/octet-stream")
                                            )
                                        },
                                        bottomPadding = bottomPadding,
                                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
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
private fun EmptyContent(
    onInstallPressed: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        InstallModuleEntryButton(onClick = onInstallPressed)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = context.getString(CoreR.string.module_empty),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 模块列表
 * 使用 LazyColumn 显示所有模块项
 *
 * @param viewModel 模块 ViewModel
 * @param modules 模块列表
 * @param bottomPadding 底部内边距
 * @param modifier Modifier
 */
@Composable
private fun ModuleList(
    viewModel: ModuleViewModel,
    modules: List<ModuleInfo>,
    onInstallPressed: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            InstallModuleEntryButton(onClick = onInstallPressed)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(
            items = modules,
            key = { it.id }
        ) { module ->
            ModuleItem(
                module = module,
                viewModel = viewModel
            )
        }

        item {
            Spacer(modifier = Modifier.height(bottomPadding))
        }
    }
}

/**
 * 安装模块入口按钮
 */
@Composable
private fun InstallModuleEntryButton(
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors()
    ) {
        Icon(
            imageVector = MiuixIcons.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = context.getString(CoreR.string.module_action_install_external))
    }
}

/**
 * 单个模块项组件
 * 显示模块信息、开关、删除/恢复按钮等
 * 支持点击卡片展开/收起描述
 *
 * @param module 模块信息
 * @param viewModel 模块 ViewModel
 */
@Composable
private fun ModuleItem(
    module: ModuleInfo,
    viewModel: ModuleViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 当模块被移除、显示警告或待重启时，禁用开关
    val isSwitchEnabled = !module.removed && !module.showNotice && !module.updated
    val hasDescription = module.description.isNotEmpty()
    var expanded by rememberSaveable(module.id) { mutableStateOf(false) }

    val textDecoration = if (module.removed) TextDecoration.LineThrough else null
    val cardAlpha = if (module.enabled && !module.removed) 1f else 0.5f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(16.dp),
        onClick = {
            if (hasDescription) expanded = !expanded
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = module.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurface,
                    textDecoration = textDecoration,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${module.version} by ${module.author}",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textDecoration = textDecoration
                )
            }
            Switch(
                checked = module.enabled,
                enabled = isSwitchEnabled,
                onCheckedChange = { checked ->
                    viewModel.toggleModule(module.id, checked)
                }
            )
        }

        if (hasDescription) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = 250,
                            easing = FastOutSlowInEasing
                        )
                    )
            ) {
                Text(
                    text = module.description,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    textDecoration = textDecoration
                )
            }
        }

        if (module.showNotice) {
            Text(
                text = module.noticeText.getText(context.resources).toString(),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
                color = MiuixTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 操作按钮 - 图标+文字样式
            if (module.showAction && module.enabled && !module.removed) {
                val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                val actionIconTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                IconButton(
                    minHeight = 32.dp,
                    minWidth = 32.dp,
                    onClick = { viewModel.runAction(module.id, module.name) },
                    enabled = module.enabled,
                    backgroundColor = secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = MiuixIcons.Play,
                            tint = actionIconTint,
                            contentDescription = context.getString(CoreR.string.module_action)
                        )
                        Text(
                            text = context.getString(CoreR.string.module_action),
                            color = actionIconTint,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 更新按钮 - 图标+文字样式
            if (module.showUpdate) {
                val updateBg = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                val updateTint = MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                IconButton(
                    modifier = Modifier.padding(end = 8.dp),
                    backgroundColor = updateBg,
                    enabled = module.updateReady,
                    minHeight = 32.dp,
                    minWidth = 32.dp,
                    onClick = { viewModel.downloadPressed(module.updateInfo) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = MiuixIcons.UploadCloud,
                            tint = updateTint,
                            contentDescription = context.getString(CoreR.string.update),
                        )
                        Text(
                            text = context.getString(CoreR.string.update),
                            color = updateTint,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 移除/恢复按钮 - 图标+文字样式
            val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
            val actionIconTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            IconButton(
                minHeight = 32.dp,
                minWidth = 32.dp,
                onClick = { viewModel.toggleModuleRemove(module.id) },
                enabled = !module.updated,
                backgroundColor = if (module.removed) {
                    secondaryContainer.copy(alpha = 0.8f)
                } else {
                    secondaryContainer
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = if (module.removed) {
                            MiuixIcons.Undo
                        } else {
                            MiuixIcons.Delete
                        },
                        tint = actionIconTint,
                        contentDescription = null
                    )
                    Text(
                        text = if (module.removed) {
                            context.getString(CoreR.string.module_state_restore)
                        } else {
                            context.getString(CoreR.string.module_state_remove)
                        },
                        color = actionIconTint,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
