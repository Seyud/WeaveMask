package com.topjohnwu.magisk.ui.settings

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AppBlocking
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.isRunningAsStub
import com.topjohnwu.magisk.core.utils.LocaleSetting
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.topjohnwu.magisk.core.R as CoreR

/**
 * 设置页面主屏幕
 * 使用 Miuix 组件实现符合 MIUI 风格的设置列表界面
 *
 * @param viewModel 设置 ViewModel
 * @param bottomPadding 底部内边距，用于避免内容被底部导航栏遮挡
 * @param onNavigateToLog 导航到日志页面的回调
 * @param modifier Modifier
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    bottomPadding: Dp,
    onNavigateToLog: () -> Unit,
    onNavigateToAppLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val res = context.resources
    val dummyView = remember { createDummyView(context) }
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.surface,
        tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
    )

    LaunchedEffect(onNavigateToLog) {
        viewModel.onNavigateToLog = onNavigateToLog
    }

    // 预先计算条件可见性
    val hidden = context.packageName != BuildConfig.APP_PACKAGE_NAME
    val showAddShortcut = isRunningAsStub &&
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    val showMagiskSection = Info.env.isActive
    val showZygisk = showMagiskSection && Const.Version.atLeast_24_0()
    val showSuperuserSection = Info.showSuperUser
    val showHideRestore = Info.env.isActive && Const.USER_ID == 0
    val showTapjack = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val showReauthenticate = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
    val showRestrict = Const.Version.atLeast_30_1()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.hazeEffect(hazeState) {
                    style = hazeStyle
                    blurRadius = 30.dp
                    noiseFactor = 0f
                },
                color = Color.Transparent,
                title = stringResource(CoreR.string.settings),
                scrollBehavior = scrollBehavior
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
                .hazeSource(state = hazeState)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null
        ) {
            // ==================== 日志入口 ====================
            item {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    SuperArrow(
                        title = stringResource(CoreR.string.logs),
                        startAction = {
                            Icon(
                                Icons.Rounded.BugReport,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = {
                            viewModel.onItemPressed(dummyView, Logs) {
                                viewModel.onItemAction(dummyView, Logs)
                            }
                        }
                    )
                }
            }

            // ==================== 自定义 ====================
            item {
                SmallTitle(text = stringResource(CoreR.string.settings_customization))
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 主题模式
                    val themeItems = listOf(
                        stringResource(CoreR.string.settings_theme_mode_system),
                        stringResource(CoreR.string.settings_theme_mode_light),
                        stringResource(CoreR.string.settings_theme_mode_dark),
                        stringResource(CoreR.string.settings_theme_mode_monet_system),
                        stringResource(CoreR.string.settings_theme_mode_monet_light),
                        stringResource(CoreR.string.settings_theme_mode_monet_dark),
                    )
                    var themeMode by remember { mutableIntStateOf(Config.colorMode) }
                    SuperDropdown(
                        title = stringResource(CoreR.string.settings_theme),
                        summary = stringResource(CoreR.string.settings_theme_summary),
                        items = themeItems,
                        startAction = {
                            Icon(
                                Icons.Rounded.Palette,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        },
                        selectedIndex = themeMode,
                        onSelectedIndexChange = { index ->
                            Config.colorMode = index
                            themeMode = index
                        }
                    )

                    // 种子色（仅 Monet 模式下显示）
                    AnimatedVisibility(
                        visible = themeMode in 3..5
                    ) {
                        val colorItems = listOf(
                            stringResource(CoreR.string.settings_key_color_default),
                            stringResource(CoreR.string.color_red),
                            stringResource(CoreR.string.color_pink),
                            stringResource(CoreR.string.color_purple),
                            stringResource(CoreR.string.color_deep_purple),
                            stringResource(CoreR.string.color_indigo),
                            stringResource(CoreR.string.color_blue),
                            stringResource(CoreR.string.color_cyan),
                            stringResource(CoreR.string.color_teal),
                            stringResource(CoreR.string.color_green),
                            stringResource(CoreR.string.color_yellow),
                            stringResource(CoreR.string.color_amber),
                            stringResource(CoreR.string.color_orange),
                            stringResource(CoreR.string.color_brown),
                            stringResource(CoreR.string.color_blue_grey),
                            stringResource(CoreR.string.color_sakura),
                        )
                        val colorValues = listOf(
                            0,
                            Color(0xFFF44336).toArgb(),
                            Color(0xFFE91E63).toArgb(),
                            Color(0xFF9C27B0).toArgb(),
                            Color(0xFF673AB7).toArgb(),
                            Color(0xFF3F51B5).toArgb(),
                            Color(0xFF2196F3).toArgb(),
                            Color(0xFF00BCD4).toArgb(),
                            Color(0xFF009688).toArgb(),
                            Color(0xFF4FAF50).toArgb(),
                            Color(0xFFFFEB3B).toArgb(),
                            Color(0xFFFFC107).toArgb(),
                            Color(0xFFFF9800).toArgb(),
                            Color(0xFF795548).toArgb(),
                            Color(0xFF607D8F).toArgb(),
                            Color(0xFFFF9CA8).toArgb(),
                        )
                        var keyColorIndex by remember {
                            mutableIntStateOf(
                                colorValues.indexOf(Config.keyColor).takeIf { it >= 0 } ?: 0
                            )
                        }
                        SuperDropdown(
                            title = stringResource(CoreR.string.settings_key_color),
                            summary = stringResource(CoreR.string.settings_key_color_summary),
                            items = colorItems,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = keyColorIndex,
                            onSelectedIndexChange = { index ->
                                Config.keyColor = colorValues[index]
                                keyColorIndex = index
                            }
                        )
                    }

                    // 语言
                    SuperArrow(
                        title = stringResource(CoreR.string.language),
                        summary = appLanguageSummary(res),
                        startAction = {
                            Icon(
                                Icons.Rounded.Language,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = onNavigateToAppLanguage
                    )

                    // 添加桌面快捷方式
                    if (showAddShortcut) {
                        SuperArrow(
                            title = stringResource(CoreR.string.add_shortcut_title),
                            summary = stringResource(CoreR.string.setting_add_shortcut_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.AddToHomeScreen,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = {
                                viewModel.onItemPressed(dummyView, AddShortcut) {
                                    viewModel.onItemAction(dummyView, AddShortcut)
                                }
                            }
                        )
                    }
                }
            }

            // ==================== 应用设置 ====================
            item {
                SmallTitle(text = stringResource(CoreR.string.home_app_title))
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 更新通道
                    UpdateChannelSelectorItem(
                        res = res,
                        dummyView = dummyView,
                        viewModel = viewModel
                    )

                    // 自定义更新通道 URL
                    UpdateChannelUrl.refresh()
                    if (UpdateChannelUrl.isEnabled) {
                        SuperArrow(
                            title = stringResource(CoreR.string.settings_update_custom),
                            summary = UpdateChannelUrl.description.getText(res).toString()
                                .takeIf { it.isNotEmpty() },
                            startAction = {
                                Icon(
                                    Icons.Rounded.Link,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = {
                                UpdateChannelUrl.onPressed(dummyView, viewModel)
                            }
                        )
                    }

                    // DNS over HTTPS
                    SuperSwitch(
                        title = stringResource(CoreR.string.settings_doh_title),
                        summary = stringResource(CoreR.string.settings_doh_description),
                        checked = DoHToggle.isChecked,
                        onCheckedChange = {
                            DoHToggle.onToggle(dummyView, viewModel, it)
                        },
                        startAction = {
                            Icon(
                                Icons.Rounded.Dns,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        },
                        enabled = DoHToggle.isEnabled
                    )

                    // 检查更新
                    SuperSwitch(
                        title = stringResource(CoreR.string.settings_check_update_title),
                        summary = stringResource(CoreR.string.settings_check_update_summary),
                        checked = UpdateChecker.isChecked,
                        onCheckedChange = {
                            UpdateChecker.onToggle(dummyView, viewModel, it)
                        },
                        startAction = {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        },
                        enabled = UpdateChecker.isEnabled
                    )

                    // 下载路径
                    SuperArrow(
                        title = stringResource(CoreR.string.settings_download_path_title),
                        summary = DownloadPath.description.getText(res).toString()
                            .takeIf { it.isNotEmpty() },
                        startAction = {
                            Icon(
                                Icons.Rounded.FolderOpen,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = {
                            DownloadPath.onPressed(dummyView, viewModel)
                        },
                        enabled = DownloadPath.isEnabled
                    )

                    // 随机文件名
                    SuperSwitch(
                        title = stringResource(CoreR.string.settings_random_name_title),
                        summary = stringResource(CoreR.string.settings_random_name_description),
                        checked = RandNameToggle.isChecked,
                        onCheckedChange = {
                            RandNameToggle.onToggle(dummyView, viewModel, it)
                        },
                        startAction = {
                            Icon(
                                Icons.Rounded.Shuffle,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        },
                        enabled = RandNameToggle.isEnabled
                    )

                    // 隐藏/恢复 Magisk app
                    if (showHideRestore) {
                        if (hidden) {
                            SuperArrow(
                                title = stringResource(CoreR.string.settings_restore_app_title),
                                summary = stringResource(CoreR.string.settings_restore_app_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Restore,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = {
                                    Restore.onPressed(dummyView, viewModel)
                                }
                            )
                        } else {
                            SuperArrow(
                                title = stringResource(CoreR.string.settings_hide_app_title),
                                summary = stringResource(CoreR.string.settings_hide_app_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.VisibilityOff,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = {
                                    Hide.onPressed(dummyView, viewModel)
                                }
                            )
                        }
                    }
                }
            }

            // ==================== Magisk ====================
            if (showMagiskSection) {
                item {
                    SmallTitle(text = stringResource(CoreR.string.magisk))
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Systemless Hosts
                        SuperArrow(
                            title = stringResource(CoreR.string.settings_hosts_title),
                            summary = stringResource(CoreR.string.settings_hosts_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Storage,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = {
                                viewModel.onItemPressed(dummyView, SystemlessHosts) {
                                    viewModel.onItemAction(dummyView, SystemlessHosts)
                                }
                            }
                        )

                        if (showZygisk) {
                            // Zygisk
                            SuperSwitch(
                                title = stringResource(CoreR.string.zygisk),
                                summary = Zygisk.description.getText(res).toString()
                                    .takeIf { it.isNotEmpty() },
                                checked = Zygisk.isChecked,
                                onCheckedChange = {
                                    Zygisk.onToggle(dummyView, viewModel, it)
                                },
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Memory,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                }
                            )

                            // DenyList
                            SuperSwitch(
                                title = stringResource(CoreR.string.settings_denylist_title),
                                summary = stringResource(CoreR.string.settings_denylist_summary),
                                checked = DenyList.isChecked,
                                onCheckedChange = {
                                    DenyList.onToggle(dummyView, viewModel, it)
                                },
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Block,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                }
                            )

                            // DenyList Config
                            SuperArrow(
                                title = stringResource(CoreR.string.settings_denylist_config_title),
                                summary = stringResource(CoreR.string.settings_denylist_config_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Settings,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = {
                                    viewModel.onItemPressed(dummyView, DenyListConfig) {
                                        viewModel.onItemAction(dummyView, DenyListConfig)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ==================== 超级用户 ====================
            if (showSuperuserSection) {
                item {
                    SmallTitle(text = stringResource(CoreR.string.superuser))
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 防窗口覆盖
                        if (showTapjack) {
                            SuperSwitch(
                                title = stringResource(CoreR.string.settings_su_tapjack_title),
                                summary = stringResource(CoreR.string.settings_su_tapjack_summary),
                                checked = Tapjack.isChecked,
                                onCheckedChange = {
                                    Tapjack.onToggle(dummyView, viewModel, it)
                                },
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Security,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                enabled = Tapjack.isEnabled
                            )
                        }

                        // 生物认证
                        Authentication.refresh()
                        SuperSwitch(
                            title = stringResource(CoreR.string.settings_su_auth_title),
                            summary = Authentication.description.getText(res).toString()
                                .takeIf { it.isNotEmpty() },
                            checked = Authentication.isChecked,
                            onCheckedChange = {
                                Authentication.onToggle(dummyView, viewModel, it)
                            },
                            startAction = {
                                Icon(
                                    Icons.Rounded.Fingerprint,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground
                                )
                            },
                            enabled = Authentication.isEnabled
                        )

                        // Root 访问模式
                        AccessModeSelectorItem(
                            res = res,
                            dummyView = dummyView,
                            viewModel = viewModel
                        )

                        // 多用户模式
                        MultiuserMode.refresh()
                        MultiuserModeSelectorItem(
                            res = res,
                            dummyView = dummyView,
                            viewModel = viewModel
                        )

                        // 挂载命名空间模式
                        MountNamespaceModeSelectorItem(
                            res = res,
                            dummyView = dummyView,
                            viewModel = viewModel
                        )

                        // 自动响应
                        AutomaticResponseSelectorItem(
                            res = res,
                            dummyView = dummyView,
                            viewModel = viewModel
                        )

                        // 请求超时
                        RequestTimeoutSelectorItem(
                            res = res,
                            dummyView = dummyView,
                            viewModel = viewModel
                        )

                        // Root 通知
                        SUNotificationSelectorItem(
                            res = res,
                            dummyView = dummyView,
                            viewModel = viewModel
                        )

                        // 重新认证
                        if (showReauthenticate) {
                            Reauthenticate.refresh()
                            SuperSwitch(
                                title = stringResource(CoreR.string.settings_su_reauth_title),
                                summary = stringResource(CoreR.string.settings_su_reauth_summary),
                                checked = Reauthenticate.isChecked,
                                onCheckedChange = {
                                    Reauthenticate.onToggle(dummyView, viewModel, it)
                                },
                                startAction = {
                                    Icon(
                                        Icons.Rounded.LockReset,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                enabled = Reauthenticate.isEnabled
                            )
                        }

                        // 限制前台应用
                        if (showRestrict) {
                            SuperSwitch(
                                title = stringResource(CoreR.string.settings_su_restrict_title),
                                summary = stringResource(CoreR.string.settings_su_restrict_summary),
                                checked = Restrict.isChecked,
                                onCheckedChange = {
                                    Restrict.onToggle(dummyView, viewModel, it)
                                },
                                startAction = {
                                    Icon(
                                        Icons.Rounded.AppBlocking,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                enabled = Restrict.isEnabled
                            )
                        }
                    }
                }
            }

            // 底部留白 - 使用传入的 bottomPadding 确保最后一个卡片内容可以正常显示
            item {
                Spacer(Modifier.height(bottomPadding))
            }
        }
    }
}

/**
 * 创建虚拟 View 用于兼容旧的 View 回调
 */
