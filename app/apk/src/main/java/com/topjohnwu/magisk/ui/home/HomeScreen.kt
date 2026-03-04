package com.topjohnwu.magisk.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.painterResource
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.topjohnwu.magisk.ui.theme.LocalEnableBlur
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import android.widget.Toast
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.dialog.EnvFixDialog
import com.topjohnwu.magisk.dialog.UninstallDialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.R as CoreR
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.utils.TextHolder
import com.topjohnwu.magisk.ui.module.RebootListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

/**
 * 主页面屏幕
 * 显示 Magisk 状态、管理器信息和开发者链接
 *
 * @param viewModel 主页 ViewModel
 * @param bottomPadding 底部内边距，用于避免内容被底部导航栏遮挡
 * @param modifier Modifier
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    bottomPadding: Dp,
    onNavigateToInstall: () -> Unit,
    onNavigateToUninstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasStartedLoading by rememberSaveable { mutableStateOf(false) }
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

    // 仅首次进入时触发加载，避免每次返回主页都触发重任务导致转场掉帧
    LaunchedEffect(hasStartedLoading) {
        if (!hasStartedLoading) {
            hasStartedLoading = true
            viewModel.startLoading()
        }
    }

    // EnvFixDialog
    EnvFixDialog(
        state = viewModel.envFixDialogState,
        context = context,
        onDismiss = { viewModel.dismissEnvFixDialog() },
        onNavigateToInstall = { viewModel.navigateToInstall() }
    )

    // UninstallDialog
    UninstallDialog(
        state = viewModel.uninstallDialogState,
        context = context,
        onDismiss = { viewModel.dismissUninstallDialog() },
        onRestoreImg = {
            viewModel.startRestoreImg()
            // 启动协程执行恢复镜像操作
            coroutineScope.launch {
                com.topjohnwu.magisk.core.tasks.MagiskInstaller.Restore().exec { success ->
                    viewModel.dismissUninstallDialog()
                    context.toast(
                        if (success) CoreR.string.restore_done else CoreR.string.restore_fail,
                        if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    )
                }
            }
        },
        onCompleteUninstall = {
            viewModel.dismissUninstallDialog()
            onNavigateToUninstall()
        }
    )

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
                title = context.getString(CoreR.string.section_home),
                scrollBehavior = scrollBehavior,
                actions = {
                    // 添加电源重启弹出菜单
                    RebootListPopup(
                        modifier = Modifier.padding(end = 16.dp),
                        alignment = PopupPositionProvider.Align.TopEnd,
                    )
                }
            )
        },
        popupHost = { }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .then(if (enableBlur) Modifier.hazeSource(state = hazeState) else Modifier)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null
        ) {
            // 通知卡片 - 使用 AnimatedVisibility 添加消失动画
            item {
                AnimatedVisibility(
                    visible = viewModel.isNoticeVisible,
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = 300)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 300)
                    )
                ) {
                    NoticeCard(
                        onHide = { viewModel.hideNotice() }
                    )
                }
            }

            // Core 板块
            item {
                MagiskCard(
                    magiskState = viewModel.magiskState,
                    installedVersion = viewModel.magiskInstalledVersion.toString(),
                    onPressed = onNavigateToInstall
                )
            }

            // 卸载按钮（红色警告色）
            if (Info.env.isActive) {
                item {
                    UninstallButton(
                        onPressed = { viewModel.onDeletePressed() }
                    )
                }
            }

            // Zygisk & Ramdisk 并排卡片
            item {
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ZygiskCard(
                        isEnabled = Info.isZygiskEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RamdiskCard(
                        isAvailable = Info.ramdisk,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // App 板块
            item {
                ManagerCard(
                    appState = viewModel.appState,
                    remoteVersion = viewModel.managerRemoteVersion.getText(context.resources).toString(),
                    installedVersion = viewModel.managerInstalledVersion,
                    packageName = viewModel.managerPackageName,
                    progress = viewModel.stateManagerProgress,
                    onPressed = { viewModel.onManagerPressed() }
                )
            }

            // 支持开发板块
            item {
                SupportCard(
                    onLinkPressed = { link ->
                        viewModel.onLinkPressed(link)
                    }
                )
            }

            // 贡献者板块
            item {
                FollowCard(
                    onLinkPressed = { link ->
                        viewModel.onLinkPressed(link)
                    }
                )
            }

            // 底部间距 - 使用传入的 bottomPadding 确保最后一个卡片内容可以正常显示，不会被导航栏遮挡
            item {
                Spacer(modifier = Modifier.height(bottomPadding))
            }
        }
    }
}

/**
 * 通知卡片
 * 显示安全提示信息
 */
