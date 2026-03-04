package com.topjohnwu.magisk.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import com.topjohnwu.magisk.ui.icon.SuperuserIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.R as CoreR
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.ui.component.FloatingBottomBar
import com.topjohnwu.magisk.ui.component.FloatingBottomBarItem
import com.topjohnwu.magisk.ui.theme.LocalEnableBlur
import com.topjohnwu.magisk.ui.theme.LocalEnableFloatingBottomBar
import com.topjohnwu.magisk.ui.theme.LocalEnableFloatingBottomBarBlur
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import com.topjohnwu.magisk.ui.flash.FlashScreen
import com.topjohnwu.magisk.ui.flash.FlashViewModel
import com.topjohnwu.magisk.ui.home.HomeScreen
import com.topjohnwu.magisk.ui.home.HomeViewModel
import com.topjohnwu.magisk.ui.install.InstallScreen
import com.topjohnwu.magisk.ui.install.InstallViewModel
import com.topjohnwu.magisk.ui.log.LogScreen
import com.topjohnwu.magisk.ui.log.LogViewModel
import com.topjohnwu.magisk.ui.module.ActionScreen
import com.topjohnwu.magisk.ui.module.ModuleScreen
import com.topjohnwu.magisk.ui.module.ModuleViewModel
import com.topjohnwu.magisk.ui.settings.AppLanguageScreen
import com.topjohnwu.magisk.ui.settings.SettingsScreen
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.superuser.SuperuserScreen
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel
import com.topjohnwu.magisk.ui.deny.DenyListScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.topjohnwu.magisk.ui.theme.WeaveMagiskTheme

/**
 * 主屏幕路由定义
 * 使用 sealed class 定义所有页面路由
 */
sealed class Screen(val route: String) {
    /** 主页（含 Tab 切换） */
    object Main : Screen("main")
    /** 主页 */
    object Home : Screen("home")
    /** 超级用户 */
    object Superuser : Screen("superuser")
    /** 模块 */
    object Module : Screen("module")
    /** 设置 */
    object Settings : Screen("settings")
    /** 日志 - 作为设置页的二级页面 */
    object Log : Screen("log")
    /** 应用语言 - 作为设置页的二级页面 */
    object AppLanguage : Screen("app_language")
    /** 安装 */
    object Install : Screen("install")
    /** 主题 */
    object Theme : Screen("theme")
    /** 刷写 */
    object Flash : Screen("flash/{action}?uri={uri}") {
        const val ACTION_ARG = "action"
        const val URI_ARG = "uri"

        fun createRoute(action: String, uri: Uri?): String {
            val encodedAction = Uri.encode(action)
            val encodedUri = uri?.toString()?.let(Uri::encode).orEmpty()
            return "flash/$encodedAction?uri=$encodedUri"
        }
    }
    /** 模块操作 */
    object Action : Screen("action/{id}?name={name}") {
        const val ID_ARG = "id"
        const val NAME_ARG = "name"

        fun createRoute(id: String, name: String): String {
            val encodedId = Uri.encode(id)
            val encodedName = Uri.encode(name)
            return "action/$encodedId?name=$encodedName"
        }
    }
    /** 拒绝列表 */
    object Deny : Screen("deny")
}

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
 *
 * @param pagerState Compose HorizontalPager 状态
 * @param coroutineScope 用于启动动画协程
 */
