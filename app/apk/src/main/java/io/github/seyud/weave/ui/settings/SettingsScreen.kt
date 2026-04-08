package io.github.seyud.weave.ui.settings

import android.app.Activity
import android.content.res.Resources
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AppBlocking
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CallToAction
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
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import io.github.seyud.weave.core.BuildConfig
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.isRunningAsStub
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.utils.LocaleSetting
import io.github.seyud.weave.core.utils.MediaStoreUtils
import com.topjohnwu.superuser.Shell
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeSource
import io.github.seyud.weave.ui.util.defaultHazeEffect
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch
import io.github.seyud.weave.core.App as CoreApp
import io.github.seyud.weave.core.R as CoreR

/**
 * 设置页面主屏幕
 * 使用 Miuix 组件实现符合 MIUI 风格的设置列表界面
 *
 * @param viewModel 设置 ViewModel
 * @param contentBottomPadding 主页面内容底部留白
 * @param onNavigateToLog 导航到日志页面的回调
 * @param onNavigateToAppLanguage 导航到应用语言页面的回调
 * @param onNavigateToDenyListConfig 导航到 DenyList 配置页面的回调
 * @param modifier Modifier
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentBottomPadding: Dp,
    onNavigateToLog: () -> Unit,
    onNavigateToAppLanguage: () -> Unit,
    onNavigateToDenyListConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as Activity
    val res = context.resources
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val hazeState = remember { HazeState() }
    val hazeStyle = if (enableBlur) {
        HazeStyle(
            backgroundColor = colorScheme.surface,
            tint = HazeTint(colorScheme.surface.copy(0.8f))
        )
    } else {
        HazeStyle.Unspecified
    }

    LaunchedEffect(onNavigateToLog, onNavigateToDenyListConfig) {
        viewModel.onNavigateToLog = onNavigateToLog
        viewModel.onNavigateToDenyListConfig = onNavigateToDenyListConfig
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
    var updateChannelIndex by rememberSaveable { mutableIntStateOf(Config.updateChannel) }
    var showCustomChannelDialog by rememberSaveable { mutableStateOf(false) }
    var customChannelUrl by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(Config.customChannelUrl))
    }
    var showDownloadPathDialog by rememberSaveable { mutableStateOf(false) }
    var downloadPathInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(Config.downloadDir))
    }
    var showHideDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var hideAppName by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(HideAppDefaultName))
    }
    var isHideInProgress by rememberSaveable { mutableStateOf(false) }
    var isRestoreInProgress by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                modifier = if (enableBlur) {
                    Modifier.defaultHazeEffect(hazeState, hazeStyle)
                } else Modifier,
                color = if (enableBlur) Color.Transparent else colorScheme.surface,
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
                .then(if (enableBlur) Modifier.hazeSource(state = hazeState) else Modifier)
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
                    ArrowPreference(
                        title = stringResource(CoreR.string.logs),
                        startAction = {
                            Icon(
                                Icons.Rounded.BugReport,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = { viewModel.onNavigateToLog?.invoke() }
                    )
                }
            }

            // ==================== 自定义 ====================
            item {
                SmallTitle(text = stringResource(CoreR.string.settings_customization))
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val showScaleDialog = rememberSaveable { mutableStateOf(false) }
                    var enableBlur by rememberSaveable { mutableStateOf(Config.enableBlur) }
                    var enableFloatingBottomBar by rememberSaveable { mutableStateOf(Config.enableFloatingBottomBar) }
                    var enableFloatingBottomBarBlur by rememberSaveable { mutableStateOf(Config.enableFloatingBottomBarBlur) }

                    // 主题模式
                    val themeItems = listOf(
                        stringResource(CoreR.string.settings_theme_mode_system),
                        stringResource(CoreR.string.settings_theme_mode_light),
                        stringResource(CoreR.string.settings_theme_mode_dark),
                        stringResource(CoreR.string.settings_theme_mode_monet_system),
                        stringResource(CoreR.string.settings_theme_mode_monet_light),
                        stringResource(CoreR.string.settings_theme_mode_monet_dark),
                    )
                    var themeMode by rememberSaveable { mutableIntStateOf(Config.colorMode) }
                    OverlayDropdownPreference(
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
                    AnimatedVisibility(visible = themeMode in 3..5) {
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
                        var keyColorIndex by rememberSaveable {
                            mutableIntStateOf(
                                colorValues.indexOf(Config.keyColor).takeIf { it >= 0 } ?: 0
                            )
                        }
                        OverlayDropdownPreference(
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

                    // 模糊
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        SwitchPreference(
                            title = stringResource(CoreR.string.settings_enable_blur),
                            summary = stringResource(CoreR.string.settings_enable_blur_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.WaterDrop,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(CoreR.string.settings_enable_blur),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = enableBlur,
                            onCheckedChange = {
                                Config.enableBlur = it
                                enableBlur = Config.enableBlur
                                enableFloatingBottomBarBlur = Config.enableFloatingBottomBarBlur
                            }
                        )
                    }

                    // 悬浮底栏
                    SwitchPreference(
                        title = stringResource(CoreR.string.settings_floating_bottom_bar),
                        summary = stringResource(CoreR.string.settings_floating_bottom_bar_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.CallToAction,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(CoreR.string.settings_floating_bottom_bar),
                                tint = colorScheme.onBackground
                            )
                        },
                        checked = enableFloatingBottomBar,
                        onCheckedChange = {
                            Config.enableFloatingBottomBar = it
                            enableFloatingBottomBar = it
                        }
                    )

                    // 液态玻璃
                    AnimatedVisibility(
                        visible = enableFloatingBottomBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        SwitchPreference(
                            title = stringResource(CoreR.string.settings_enable_glass),
                            summary = stringResource(CoreR.string.settings_enable_glass_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BlurOn,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(CoreR.string.settings_enable_glass),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = enableFloatingBottomBarBlur,
                            onCheckedChange = {
                                Config.enableFloatingBottomBarBlur = it
                                enableFloatingBottomBarBlur = Config.enableFloatingBottomBarBlur
                                enableBlur = Config.enableBlur
                            }
                        )
                    }

                    // 预测性返回手势（Android 14+）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        var enablePredictiveBack by rememberSaveable { mutableStateOf(Config.enablePredictiveBack) }
                        SwitchPreference(
                            title = stringResource(CoreR.string.settings_enable_predictive_back),
                            summary = stringResource(CoreR.string.settings_enable_predictive_back_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Adb,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(CoreR.string.settings_enable_predictive_back),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = enablePredictiveBack,
                            onCheckedChange = {
                                Config.enablePredictiveBack = it
                                enablePredictiveBack = it
                                CoreApp.setEnableOnBackInvokedCallback(context.applicationInfo, it)
                                activity.recreate()
                            }
                        )
                    }

                    // 界面缩放
                    var sliderValue by rememberSaveable { mutableStateOf(Config.pageScale) }
                    ArrowPreference(
                        title = stringResource(CoreR.string.settings_page_scale),
                        summary = stringResource(CoreR.string.settings_page_scale_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.AspectRatio,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(CoreR.string.settings_page_scale),
                                tint = colorScheme.onBackground
                            )
                        },
                        endActions = {
                            Text(
                                text = "${(sliderValue * 100).toInt()}%",
                                color = colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = { showScaleDialog.value = !showScaleDialog.value },
                        holdDownState = showScaleDialog.value,
                        bottomAction = {
                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                onValueChangeFinished = {
                                    Config.pageScale = sliderValue
                                },
                                valueRange = 0.8f..1.1f,
                                showKeyPoints = true,
                                keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f),
                                magnetThreshold = 0.01f,
                                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                            )
                        }
                    )
                    ScaleDialog(
                        showDialog = showScaleDialog,
                        scaleState = { Config.pageScale },
                        onScaleChange = {
                            Config.pageScale = it
                            sliderValue = it
                        }
                    )

                    // 语言
                    ArrowPreference(
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
                        ArrowPreference(
                            title = stringResource(CoreR.string.add_shortcut_title),
                            summary = stringResource(CoreR.string.setting_add_shortcut_summary),
                            startAction = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.AddToHomeScreen,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { viewModel.addShortcut() }
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
                        selectedIndex = updateChannelIndex,
                        onSelectedIndexChange = { index ->
                            Config.updateChannel = index
                            Info.resetUpdate()
                            updateChannelIndex = index
                        },
                    )

                    // 自定义更新通道 URL
                    AnimatedVisibility(visible = updateChannelIndex == Config.Value.CUSTOM_CHANNEL) {
                        ArrowPreference(
                            title = stringResource(CoreR.string.settings_update_custom),
                            summary = UpdateChannelUrl.getDescription(res),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Link,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = {
                                customChannelUrl = TextFieldValue(Config.customChannelUrl)
                                showCustomChannelDialog = true
                            }
                        )
                    }

                    // DNS over HTTPS
                    var dohEnabled by rememberSaveable { mutableStateOf(Config.doh) }
                    SwitchPreference(
                        title = stringResource(CoreR.string.settings_doh_title),
                        summary = stringResource(CoreR.string.settings_doh_description),
                        checked = dohEnabled,
                        onCheckedChange = {
                            Config.doh = it
                            dohEnabled = it
                        },
                        startAction = {
                            Icon(
                                Icons.Rounded.Dns,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        }
                    )

                    // 检查更新
                    var checkUpdateEnabled by rememberSaveable { mutableStateOf(Config.checkUpdate) }
                    SwitchPreference(
                        title = stringResource(CoreR.string.settings_check_update_title),
                        summary = stringResource(CoreR.string.settings_check_update_summary),
                        checked = checkUpdateEnabled,
                        onCheckedChange = {
                            Config.checkUpdate = it
                            checkUpdateEnabled = it
                        },
                        startAction = {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        }
                    )

                    // 下载路径
                    ArrowPreference(
                        title = stringResource(CoreR.string.settings_download_path_title),
                        summary = MediaStoreUtils.fullPath(Config.downloadDir).takeIf { it.isNotEmpty() },
                        startAction = {
                            Icon(
                                Icons.Rounded.FolderOpen,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = {
                            downloadPathInput = TextFieldValue(Config.downloadDir)
                            showDownloadPathDialog = true
                        }
                    )

                    // 随机文件名
                    var randNameEnabled by rememberSaveable { mutableStateOf(Config.randName) }
                    SwitchPreference(
                        title = stringResource(CoreR.string.settings_random_name_title),
                        summary = stringResource(CoreR.string.settings_random_name_description),
                        checked = randNameEnabled,
                        onCheckedChange = {
                            Config.randName = it
                            randNameEnabled = it
                        },
                        startAction = {
                            Icon(
                                Icons.Rounded.Shuffle,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        }
                    )

                    // 隐藏/恢复 Magisk app
                    if (showHideRestore) {
                        if (hidden) {
                            ArrowPreference(
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
                                    if (!isRestoreInProgress) {
                                        showRestoreConfirmDialog = true
                                    }
                                }
                            )
                        } else {
                            ArrowPreference(
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
                                    hideAppName = TextFieldValue(HideAppDefaultName)
                                    showHideDialog = true
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
                        ArrowPreference(
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
                            onClick = { viewModel.createHosts() }
                        )

                        if (showZygisk) {
                            // Zygisk
                            var zygiskEnabled by rememberSaveable { mutableStateOf(Config.zygisk) }
                            val zygiskMismatch = zygiskEnabled != Info.isZygiskEnabled
                            SwitchPreference(
                                title = stringResource(CoreR.string.zygisk),
                                summary = if (zygiskMismatch) stringResource(CoreR.string.reboot_apply_change) else stringResource(CoreR.string.settings_zygisk_summary),
                                checked = zygiskEnabled,
                                onCheckedChange = {
                                    Config.zygisk = it
                                    zygiskEnabled = it
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
                            var denyListEnabled by rememberSaveable { mutableStateOf(Config.denyList) }
                            SwitchPreference(
                                title = stringResource(CoreR.string.settings_denylist_title),
                                summary = stringResource(CoreR.string.settings_denylist_summary),
                                checked = denyListEnabled,
                                onCheckedChange = { checked ->
                                    val cmd = if (checked) "enable" else "disable"
                                    Shell.cmd("magisk --denylist $cmd").submit { result ->
                                        if (result.isSuccess) {
                                            Config.denyList = checked
                                            denyListEnabled = checked
                                        }
                                    }
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
                            ArrowPreference(
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
                                onClick = { viewModel.navigateToDenyListConfig() }
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
                            var tapjackEnabled by rememberSaveable { mutableStateOf(Config.suTapjack) }
                            SwitchPreference(
                                title = stringResource(CoreR.string.settings_su_tapjack_title),
                                summary = stringResource(CoreR.string.settings_su_tapjack_summary),
                                checked = tapjackEnabled,
                                onCheckedChange = {
                                    Config.suTapjack = it
                                    tapjackEnabled = it
                                },
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Security,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                }
                            )
                        }

                        // 生物认证
                        var authEnabled by rememberSaveable { mutableStateOf(Config.suAuth) }
                        val authEnabledState = remember { mutableStateOf(Info.isDeviceSecure) }
                        val authSummary = if (authEnabledState.value) {
                            stringResource(CoreR.string.settings_su_auth_summary)
                        } else {
                            stringResource(CoreR.string.settings_su_auth_insecure)
                        }
                        SwitchPreference(
                            title = stringResource(CoreR.string.settings_su_auth_title),
                            summary = authSummary,
                            checked = authEnabled,
                            onCheckedChange = { checked ->
                                viewModel.authenticateAndToggle(checked) { success ->
                                    if (success) {
                                        Config.suAuth = checked
                                        authEnabled = checked
                                    }
                                }
                            },
                            enabled = authEnabledState.value,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Fingerprint,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground
                                )
                            }
                        )

                        // Root 访问模式
                        AccessModeSelectorItem(res = res)

                        // 多用户模式
                        MultiuserModeSelectorItem(res = res)

                        // 挂载命名空间模式
                        MountNamespaceModeSelectorItem(res = res)

                        // 自动响应
                        AutomaticResponseSelectorItem(res = res, viewModel = viewModel)

                        // 请求超时
                        RequestTimeoutSelectorItem(res = res)

                        // Root 通知
                        SUNotificationSelectorItem(res = res)

                        // 重新认证
                        if (showReauthenticate) {
                            var reauthEnabled by rememberSaveable { mutableStateOf(Config.suReAuth) }
                            SwitchPreference(
                                title = stringResource(CoreR.string.settings_su_reauth_title),
                                summary = stringResource(CoreR.string.settings_su_reauth_summary),
                                checked = reauthEnabled,
                                onCheckedChange = {
                                    Config.suReAuth = it
                                    reauthEnabled = it
                                },
                                startAction = {
                                    Icon(
                                        Icons.Rounded.LockReset,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                }
                            )
                        }

                        // 限制前台应用
                        if (showRestrict) {
                            var restrictEnabled by rememberSaveable { mutableStateOf(Config.suRestrict) }
                            SwitchPreference(
                                title = stringResource(CoreR.string.settings_su_restrict_title),
                                summary = stringResource(CoreR.string.settings_su_restrict_summary),
                                checked = restrictEnabled,
                                onCheckedChange = {
                                    Config.suRestrict = it
                                    restrictEnabled = it
                                },
                                startAction = {
                                    Icon(
                                        Icons.Rounded.AppBlocking,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 底部留白
            item {
                Spacer(Modifier.height(contentBottomPadding))
            }
        }
    }

    HideAppDialog(
        show = showHideDialog,
        appName = hideAppName,
        onAppNameChange = { hideAppName = it },
        onDismissRequest = {
            if (!isHideInProgress) {
                showHideDialog = false
            }
        },
        onConfirm = {
            val newName = hideAppName.text.ifBlank { HideAppDefaultName }
            coroutineScope.launch {
                isHideInProgress = true
                val success = viewModel.hideApp(context, newName)
                isHideInProgress = false
                if (success) {
                    showHideDialog = false
                } else {
                    context.toast(CoreR.string.failure, Toast.LENGTH_LONG)
                }
            }
        },
    )

    HideAppLoadingDialog(
        show = isHideInProgress,
        title = stringResource(CoreR.string.hide_app_title),
    )

    HideAppLoadingDialog(
        show = isRestoreInProgress,
        title = stringResource(CoreR.string.restore_img_msg),
    )

    io.github.seyud.weave.ui.component.MiuixConfirmDialog(
        show = showRestoreConfirmDialog,
        title = stringResource(CoreR.string.settings_restore_app_title),
        summary = stringResource(CoreR.string.restore_app_confirmation),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = {
            if (!isRestoreInProgress) {
                showRestoreConfirmDialog = false
            }
        },
        onConfirm = {
            if (!isRestoreInProgress) {
                showRestoreConfirmDialog = false
                coroutineScope.launch {
                    isRestoreInProgress = true
                    val success = viewModel.restoreApp(context)
                    isRestoreInProgress = false
                    if (!success) {
                        context.toast(CoreR.string.failure, Toast.LENGTH_LONG)
                    }
                }
            }
        },
    )

    io.github.seyud.weave.ui.component.MiuixTextInputDialog(
        show = showCustomChannelDialog,
        title = stringResource(CoreR.string.settings_update_custom),
        value = customChannelUrl,
        onValueChange = { customChannelUrl = it },
        label = stringResource(CoreR.string.settings_update_custom_msg),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = { showCustomChannelDialog = false },
        onConfirm = {
            Config.customChannelUrl = customChannelUrl.text
            Info.resetUpdate()
            showCustomChannelDialog = false
        },
        confirmEnabled = true,
    )

    io.github.seyud.weave.ui.component.MiuixTextInputDialog(
        show = showDownloadPathDialog,
        title = stringResource(CoreR.string.settings_download_path_title),
        value = downloadPathInput,
        onValueChange = { downloadPathInput = it },
        label = stringResource(CoreR.string.settings_download_path_title),
        helperText = context.getString(
            CoreR.string.settings_download_path_message,
            MediaStoreUtils.fullPath(Config.downloadDir),
        ),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = { showDownloadPathDialog = false },
        onConfirm = {
            Config.downloadDir = downloadPathInput.text
            showDownloadPathDialog = false
        },
        confirmEnabled = true,
    )
}

/**
 * 获取应用语言摘要
 */
