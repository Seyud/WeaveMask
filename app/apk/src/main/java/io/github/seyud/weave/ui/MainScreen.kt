package io.github.seyud.weave.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.intent
import io.github.seyud.weave.core.model.module.LocalModule
import io.github.seyud.weave.core.utils.MediaStoreUtils
import io.github.seyud.weave.dialog.LocalModuleInstallDialog
import io.github.seyud.weave.ui.component.FloatingBottomBar
import io.github.seyud.weave.ui.component.FloatingBottomBarItem
import io.github.seyud.weave.ui.icon.SuperuserIcon
import io.github.seyud.weave.ui.navigation3.LocalNavigator
import io.github.seyud.weave.ui.navigation3.Navigator
import io.github.seyud.weave.ui.navigation3.Route
import io.github.seyud.weave.ui.navigation3.rememberNavigator
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.theme.LocalEnableSwipeDismiss
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBar
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBarBlur
import io.github.seyud.weave.ui.theme.LocalPagerGestureMode
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import io.github.seyud.weave.ui.flash.FlashScreen
import io.github.seyud.weave.ui.flash.FlashRequest
import io.github.seyud.weave.ui.flash.FlashViewModel
import io.github.seyud.weave.ui.home.HomeScreen
import io.github.seyud.weave.ui.home.HomeViewModel
import io.github.seyud.weave.ui.about.AboutScreen
import io.github.seyud.weave.ui.install.InstallScreen
import io.github.seyud.weave.ui.install.InstallViewModel
import io.github.seyud.weave.ui.log.LogScreen
import io.github.seyud.weave.ui.log.LogViewModel
import io.github.seyud.weave.ui.modulerepo.ModuleRepoDetailScreen
import io.github.seyud.weave.ui.modulerepo.ModuleRepoScreen
import io.github.seyud.weave.ui.modulerepo.ModuleRepoViewModel
import io.github.seyud.weave.ui.module.ActionScreen
import io.github.seyud.weave.ui.module.ModuleInstallTarget
import io.github.seyud.weave.ui.module.ModuleShortcutContract
import io.github.seyud.weave.ui.module.ModuleScreen
import io.github.seyud.weave.ui.module.ModuleViewModel
import io.github.seyud.weave.ui.scriptrun.ScriptRunScreen
import io.github.seyud.weave.ui.settings.AppLanguageScreen
import io.github.seyud.weave.ui.settings.SettingsScreen
import io.github.seyud.weave.ui.settings.SettingsViewModel
import io.github.seyud.weave.ui.superuser.SuperuserScreen
import io.github.seyud.weave.ui.superuser.SuperuserViewModel
import io.github.seyud.weave.ui.colorpalette.ColorPaletteScreen
import io.github.seyud.weave.ui.deny.DenyListScreen
import io.github.seyud.weave.ui.util.rememberContentReady
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.utils.PagerFlingTrackerConnection
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.seyud.weave.core.R as CoreR

private val MainTabContentBottomSpacing = 12.dp

/**
 * 底部导航目的地枚举
 * 固定四个导航项：主页、超级用户、模块、设置
 */
enum class BottomBarDestination(
    val labelResId: Int,
    val icon: ImageVector,
) {
    Home(CoreR.string.section_home, Icons.Rounded.Home),
    SuperUser(CoreR.string.superuser, SuperuserIcon),
    Module(CoreR.string.modules, Icons.Rounded.Extension),
    Setting(CoreR.string.settings, Icons.Rounded.Settings)
}

/**
 * Pager 状态管理，与 KernelSU 相同的即时选中 + 异步动画模式
 * 点击导航项时立即更新选中状态（图标高亮无延迟），Pager 动画异步滚动
 */
class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return
        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                // 滚动物理交给 miuix 共享的 PagerNavigationSpringSpec，
                // 与底部导航栏、fling 吸附使用同一条弹簧曲线
                pagerState.springAnimateToPage(targetIndex)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(pagerState: PagerState): MainPagerState {
    val coroutineScope = rememberCoroutineScope()
    return remember(pagerState, coroutineScope) {
        MainPagerState(pagerState, coroutineScope)
    }
}