private fun createDummyView(context: Context): View {
    return FrameLayout(context)
}

// ==================== Selector 类型组件（使用 SuperDropdown 行内选择） ====================

private fun appLanguageSummary(res: Resources): String {
    val locale = LocaleSetting.instance.appLocale
    if (locale != null) {
        return locale.getDisplayName(locale)
    }
    return res.getString(CoreR.string.system_default)
}

/**
 * 更新通道选择器
 */
@Composable
private fun UpdateChannelSelectorItem(
    res: Resources,
    dummyView: View,
    viewModel: SettingsViewModel
) {
    val entries = UpdateChannel.entries(res)
    var selected by remember { mutableIntStateOf(UpdateChannel.value) }

    SuperDropdown(
        title = stringResource(CoreR.string.settings_update_channel_title),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            UpdateChannel.selectValue(index, dummyView, viewModel)
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.Update,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        },
        enabled = UpdateChannel.isEnabled
    )
}

/**
 * Root 访问模式选择器
 */
@Composable
private fun AccessModeSelectorItem(
    res: Resources,
    dummyView: View,
    viewModel: SettingsViewModel
) {
    val entries = AccessMode.entries(res)
    var selected by remember { mutableIntStateOf(AccessMode.value) }

    SuperDropdown(
        title = stringResource(CoreR.string.superuser_access),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            AccessMode.selectValue(index, dummyView, viewModel)
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.AdminPanelSettings,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        },
        enabled = AccessMode.isEnabled
    )
}