private fun appLanguageSummary(res: Resources): String {
    val locale = LocaleSetting.instance.appLocale
    return if (locale != null) {
        locale.getDisplayName(locale)
    } else {
        res.getString(CoreR.string.system_default)
    }
}

/**
 * 更新通道选择器
 */
@Composable
private fun UpdateChannelSelectorItem(
    res: Resources,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val entries = res.getStringArray(CoreR.array.update_channel)

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.settings_update_channel_title),
        items = entries.toList(),
        selectedIndex = selectedIndex.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = onSelectedIndexChange,
        startAction = {
            Icon(
                Icons.Rounded.Update,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        }
    )
}

/**
 * Root 访问模式选择器
 */
@Composable
private fun AccessModeSelectorItem(res: Resources) {
    val entries = res.getStringArray(CoreR.array.su_access)
    var selected by rememberSaveable { mutableIntStateOf(Config.rootMode) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.superuser_access),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.rootMode = index
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.AdminPanelSettings,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        }
    )
}

/**
 * 多用户模式选择器
 */
@Composable
private fun MultiuserModeSelectorItem(res: Resources) {
    val entries = res.getStringArray(CoreR.array.multiuser_mode)
    val summaries = res.getStringArray(CoreR.array.multiuser_summary)
    var selected by rememberSaveable { mutableIntStateOf(Config.suMultiuserMode) }
    val enabled = Const.USER_ID == 0

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.multiuser_mode),
        summary = summaries.getOrElse(selected) { "" },
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.suMultiuserMode = index
            selected = index
        },
        enabled = enabled,
        startAction = {
            Icon(
                Icons.Rounded.Group,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        }
    )
}