/**
 * 在底栏（bar）层级附加「长按指定 tab」检测。
 *
 * 不能挂在底栏的单个 item 上：
 *  - 标准 NavigationBar：Miuix NavigationBarItem 内部 selectable 会使 item 级外部
 *    pointerInput 协程在手势中途被静默取消（DOWN 可达、后续全丢）；
 *  - 悬浮底栏：选中指示器自带拖拽手势层且覆盖在选中的 tab 上，item 的
 *    combinedClickable 长按无法赢得手势仲裁。
 * bar 层级没有竞争手势；长按成立后 consume 后续事件，避免松手时误触 item 的 click。
 *
 * @param tabsCount   底栏均分的 tab 数
 * @param targetTab   响应长按的 tab 下标
 * @param onLongPress 长按成立时的回调
 */
private fun Modifier.detectTabLongPress(
    tabsCount: Int,
    targetTab: Int,
    onLongPress: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tabWidth = size.width / tabsCount.toFloat()
        val inTargetTab = down.position.x >= tabWidth * targetTab &&
            down.position.x < tabWidth * (targetTab + 1)
        if (!inTargetTab) return@awaitEachGesture
        var fired = false
        try {
            withTimeout(longPressTimeout) {
                var pointer = down
                while (pointer.pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    pointer = event.changes.firstOrNull() ?: break
                    if ((pointer.position - down.position).getDistance() > touchSlop) {
                        break
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            fired = true
        } catch (_: PointerEventTimeoutCancellationException) {
            // awaitPointerEvent 会把 withTimeout 的超时包装成 Compose 自己的
            // PointerEventTimeoutCancellationException（CancellationException
            // 子类），只 catch kotlinx 的 TimeoutCancellationException 会漏掉
            fired = true
        }
        if (fired) {
            onLongPress()
            // 吞掉后续事件并 consume，阻止 item 的 click 在抬起时触发
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            } while (true)
        }
    }
}

/**
 * 主屏幕 Composable 函数
 * 使用 Navigation3 NavDisplay 作为导航容器
 */
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    flashViewModel: FlashViewModel,
    moduleViewModel: ModuleViewModel,
    moduleRepoViewModel: ModuleRepoViewModel,
    superuserViewModel: SuperuserViewModel,
    logViewModel: LogViewModel,
    installViewModel: InstallViewModel,
    settingsViewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    initialMainTab: Int = 0,
    intentVersion: Int = 0,
    pendingFlashRequest: FlashRequest? = null,
    onPendingFlashRequestConsumed: () -> Unit = {},
    externalZipUris: List<ModuleInstallTarget>? = null,
    onExternalZipHandled: () -> Unit = {},
) {
    val navigator = rememberNavigator(Route.Main)
    var snackbarBottomPadding by remember { mutableStateOf(0.dp) }
    var rememberedMainTab by rememberSaveable {
        mutableIntStateOf(initialMainTab.coerceIn(0, 3))
    }
    val currentRoute by remember(navigator) {
        derivedStateOf { navigator.current() }
    }

    // miuix-nav 的边缘横移返回。NavSwipeDirection 是物理方向、不随 layoutDirection 镜像，
    // 所以 RTL 下要换成另一侧。
    val enableSwipeDismiss = LocalEnableSwipeDismiss.current
    val layoutDirection = LocalLayoutDirection.current
    val swipeDismiss = when {
        !enableSwipeDismiss -> NavSwipeDirection.None
        layoutDirection == LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        else -> NavSwipeDirection.LeftToRight
    }

    // 会写盘 / 运行 root 脚本的路由：进行中把该 entry 的横移手势和返回键一起关掉，
    // 避免一次误滑中断刷写、卸载或遗留 root shell。默认值与各屏的初始状态一致。
    val flashRoute = currentRoute as? Route.Flash
    var flashBackGestureEnabled by remember(flashRoute) { mutableStateOf(false) }
    val actionRoute = currentRoute as? Route.Action
    var actionBackGestureEnabled by remember(actionRoute) { mutableStateOf(false) }
    val scriptRunRoute = currentRoute as? Route.ScriptRun
    var scriptRunBackGestureEnabled by remember(scriptRunRoute) { mutableStateOf(true) }

    // 处理外部应用通过"打开方式"打开的 ZIP 文件
    var pendingExternalZip by remember { mutableStateOf<List<ModuleInstallTarget>?>(null) }
    val context = LocalContext.current

    LaunchedEffect(externalZipUris) {
        if (!externalZipUris.isNullOrEmpty()) {
            pendingExternalZip = externalZipUris
        }
    }

    // 显示外部 ZIP 文件确认对话框
    if (!pendingExternalZip.isNullOrEmpty()) {
        LocalModuleInstallDialog(
            state = LocalModuleInstallDialog.DialogState(
                visible = true,
                modules = pendingExternalZip.orEmpty()
            ),
            context = context,
            onDismiss = {
                pendingExternalZip = null
                onExternalZipHandled()
            },
            onConfirm = {
                val uris = pendingExternalZip.orEmpty().map(ModuleInstallTarget::uri)
                if (uris.isNotEmpty()) {
                    navigator.push(Route.Flash(Const.Value.FLASH_ZIP, uris.map(Uri::toString)))
                }
                pendingExternalZip = null
                onExternalZipHandled()
            },
            renderInRootScaffold = true
        )
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute !is Route.Main) {
            snackbarBottomPadding = 0.dp
        }
    }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
    ) {
        FlashIntentHandler(
            pendingFlashRequest = pendingFlashRequest,
            navigator = navigator,
            onSelectMainTab = { rememberedMainTab = it.coerceIn(0, 3) },
            onConsumed = onPendingFlashRequestConsumed,
        )
        Box(modifier = modifier.fillMaxSize()) {
            ShortcutIntentHandler(intentVersion = intentVersion)
            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        state = snackbarHostState,
                        modifier = Modifier.padding(bottom = snackbarBottomPadding)
                    )
                }
            ) { _ ->
                NavDisplay(
                    backStack = navigator.backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { navigator.pop() },
                    // 转场时把进入页面裁到系统屏幕圆角，与平台观感一致
                    effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
                ) {
                    // 栈底路由：不参与横移返回，横向拖动留给主界面的 HorizontalPager
                    entry<Route.Main>(swipeDismiss = NavSwipeDirection.None) {
                        MainTabScreen(
                            navigator = navigator,
                            homeViewModel = homeViewModel,
                            moduleViewModel = moduleViewModel,
                            superuserViewModel = superuserViewModel,
                            settingsViewModel = settingsViewModel,
                            logViewModel = logViewModel,
                            initialMainTab = rememberedMainTab,
                            onCurrentTabChanged = { rememberedMainTab = it },
                            onSnackbarBottomPaddingChanged = { snackbarBottomPadding = it },
                            snackbarHostState = snackbarHostState
                        )
                    }
                    entry<Route.Install>(swipeDismiss = swipeDismiss) {
                        InstallScreen(
                            viewModel = installViewModel,
                            onNavigateBack = { navigator.pop() },
                            onNavigateToFlash = { action, uri ->
                                navigator.pop()
                                navigator.push(
                                    Route.Flash(
                                        action,
                                        uri?.let { listOf(it.toString()) } ?: emptyList()
                                    )
                                )
                            }
                        )
                    }
                    entry<Route.Flash>(
                        swipeDismiss = if (flashBackGestureEnabled) swipeDismiss else NavSwipeDirection.None,
                    ) { key ->
                        val uriArgs = key.uriStrings
                            .filter { it.isNotEmpty() }
                            .map { it.toUri() }

                        DisposableEffect(key.action) {
                            onDispose {
                                if (key.action == Const.Value.FLASH_ZIP) {
                                    // 安装返回模块页：静默刷新，避免下拉刷新指示器把列表顶下去再弹回
                                    moduleViewModel.refresh(showProgress = false)
                                }
                            }
                        }

                        FlashScreen(
                            viewModel = flashViewModel,
                            action = key.action,
                            additionalData = uriArgs,
                            onNavigateBack = { navigator.pop() },
                            onBackGestureEnabledChange = { enabled ->
                                if (navigator.current() == key) flashBackGestureEnabled = enabled
                            },
                            snackbarHostState = snackbarHostState
                        )
                    }
                    entry<Route.Log>(swipeDismiss = swipeDismiss) {
                        LogScreen(
                            viewModel = logViewModel,
                            onNavigateBack = { navigator.pop() },
                            snackbarHostState = snackbarHostState
                        )
                    }
                    entry<Route.ModuleRepoList>(swipeDismiss = swipeDismiss) {
                        ModuleRepoScreen(
                            viewModel = moduleRepoViewModel,
                            moduleViewModel = moduleViewModel,
                            onNavigateBack = { navigator.pop() },
                            onOpenModuleDetail = { moduleId ->
                                navigator.push(Route.ModuleRepoDetail(moduleId))
                            }
                        )
                    }
                    entry<Route.ModuleRepoDetail>(swipeDismiss = swipeDismiss) { key ->
                        ModuleRepoDetailScreen(
                            moduleId = key.moduleId,
                            moduleViewModel = moduleViewModel,
                            onNavigateBack = { navigator.pop() },
                            onRunScript = { zipFile, registryId, displayName, expected ->
                                navigator.push(
                                    Route.ScriptRun(
                                        zipUri = zipFile.toString(),
                                        registryId = registryId,
                                        displayName = displayName,
                                        expectedSize = expected?.size ?: 0L,
                                        expectedSha256 = expected?.sha256.orEmpty(),
                                    )
                                )
                            }
                        )
                    }
                    entry<Route.ScriptRun>(
                        swipeDismiss = if (scriptRunBackGestureEnabled) swipeDismiss else NavSwipeDirection.None,
                    ) { key ->
                        // The ViewModel owns the extraction of the prepared script and releases it
                        // in onCleared, so it must be scoped to this navigation entry.
                        ScriptRunScreen(
                            zipUri = key.zipUri.toUri(),
                            registryId = key.registryId,
                            displayName = key.displayName,
                            expectedSize = key.expectedSize,
                            expectedSha256 = key.expectedSha256.takeIf { it.isNotEmpty() },
                            onNavigateBack = { navigator.pop() },
                            onBackGestureEnabledChange = { enabled ->
                                if (navigator.current() == key) scriptRunBackGestureEnabled = enabled
                            },
                        )
                    }
                    entry<Route.AppLanguage>(swipeDismiss = swipeDismiss) {
                        AppLanguageScreen(
                            onNavigateBack = { navigator.pop() }
                        )
                    }
                    entry<Route.ColorPalette>(swipeDismiss = swipeDismiss) {
                        ColorPaletteScreen(
                            onNavigateBack = { navigator.pop() }
                        )
                    }
                    entry<Route.Deny>(swipeDismiss = swipeDismiss) {
                        DenyListScreen(
                            onNavigateBack = { navigator.pop() }
                        )
                    }
                    entry<Route.About>(swipeDismiss = swipeDismiss) {
                        AboutScreen(
                            onNavigateBack = { navigator.pop() },
                            onLinkPressed = { link ->
                                // 误报抑制：SuRequestActivity 的 VIEW filter 无 <data>，
                                // 不可能匹配 http(s) 链接；BROWSABLE 限定只由浏览器解析
                                @SuppressLint("UnsafeImplicitIntentLaunch")
                                val intent = Intent(Intent.ACTION_VIEW, link.toUri())
                                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                context.startActivity(intent)
                            }
                        )
                    }
                    entry<Route.Action>(
                        swipeDismiss = if (actionBackGestureEnabled) swipeDismiss else NavSwipeDirection.None,
                    ) { key ->
                        ActionScreen(
                            moduleId = key.moduleId,
                            moduleName = key.moduleName,
                            fromShortcut = key.fromShortcut,
                            onNavigateBack = { navigator.pop() },
                            onBackGestureEnabledChange = { enabled ->
                                if (navigator.current() == key) actionBackGestureEnabled = enabled
                            },
                        )
                    }
                }
            }

            if (pendingFlashRequest != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.background)
                )
            }
        }
    }
}

