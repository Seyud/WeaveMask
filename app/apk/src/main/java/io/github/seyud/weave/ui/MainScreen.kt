package io.github.seyud.weave.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBar
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBarBlur
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
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
import io.github.seyud.weave.ui.settings.AppLanguageScreen
import io.github.seyud.weave.ui.settings.SettingsScreen
import io.github.seyud.weave.ui.settings.SettingsViewModel
import io.github.seyud.weave.ui.superuser.SuperuserScreen
import io.github.seyud.weave.ui.superuser.SuperuserViewModel
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

        val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
        val duration = 100 * distance + 100

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            val layoutInfo = pagerState.layoutInfo
            val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
            val currentDistanceInPages =
                targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
            val scrollPixels = currentDistanceInPages * pageSize
            try {
                pagerState.animateScrollBy(
                    value = scrollPixels,
                    animationSpec = tween(easing = EaseInOut, durationMillis = duration)
                )
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
    initialMainTab: Int = 0,
    intentVersion: Int = 0,
    pendingFlashRequest: FlashRequest? = null,
    onPendingFlashRequestConsumed: () -> Unit = {},
    externalZipUris: List<ModuleInstallTarget>? = null,
    onExternalZipHandled: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val navigator = rememberNavigator(Route.Main)
    var snackbarBottomPadding by remember { mutableStateOf(0.dp) }
    var rememberedMainTab by rememberSaveable {
        mutableIntStateOf(initialMainTab.coerceIn(0, 3))
    }
    val currentRoute by remember(navigator) {
        derivedStateOf { navigator.current() }
    }

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
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    onBack = { navigator.pop() },
                    modifier = Modifier.fillMaxSize(),
                    entryProvider = entryProvider {
                        entry<Route.Main> {
                            MainTabScreen(
                                navigator = navigator,
                                homeViewModel = homeViewModel,
                                moduleViewModel = moduleViewModel,
                                superuserViewModel = superuserViewModel,
                                settingsViewModel = settingsViewModel,
                                logViewModel = logViewModel,
                                initialMainTab = rememberedMainTab,
                                onCurrentTabChanged = { rememberedMainTab = it },
                                onSnackbarBottomPaddingChanged = { snackbarBottomPadding = it }
                            )
                        }
                        entry<Route.Install> {
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
                        entry<Route.Flash> { key ->
                            val uriArgs = key.uriStrings
                                .filter { it.isNotEmpty() }
                                .map(Uri::parse)

                            DisposableEffect(key.action) {
                                onDispose {
                                    if (key.action == Const.Value.FLASH_ZIP) {
                                        moduleViewModel.refresh()
                                    }
                                }
                            }

                            FlashScreen(
                                viewModel = flashViewModel,
                                action = key.action,
                                additionalData = uriArgs,
                                onNavigateBack = { navigator.pop() }
                            )
                        }
                        entry<Route.Log> {
                            LogScreen(
                                viewModel = logViewModel,
                                onNavigateBack = { navigator.pop() }
                            )
                        }
                        entry<Route.ModuleRepoList> {
                            ModuleRepoScreen(
                                viewModel = moduleRepoViewModel,
                                onNavigateBack = { navigator.pop() },
                                onOpenModuleDetail = { moduleId ->
                                    navigator.push(Route.ModuleRepoDetail(moduleId))
                                }
                            )
                        }
                        entry<Route.ModuleRepoDetail> { key ->
                            ModuleRepoDetailScreen(
                                moduleId = key.moduleId,
                                onNavigateBack = { navigator.pop() }
                            )
                        }
                        entry<Route.AppLanguage> {
                            AppLanguageScreen(
                                onNavigateBack = { navigator.pop() }
                            )
                        }
                        entry<Route.Deny> {
                            DenyListScreen(
                                onNavigateBack = { navigator.pop() }
                            )
                        }
                        entry<Route.About> {
                            AboutScreen(
                                onNavigateBack = { navigator.pop() },
                                onLinkPressed = { link ->
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                    )
                                }
                            )
                        }
                        entry<Route.Action> { key ->
                            ActionScreen(
                                moduleId = key.moduleId,
                                moduleName = key.moduleName,
                                fromShortcut = key.fromShortcut,
                                onNavigateBack = { navigator.pop() }
                            )
                        }
                    }
                )
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
    val activity = LocalContext.current as? Activity ?: return
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
) {
    val context = LocalContext.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current

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
    val isModuleEnabled = Info.env.isActive && LocalModule.loaded()

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
                        FloatingBottomBarItem(
                            onClick = {
                                val enabled = when (index) {
                                    1 -> isSuperuserEnabled
                                    2 -> isModuleEnabled
                                    else -> true
                                }
                                if (enabled) mainPagerState.animateToPage(index)
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp)
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
                    modifier = Modifier.defaultBarBlur(blurBackdrop, surfaceColor),
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
                userScrollEnabled = true,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val isCurrentPage = page == settledPage
                when (page) {
                    0 -> if (isCurrentPage || contentReady) HomeScreen(
                        viewModel = homeViewModel,
                        contentBottomPadding = contentBottomPadding,
                        onNavigateToInstall = {
                            navigator.push(Route.Install)
                        },
                        onNavigateToUninstall = {
                            navigator.push(Route.Flash(Const.Value.UNINSTALL))
                        },
                        onNavigateToAbout = {
                            navigator.push(Route.About)
                        }
                    )
                    1 -> if (isCurrentPage || contentReady) SuperuserScreen(
                        viewModel = superuserViewModel,
                        contentBottomPadding = contentBottomPadding
                    )
                    2 -> if (isCurrentPage || contentReady) ModuleScreen(
                        viewModel = moduleViewModel,
                        contentBottomPadding = contentBottomPadding,
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
                        }
                    )
                    3 -> if (isCurrentPage || contentReady) SettingsScreen(
                        viewModel = settingsViewModel,
                        contentBottomPadding = contentBottomPadding,
                        onNavigateToLog = {
                            navigator.push(Route.Log)
                        },
                        onNavigateToAppLanguage = {
                            onCurrentTabChanged(3)
                            navigator.push(Route.AppLanguage)
                        },
                        onNavigateToDenyListConfig = {
                            navigator.push(Route.Deny)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Miuix 风格导航过渡缓动函数
 * 基于阻尼弹簧物理模型，与 Miuix NavDisplay 使用相同参数
 */
@Immutable
private class NavTransitionEasing(
    response: Float,
    damping: Float,
) : Easing {
    private val r: Float
    private val w: Float
    private val c2: Float

    init {
        val omega = 2.0 * PI / response
        val k = omega * omega
        val c = damping * 4.0 * PI / response

        w = (sqrt(4.0 * k - c * c) / 2.0).toFloat()
        r = (-c / 2.0).toFloat()
        c2 = r / w
    }

    override fun transform(fraction: Float): Float {
        val t = fraction.toDouble()
        val decay = exp(r * t)
        return (decay * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat()
    }
}

/** Miuix 风格导航动画缓动实例 */
private val NavAnimationEasing = NavTransitionEasing(0.8f, 0.95f)

/** 导航过渡动画 tween 规格，500ms + Miuix 弹簧缓动 */
private fun <T> navTween() = tween<T>(durationMillis = 500, easing = NavAnimationEasing)