@Composable
private fun NoticeCard(
    onHide: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primary,
            contentColor = MiuixTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(CoreR.string.home_notice_content),
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onPrimary,
                style = MiuixTheme.textStyles.body1
            )
            TextButton(
                text = context.getString(CoreR.string.hide),
                onClick = onHide,
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.onPrimary,
                    color = Color.Transparent
                )
            )
        }
    }
}

/**
 * Magisk 核心状态卡片
 * 显示 Magisk 版本、运行状态和操作按钮
 */
@Composable
private fun MagiskCard(
    magiskState: HomeViewModel.State,
    installedVersion: String,
    onPressed: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
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
                Image(
                    painter = painterResource(id = CoreR.drawable.ic_magisk_outline),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(CoreR.string.magisk),
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusLabel(state = magiskState)
                }

                when (magiskState) {
                    HomeViewModel.State.OUTDATED -> {
                        Surface(
                            onClick = onPressed,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Update,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = context.getString(CoreR.string.update),
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    HomeViewModel.State.LOADING -> {
                        CircularProgressIndicator(
                            size = 24.dp,
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {
                        Surface(
                            onClick = onPressed,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Download,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = context.getString(CoreR.string.install),
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

/**
 * 状态标签
 * 根据不同状态显示不同颜色的标签
 */
@Composable
private fun StatusLabel(state: HomeViewModel.State) {
    val context = LocalContext.current
    val (text, color) = when (state) {
        HomeViewModel.State.UP_TO_DATE -> context.getString(CoreR.string.home_latest_version) to MiuixTheme.colorScheme.primary
        HomeViewModel.State.OUTDATED -> context.getString(CoreR.string.update) to MiuixTheme.colorScheme.error
        HomeViewModel.State.INVALID -> context.getString(CoreR.string.not_available) to MiuixTheme.colorScheme.onSurfaceContainer
        HomeViewModel.State.LOADING -> context.getString(CoreR.string.loading) to MiuixTheme.colorScheme.onSurfaceContainer
    }
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

/**
 * Zygisk 状态卡片
 * 显示 Zygisk 启用/禁用状态 - 使用扁形水平布局
 */
@Composable
private fun ZygiskCard(
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusColor = if (isEnabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainer

    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Pin,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = context.getString(CoreR.string.zygisk),
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isEnabled) context.getString(CoreR.string.yes) else context.getString(CoreR.string.no),
                style = MiuixTheme.textStyles.body2,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Ramdisk 状态卡片
 * 显示 Ramdisk 可用/不可用状态 - 使用扁形水平布局
 */
@Composable
private fun RamdiskCard(
    isAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusColor = if (isAvailable) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainer

    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Backup,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ramdisk",
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isAvailable) context.getString(CoreR.string.yes) else context.getString(CoreR.string.no),
                style = MiuixTheme.textStyles.body2,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 管理器卡片
 * 显示管理器应用版本和更新状态
 */
@Composable
private fun ManagerCard(
    appState: HomeViewModel.State,
    remoteVersion: String,
    installedVersion: String,
    packageName: String,
    progress: Int,
    onPressed: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_manager),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = context.getString(CoreR.string.home_app_title),
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                when (appState) {
                    HomeViewModel.State.OUTDATED -> {
                        Button(
                            onClick = onPressed,
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Update,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = context.getString(CoreR.string.update),
                                color = MiuixTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    HomeViewModel.State.UP_TO_DATE -> {
                        Button(onClick = onPressed) {
                            Icon(
                                imageVector = MiuixIcons.Download,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSecondaryVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = context.getString(CoreR.string.install),
                                color = MiuixTheme.colorScheme.onSecondaryVariant
                            )
                        }
                    }
                    HomeViewModel.State.LOADING -> {
                        // 加载状态不显示转圈图标
                    }
                    HomeViewModel.State.INVALID -> {
                        // 无网络或加载失败时显示错误提示
                        Text(
                            text = context.getString(CoreR.string.no_connection),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 最新版本显示远程版本号
                HomeItemRow(
                    label = context.getString(CoreR.string.home_latest_version),
                    value = remoteVersion
                )
                // 已安装版本
                HomeItemRow(
                    label = context.getString(CoreR.string.home_installed_version),
                    value = installedVersion
                )
                // 包名
                HomeItemRow(
                    label = context.getString(CoreR.string.home_package),
                    value = packageName
                )
            }

            if (progress > 0 && progress < 100) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 卸载按钮
 * 用于卸载 Magisk，使用 Error Container 样式
 */
@Composable
private fun UninstallButton(
    onPressed: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        onClick = onPressed,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        color = MiuixTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MiuixTheme.colorScheme.error),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = context.getString(CoreR.string.uninstall_magisk_title),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * 支持卡片
 * 显示捐赠链接
 */
@Composable
private fun SupportCard(
    onLinkPressed: (String) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = context.getString(CoreR.string.home_support_title),
                style = MiuixTheme.textStyles.title3
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = context.getString(CoreR.string.home_support_content),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Patreon 图标
                Box(
                    modifier = Modifier
                        .clickable { onLinkPressed(IconLink.Patreon.link) }
                        .padding(8.dp)
                        .size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = CoreR.drawable.ic_patreon),
                        contentDescription = context.getString(CoreR.string.patreon),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                        modifier = Modifier.size(24.dp)
                    )
                }
                // PayPal 图标
                Box(
                    modifier = Modifier
                        .clickable { onLinkPressed(IconLink.PayPal.Project.link) }
                        .padding(8.dp)
                        .size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = CoreR.drawable.ic_paypal),
                        contentDescription = context.getString(CoreR.string.paypal),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * 关注卡片
 * 显示开发者链接
 */
@Composable
private fun FollowCard(
    onLinkPressed: (String) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = context.getString(CoreR.string.home_follow_title),
                style = MiuixTheme.textStyles.title3
            )

            Spacer(modifier = Modifier.height(8.dp))

            DeveloperItem(
                handle = DeveloperItem.John.handle,
                links = DeveloperItem.John.items,
                onLinkPressed = onLinkPressed
            )

            DeveloperItem(
                handle = DeveloperItem.Vvb.handle,
                links = DeveloperItem.Vvb.items,
                onLinkPressed = onLinkPressed
            )

            DeveloperItem(
                handle = DeveloperItem.YU.handle,
                links = DeveloperItem.YU.items,
                onLinkPressed = onLinkPressed
            )

            DeveloperItem(
                handle = DeveloperItem.Seyud.handle,
                links = DeveloperItem.Seyud.items,
                onLinkPressed = onLinkPressed
            )

            DeveloperItem(
                handle = DeveloperItem.Rikka.handle,
                links = DeveloperItem.Rikka.items,
                onLinkPressed = onLinkPressed
            )

            DeveloperItem(
                handle = DeveloperItem.Canyie.handle,
                links = DeveloperItem.Canyie.items,
                onLinkPressed = onLinkPressed
            )
        }
    }
}

/**
 * 主页信息行
 * 显示标签和值
 */
@Composable
private fun HomeItemRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}

/**
 * 图标链接项
 * 可点击的图标按钮
 */
@Composable
private fun IconLinkItem(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = MiuixIcons.Download,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 开发者项
 * 显示开发者名称和社交链接
 */
@Composable
private fun DeveloperItem(
    handle: String,
    links: List<IconLink>,
    onLinkPressed: (String) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = handle,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(100.dp)
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            links.forEach { link ->
                val iconRes = when (link) {
                    is IconLink.Patreon -> CoreR.drawable.ic_patreon
                    is IconLink.PayPal -> CoreR.drawable.ic_paypal
                    is IconLink.Twitter -> CoreR.drawable.ic_twitter
                    is IconLink.Github -> CoreR.drawable.ic_github
                    is IconLink.Sponsor -> CoreR.drawable.ic_favorite
                    else -> CoreR.drawable.ic_more
                }

                IconButton(
                    onClick = { onLinkPressed(link.link) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = context.getString(link.title),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