@Composable
private fun FlashIntentHandler(
    pendingFlashRequest: FlashRequest?,
    navigator: Navigator,
    onSelectMainTab: (Int) -> Unit,
    onConsumed: () -> Unit,
) {
    if (pendingFlashRequest != null) {
        SideEffect {
            navigator.push(pendingFlashRequest.toRoute())
            pendingFlashRequest.startMainTab?.let(onSelectMainTab)
            onConsumed()
        }
    }
}

@Composable
private fun ShortcutIntentHandler(
    intentVersion: Int,
) {
    val activity = LocalActivity.current ?: return
    val navigator = LocalNavigator.current

    LaunchedEffect(intentVersion) {
        val intent = activity.intent ?: return@LaunchedEffect
        val shortcutType = intent.getStringExtra(ModuleShortcutContract.EXTRA_SHORTCUT_TYPE)
            ?: return@LaunchedEffect
        if (shortcutType != ModuleShortcutContract.TYPE_ACTION) {
            return@LaunchedEffect
        }

        val moduleId = intent.getStringExtra(ModuleShortcutContract.EXTRA_MODULE_ID)
            ?: return@LaunchedEffect
        val moduleName = intent.getStringExtra(ModuleShortcutContract.EXTRA_MODULE_NAME).orEmpty()

        intent.removeExtra(ModuleShortcutContract.EXTRA_SHORTCUT_TYPE)
        intent.removeExtra(ModuleShortcutContract.EXTRA_MODULE_ID)
        intent.removeExtra(ModuleShortcutContract.EXTRA_MODULE_NAME)

        navigator.push(Route.Action(moduleId, moduleName, fromShortcut = true))
    }
}