/**
 * 多用户模式选择器
 */
@Composable
private fun MultiuserModeSelectorItem(
    res: Resources,
    dummyView: View,
    viewModel: SettingsViewModel
) {
    val entries = MultiuserMode.entries(res)
    var selected by remember { mutableIntStateOf(MultiuserMode.value) }

    SuperDropdown(
        title = stringResource(CoreR.string.multiuser_mode),
        summary = MultiuserMode.description.getText(res).toString()
            .takeIf { it.isNotEmpty() },
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            MultiuserMode.selectValue(index, dummyView, viewModel)
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.Group,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        },
        enabled = MultiuserMode.isEnabled
    )
}

/**
 * 挂载命名空间模式选择器
 */
@Composable
private fun MountNamespaceModeSelectorItem(
    res: Resources,
    dummyView: View,
    viewModel: SettingsViewModel
) {
    val entries = MountNamespaceMode.entries(res)
    var selected by remember { mutableIntStateOf(MountNamespaceMode.value) }

    SuperDropdown(
        title = stringResource(CoreR.string.mount_namespace_mode),
        summary = MountNamespaceMode.description.getText(res).toString()
            .takeIf { it.isNotEmpty() },
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            MountNamespaceMode.selectValue(index, dummyView, viewModel)
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.FolderSpecial,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        },
        enabled = MountNamespaceMode.isEnabled
    )
}