/**
 * 挂载命名空间模式选择器
 */
@Composable
private fun MountNamespaceModeSelectorItem(res: Resources) {
    val entries = res.getStringArray(CoreR.array.namespace)
    val summaries = res.getStringArray(CoreR.array.namespace_summary)
    var selected by rememberSaveable { mutableIntStateOf(Config.suMntNamespaceMode) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.mount_namespace_mode),
        summary = summaries.getOrElse(selected) { "" },
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.suMntNamespaceMode = index
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.FolderSpecial,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        }
    )
}

/**
 * 自动响应选择器
 */
@Composable
private fun AutomaticResponseSelectorItem(res: Resources, viewModel: SettingsViewModel) {
    val entries = res.getStringArray(CoreR.array.auto_response)
    var selected by rememberSaveable { mutableIntStateOf(Config.suAutoResponse) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.auto_response),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            if (Config.suAuth) {
                viewModel.authenticate { success ->
                    if (success) {
                        Config.suAutoResponse = index
                        selected = index
                    }
                }
            } else {
                Config.suAutoResponse = index
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
        }
    )
}

/**
 * 请求超时选择器
 */
@Composable
private fun RequestTimeoutSelectorItem(res: Resources) {
    val entries = res.getStringArray(CoreR.array.request_timeout)
    val entryValues = listOf(10, 15, 20, 30, 45, 60)
    var selected by rememberSaveable {
        mutableIntStateOf(entryValues.indexOfFirst { it == Config.suDefaultTimeout }.coerceAtLeast(0))
    }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.request_timeout),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.suDefaultTimeout = entryValues[index]
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.Timer,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        }
    )
}

/**
 * Root 通知选择器
 */
@Composable
private fun SUNotificationSelectorItem(res: Resources) {
    val entries = res.getStringArray(CoreR.array.su_notification)
    var selected by rememberSaveable { mutableIntStateOf(Config.suNotification) }

    OverlayDropdownPreference(
        title = stringResource(CoreR.string.superuser_notification),
        items = entries.toList(),
        selectedIndex = selected.coerceIn(0, entries.size - 1),
        onSelectedIndexChange = { index ->
            Config.suNotification = index
            selected = index
        },
        startAction = {
            Icon(
                Icons.Rounded.Notifications,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = null,
                tint = colorScheme.onBackground
            )
        }
    )
}