/**
 * 主 Tab 页面
 * 使用 HorizontalPager 实现 Tab 切换
 * 导航转场动画完成后再预渲染其他页面（rememberContentReady）
 */
@Composable
private fun MainTabScreen(
    navigator: Navigator,
    homeViewModel: HomeViewModel,
    moduleViewModel: ModuleViewModel,
    superuserViewModel: SuperuserViewModel,
    settingsViewModel: SettingsViewModel,
    logViewModel: LogViewModel,
    initialMainTab: Int,
    onCurrentTabChanged: (Int) -> Unit,
    onSnackbarBottomPaddingChanged: (androidx.compose.ui.unit.Dp) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current

    // 主界面 Pager 手势模式：Native / CrossAxisInterceptor（列表滚动中也能切页）/ TapToHalt
    val pagerGestureMode = LocalPagerGestureMode.current
    val pagerMode = remember(pagerGestureMode) {
        PagerInterceptionMode.entries.getOrElse(pagerGestureMode) {
            PagerInterceptionMode.Native
        }
    }
    val interceptPagerGestures = pagerMode == PagerInterceptionMode.CrossAxisInterceptor
    val pagerFlingTracker = remember { PagerFlingTrackerConnection() }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBarBlurBackdrop(enableBlur, surfaceColor)
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val pagerState = rememberPagerState(
        initialPage = initialMainTab.coerceIn(0, 3),
        pageCount = { 4 }
    )
    val mainPagerState = rememberMainPagerState(pagerState)
    val settledPage by remember(pagerState) {
        derivedStateOf { pagerState.settledPage }
    }
    var lastAppliedTabRequest by remember { mutableIntStateOf(initialMainTab.coerceIn(0, 3)) }
    val isAtMainRoot by remember(navigator) {
        derivedStateOf { navigator.backStack.size == 1 && navigator.current() is Route.Main }
    }

    // Navigation3 back handler
    val isPagerBackHandlerEnabled by remember(navigator, mainPagerState) {
        derivedStateOf {
            isAtMainRoot && mainPagerState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainPagerState.animateToPage(0)
        }
    )

    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    // 仅在页面落稳后回传到父层，避免动画过程中的状态回灌。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                if (settledPage != lastAppliedTabRequest) {
                    lastAppliedTabRequest = settledPage
                    onCurrentTabChanged(settledPage)
                }
            }
    }

    LaunchedEffect(initialMainTab) {
        val targetPage = initialMainTab.coerceIn(0, 3)
        if (targetPage == lastAppliedTabRequest && targetPage == pagerState.settledPage) return@LaunchedEffect

        lastAppliedTabRequest = targetPage
        if (targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
        mainPagerState.syncPage()
    }

    val destinations = BottomBarDestination.entries
    val isSuperuserEnabled = Info.showSuperUser
    val isRootServiceConnected by Info.isRootServiceConnected.observeAsState(initial = false)
    val isModuleEnabled = remember(isRootServiceConnected, Info.env.isActive) {
        Info.env.isActive && LocalModule.loaded()
    }

    // 长按底栏模块页图标切换管理模式（仅当前处于模块页时生效）
    // 返回是否处理了长按；两种底栏变体都用 detectTabLongPress（bar 级检测），
    // 震动需在此自行触发
    val hapticFeedback = LocalHapticFeedback.current
    val onModuleTabLongPress: () -> Boolean = {
        if (mainPagerState.selectedPage == BottomBarDestination.Module.ordinal) {
            moduleViewModel.toggleManagementMode()
            true
        } else {
            false
        }
    }

    // 使用 rememberContentReady 延迟加载 Pager 内容
    val contentReady = rememberContentReady()

    Scaffold(
        bottomBar = {
            if (enableFloatingBottomBar) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FloatingBottomBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            // 长按检测挂在悬浮底栏整体层级：item 的 combinedClickable 长按
                            // 会被选中指示器的拖拽手势层挡住（指示器覆盖在选中的 tab 上）
                            .detectTabLongPress(
                                tabsCount = destinations.size,
                                targetTab = BottomBarDestination.Module.ordinal,
                            ) {
                                if (onModuleTabLongPress()) {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.LongPress
                                    )
                                }
                            }
                            .padding(
                                bottom = 12.dp + WindowInsets.navigationBars
                                    .asPaddingValues().calculateBottomPadding()
                            ),
                    selectedIndex = { mainPagerState.selectedPage },
                    onSelected = { index ->
                        val enabled = when (index) {
                            1 -> isSuperuserEnabled
                            2 -> isModuleEnabled
                            else -> true
                        }
                        if (enabled) mainPagerState.animateToPage(index)
                    },
                    backdrop = backdrop,
                    tabsCount = destinations.size,
                    isBackdropBlurEnabled = enableBlur,
                    isLiquidGlassEnabled = enableBlur && enableFloatingBottomBarBlur,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val itemEnabled = when (index) {
                            1 -> isSuperuserEnabled
                            2 -> isModuleEnabled
                            else -> true
                        }
                        FloatingBottomBarItem(
                            onClick = {
                                if (itemEnabled) mainPagerState.animateToPage(index)
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                                .then(if (!itemEnabled) Modifier.alpha(0.38f) else Modifier)
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelResId),
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(destination.labelResId),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                        }
                    }
                }
                }
            } else {
                NavigationBar(
                    // 长按检测挂在 NavigationBar 层级而非 item 级：经 NavigationBarItem 的 modifier
                    // 附加 pointerInput 时，协程会在手势中途被静默取消（DOWN 可达、后续全丢）。
                    // detectTabLongPress 按 bar 宽度均分 4 格、以 DOWN 的 x 坐标判定模块页区域。
                    modifier = Modifier.defaultBarBlur(blurBackdrop, surfaceColor)
                        .detectTabLongPress(
                            tabsCount = destinations.size,
                            targetTab = BottomBarDestination.Module.ordinal,
                        ) {
                            if (onModuleTabLongPress()) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                    color = barBlurContainerColor(blurBackdrop, surfaceColor),
                    content = {
                        destinations.forEachIndexed { index, destination ->
                            val enabled = when (index) {
                                1 -> isSuperuserEnabled
                                2 -> isModuleEnabled
                                else -> true
                            }
                            NavigationBarItem(
                                icon = destination.icon,
                                label = stringResource(destination.labelResId),
                                selected = mainPagerState.selectedPage == index,
                                enabled = enabled,
                                onClick = {
                                    mainPagerState.animateToPage(index)
                                }
                            )
                        }
                    }
                )
            }
        },
    ) { paddingValues ->
        SideEffect {
            onSnackbarBottomPaddingChanged(paddingValues.calculateBottomPadding())
        }
        val contentBottomPadding = paddingValues.calculateBottomPadding() + MainTabContentBottomSpacing

        Box(
            modifier = Modifier
                .fillMaxSize()
                .attachBarBlurBackdrop(blurBackdrop)
                .then(
                    if (enableFloatingBottomBar && enableBlur)
                        Modifier.layerBackdrop(backdrop)
                    else Modifier
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = if (contentReady) destinations.size - 1 else 0,
                modifier = Modifier
                    .fillMaxSize()
                    .pagerGestureOverride(
                        pagerState = pagerState,
                        mode = pagerMode,
                        enabled = true,
                        flingTracker = pagerFlingTracker,
                    ),
                // CrossAxisInterceptor 自己驱动横向拖动：pager 的原生滚动必须让位，
                // 并把纵向滚动/速度留给页面内容，否则两套手势会互相打架
                userScrollEnabled = !interceptPagerGestures,
                pageNestedScrollConnection = if (interceptPagerGestures) {
                    PagerGestureNestedScrollConnection
                } else {
                    PagerDefaults.pageNestedScrollConnection(
                        state = pagerState,
                        orientation = Orientation.Horizontal,
                    )
                },
                // 与标签点击共用同一条弹簧曲线，滑动/点击切页手感一致
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = PagerNavigationSpringSpec,
                ),
            ) { page ->
                val isCurrentPage = page == settledPage
                when (page) {
                    0 -> if (isCurrentPage || contentReady) HomeScreen(
                        viewModel = homeViewModel,
                        contentBottomPadding = contentBottomPadding,
                        isActive = isCurrentPage,
                        onNavigateToInstall = {
                            navigator.push(Route.Install)
                        },
                        onNavigateToUninstall = {
                            navigator.push(Route.Flash(Const.Value.UNINSTALL))
                        },
                        onNavigateToAbout = {
                            navigator.push(Route.About)
                        },
                        snackbarHostState = snackbarHostState
                    )
                    1 -> if (isCurrentPage || contentReady) SuperuserScreen(
                        viewModel = superuserViewModel,
                        contentBottomPadding = contentBottomPadding,
                        isActive = isCurrentPage,
                        snackbarHostState = snackbarHostState
                    )
                    2 -> if (isCurrentPage || contentReady) ModuleScreen(
                        viewModel = moduleViewModel,
                        contentBottomPadding = contentBottomPadding,
                        isActive = isCurrentPage,
                        onInstallModuleFromLocal = { uris ->
                            if (uris.isNotEmpty()) {
                                navigator.push(Route.Flash(Const.Value.FLASH_ZIP, uris.map(Uri::toString)))
                            }
                        },
                        onOpenRepo = {
                            navigator.push(Route.ModuleRepoList)
                        },
                        onRunAction = { id, name ->
                            navigator.push(Route.Action(id, name))
                        },
                        onOpenWebUi = { id, name ->
                            context.startActivity(
                                context.intent<io.github.seyud.weave.ui.webui.WebUIActivity>()
                                    .putExtra("id", id)
                                    .putExtra("name", name)
                            )
                        },
                        snackbarHostState = snackbarHostState
                    )
                    3 -> if (isCurrentPage || contentReady) SettingsScreen(
                        viewModel = settingsViewModel,
                        contentBottomPadding = contentBottomPadding,
                        isActive = isCurrentPage,
                        onNavigateToLog = {
                            navigator.push(Route.Log)
                        },
                        onNavigateToAppLanguage = {
                            onCurrentTabChanged(3)
                            navigator.push(Route.AppLanguage)
                        },
                        onNavigateToDenyListConfig = {
                            navigator.push(Route.Deny)
                        },
                        onSuperuserModeChanged = {
                            moduleViewModel.refresh(showProgress = false)
                        },
                        onNavigateToColorPalette = {
                            navigator.push(Route.ColorPalette)
                        },
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}