/**
 * 自动响应选择器
 */
@Composable
private fun AutomaticResponseSelectorItem(
    res: Resources,
    dummyView: View,
    viewModel: SettingsViewModel
) {
    val entries = AutomaticResponse.entries(res)
    var selected by remember { mutableIntStateOf(AutomaticResponse.value) }

    SuperDropdown(
        title = stringResource(CoreR.string.auto_response),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            viewModel.onItemPressed(dummyView, AutomaticResponse) {
                AutomaticResponse.selectValue(index, dummyView, viewModel)
                selected = index
            }
        },
        startAction = {
            Icon(
                Icons.Rounded.QuestionAnswer,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        },
        enabled = AutomaticResponse.isEnabled
    )
}

/**
 * 请求超时选择器
 */
@Composable
private fun RequestTimeoutSelectorItem(
    res: Resources,
    dummyView: View,
    viewModel: SettingsViewModel
) {
    val entries = RequestTimeout.entries(res)
    var selected by remember { mutableIntStateOf(RequestTimeout.value) }

    SuperDropdown(
        title = stringResource(CoreR.string.request_timeout),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            RequestTimeout.selectValue(index, dummyView, viewModel)
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.Timer,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        },
        enabled = RequestTimeout.isEnabled
    )
}

/**
 * Root 通知选择器
 */
@Composable
private fun SUNotificationSelectorItem(
    res: Resources,
    dummyView: View,
    viewModel: SettingsViewModel
) {
    val entries = SUNotification.entries(res)
    var selected by remember { mutableIntStateOf(SUNotification.value) }

    SuperDropdown(
        title = stringResource(CoreR.string.superuser_notification),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            SUNotification.selectValue(index, dummyView, viewModel)
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.Notifications,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        },
        enabled = SUNotification.isEnabled
    )
}