class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope
) {
    /** 当前选中的页面索引，立即更新，供底部导航栏读取 */
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    /** 是否正在执行导航动画（防止 syncPage 覆盖） */
    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    /**
     * 动画滚动到目标页面
     * 立即更新 selectedPage，然后通过 animateScrollBy 异步执行滚动动画
     */
    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return
        navJob?.cancel()

        // 立即更新选中态，底部栏图标即时响应
        selectedPage = targetIndex
        isNavigating = true

        // 计算动画参数：页距越大动画越长
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
                    // 容错：动画被中断时修正选中态
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    /**
     * 同步页面状态（用于手势滑动后更新选中态）
     * 仅在非导航动画期间执行，避免覆盖正在进行的动画目标
     */
    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

/**
 * 记忆并创建 MainPagerState
 */
@Composable
fun rememberMainPagerState(pagerState: PagerState): MainPagerState {
    val coroutineScope = rememberCoroutineScope()
    return remember(pagerState, coroutineScope) {
        MainPagerState(pagerState, coroutineScope)
    }
}

/**
 * 主屏幕 Composable 函数
 * 作为应用的主容器，包含 Tab 页面和二级页面导航
 */
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    flashViewModel: FlashViewModel,
    moduleViewModel: ModuleViewModel,
    superuserViewModel: SuperuserViewModel,
    logViewModel: LogViewModel,
    installViewModel: InstallViewModel,
    settingsViewModel: SettingsViewModel,
    initialMainTab: Int = 0,
    colorMode: Int = 0,
    keyColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    var rememberedMainTab by rememberSaveable {
        mutableIntStateOf(initialMainTab.coerceIn(0, 3))
    }

    WeaveMagiskTheme(colorMode = colorMode, keyColor = keyColor) {
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            modifier = modifier.fillMaxSize(),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = navTween()) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = navTween()) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = navTween()) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween()) }
        ) {
            // 主页面：包含底部导航栏 + HorizontalPager 内容切换
            composable(
                Screen.Main.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = navTween()) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween()) }
            ) {
                MainTabScreen(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    moduleViewModel = moduleViewModel,
                    superuserViewModel = superuserViewModel,
                    settingsViewModel = settingsViewModel,
                    logViewModel = logViewModel,
                    initialMainTab = rememberedMainTab,
                    onCurrentTabChanged = { rememberedMainTab = it }
                )
            }

            // 二级页面：全屏覆盖，无底部导航栏
            composable(
                Screen.Install.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = navTween()) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween()) }
            ) {
                InstallScreen(
                    viewModel = installViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToFlash = { action, uri ->
                        navController.navigate(Screen.Flash.createRoute(action, uri)) {
                            popUpTo(Screen.Install.route) {
                                inclusive = true
                            }
                        }
                    }
                )
            }

            composable(
                route = Screen.Flash.route,
                arguments = listOf(
                    navArgument(Screen.Flash.ACTION_ARG) { type = NavType.StringType },
                    navArgument(Screen.Flash.URI_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    }
                ),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = navTween()) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween()) }
            ) { backStackEntry ->
                val action = backStackEntry.arguments
                    ?.getString(Screen.Flash.ACTION_ARG)
                    ?.let(Uri::decode)
                    ?: Const.Value.FLASH_MAGISK
                val uriArg = backStackEntry.arguments
                    ?.getString(Screen.Flash.URI_ARG)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(Uri::decode)
                    ?.let(Uri::parse)

                // 使用 DisposableEffect 监听页面离开，处理侧滑返回的情况
                DisposableEffect(action) {
                    onDispose {
                        // 如果从模块安装页面离开，刷新模块列表
                        if (action == Const.Value.FLASH_ZIP) {
                            moduleViewModel.refresh()
                        }
                    }
                }

                FlashScreen(
                    viewModel = flashViewModel,
                    action = action,
                    additionalData = uriArg,
                    onNavigateBack = {
                        // 点击返回按钮时也会触发 DisposableEffect 的 onDispose
                        navController.popBackStack()
                    }
                )
            }

            composable(
                Screen.Log.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = navTween()) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween()) }
            ) {
                LogScreen(
                    viewModel = logViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                Screen.AppLanguage.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = navTween()) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween()) }
            ) {
                AppLanguageScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                Screen.Deny.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = navTween()) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween()) }
            ) {
                DenyListScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Action.route,
                arguments = listOf(
                    navArgument(Screen.Action.ID_ARG) { type = NavType.StringType },
                    navArgument(Screen.Action.NAME_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = navTween()) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = navTween()) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = navTween()) }
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString(Screen.Action.ID_ARG) ?: ""
                val name = backStackEntry.arguments?.getString(Screen.Action.NAME_ARG) ?: ""

                ActionScreen(
                    moduleId = id,
                    moduleName = name,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * 主 Tab 页面
 * 使用 HorizontalPager 实现 Tab 切换，所有页面预渲染 (beyondViewportPageCount=3)
 * 点击底部导航栏时图标立即高亮（selectedPage 即时更新），Pager 异步滑动动画
 * Tab 切换时底部导航栏保持不动，仅内容区域水平滑动
 */
@Composable
private fun MainTabScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    moduleViewModel: ModuleViewModel,
    superuserViewModel: SuperuserViewModel,
    settingsViewModel: SettingsViewModel,
    logViewModel: LogViewModel,
    initialMainTab: Int,
    onCurrentTabChanged: (Int) -> Unit,
) {
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current

    val hazeState = remember { HazeState() }
    val hazeStyle = if (enableBlur) {
        HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
        )
    } else {
        HazeStyle.Unspecified
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val pagerState = rememberPagerState(
        initialPage = initialMainTab.coerceIn(0, 3),
        pageCount = { 4 }
    )
    val mainPagerState = rememberMainPagerState(pagerState)
    BackHandler(enabled = mainPagerState.selectedPage != 0) {
        mainPagerState.animateToPage(0)
    }

    // 手势滑动 Pager 后同步选中态到底部栏
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
        onCurrentTabChanged(mainPagerState.pagerState.currentPage)
    }

    val destinations = BottomBarDestination.entries
    val isSuperuserEnabled = Info.showSuperUser
    val isModuleEnabled = Info.env.isActive && LocalModule.loaded()

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
                    isBlurEnabled = enableFloatingBottomBarBlur,
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
                    modifier = if (enableBlur) {
                        Modifier.hazeEffect(hazeState) {
                            style = hazeStyle
                            blurRadius = 30.dp
                            noiseFactor = 0f
                        }
                    } else Modifier,
                    color = if (enableBlur) Color.Transparent else MiuixTheme.colorScheme.surface,
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
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        val bottomPadding = paddingValues.calculateBottomPadding()

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 3, // 预渲染所有 4 页，切换零延迟
            userScrollEnabled = true, // 支持手势滑动与底部导航栏切换
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection)
                )
                .then(if (enableBlur) Modifier.hazeSource(state = hazeState) else Modifier)
                .then(
                    if (enableFloatingBottomBar && enableFloatingBottomBarBlur)
                        Modifier.layerBackdrop(backdrop)
                    else Modifier
                )
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    viewModel = homeViewModel,
                    bottomPadding = bottomPadding,
                    onNavigateToInstall = {
                        navController.navigate(Screen.Install.route)
                    },
                    onNavigateToUninstall = {
                        navController.navigate(
                            Screen.Flash.createRoute(Const.Value.UNINSTALL, null)
                        )
                    }
                )
                1 -> SuperuserScreen(
                    viewModel = superuserViewModel,
                    bottomPadding = bottomPadding
                )
                2 -> ModuleScreen(
                    viewModel = moduleViewModel,
                    bottomPadding = bottomPadding,
                    onInstallModuleFromLocal = { uri ->
                        navController.navigate(
                            Screen.Flash.createRoute(Const.Value.FLASH_ZIP, uri)
                        )
                    },
                    onRunAction = { id, name ->
                        navController.navigate(Screen.Action.createRoute(id, name))
                    }
                )
                3 -> SettingsScreen(
                    viewModel = settingsViewModel,
                    bottomPadding = bottomPadding,
                    onNavigateToLog = {
                        navController.navigate(Screen.Log.route)
                    },
                    onNavigateToAppLanguage = {
                        onCurrentTabChanged(3)
                        navController.navigate(Screen.AppLanguage.route)
                    },
                    onNavigateToDenyListConfig = {
                        navController.navigate(Screen.Deny.route)
                    }
                )
            }
        }
    }
}

/**
 * Miuix 风格导航过渡缓动函数
 * 基于阻尼弹簧物理模型，与 Miuix NavDisplay 使用相同参数
 *
 * @param response 弹簧响应时间
 * @param damping 阻尼系数
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

/** Miuix 风格导航动画缓动实例，与 Miuix NavDisplay 参数一致 (response=0.8, damping=0.95) */
private val NavAnimationEasing = NavTransitionEasing(0.8f, 0.95f)

/** 导航过渡动画 tween 规格，500ms + Miuix 弹簧缓动 */
private fun <T> navTween() = tween<T>(durationMillis = 500, easing = NavAnimationEasing)
