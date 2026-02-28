package com.topjohnwu.magisk.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
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
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.R as CoreR
import com.topjohnwu.magisk.core.model.module.LocalModule
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
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.res.painterResource
import com.topjohnwu.magisk.R
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
 * 底部导航项定义
 * 固定四个导航项：主页、超级用户、模块、设置
 *
 * @param labelResId 标签字符串资源 ID
 * @param icon 图标（使用 MiuixIcons）
 */
data class BottomNavItem(
    val labelResId: Int,
    val iconResId: Int
)

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
 * 获取固定的底部导航项列表
 */
private fun getBottomNavItems(): List<BottomNavItem> {
    return listOf(
        BottomNavItem(
            labelResId = CoreR.string.section_home,
            iconResId = R.drawable.ic_home_filled_md2
        ),
        BottomNavItem(
            labelResId = CoreR.string.superuser,
            iconResId = R.drawable.ic_superuser_filled_md2
        ),
        BottomNavItem(
            labelResId = CoreR.string.modules,
            iconResId = R.drawable.ic_module_filled_md2
        ),
        BottomNavItem(
            labelResId = CoreR.string.settings,
            iconResId = R.drawable.ic_settings_filled_md2
        )
    )
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
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.surface,
        tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
    )

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

    val bottomNavItems = getBottomNavItems()
    val isSuperuserEnabled = Info.showSuperUser
    val isModuleEnabled = Info.env.isActive && LocalModule.loaded()

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 30.dp
                        noiseFactor = 0f
                    }
                    .background(Color.Transparent)
            ) {
                HorizontalDivider(
                    thickness = 0.6.dp,
                    color = MiuixTheme.colorScheme.dividerLine.copy(0.8f)
                )
                MainBottomBar(
                    color = Color.Transparent,
                    items = bottomNavItems.map { item ->
                        BottomNavItem(
                            labelResId = item.labelResId,
                            iconResId = item.iconResId
                        )
                    },
                    selected = mainPagerState.selectedPage,
                    isEnabled = { index ->
                        when (index) {
                            1 -> isSuperuserEnabled
                            2 -> isModuleEnabled
                            else -> true
                        }
                    },
                    onClick = { index ->
                        mainPagerState.animateToPage(index)
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
                .hazeSource(state = hazeState)
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

@Composable
private fun MainBottomBar(
    items: List<BottomNavItem>,
    selected: Int,
    isEnabled: (Int) -> Boolean,
    onClick: (Int) -> Unit,
    color: Color = MiuixTheme.colorScheme.surface,
) {
    require(items.size in 2..5) { "BottomBar must have between 2 and 5 items" }
    val currentOnClick by rememberUpdatedState(onClick)
    val context = LocalContext.current

    val captionBarPaddings = WindowInsets.captionBar.only(WindowInsetsSides.Bottom).asPaddingValues()
    val captionBarBottomPaddingValue = captionBarPaddings.calculateBottomPadding()
    val animatedCaptionBarHeight by animateDpAsState(
        targetValue = if (captionBarBottomPaddingValue > 0.dp) captionBarBottomPaddingValue else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "captionBarHeight"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val itemHeight = 64.dp
            val itemWeight = 1f / items.size

            items.forEachIndexed { index, item ->
                val enabled = isEnabled(index)
                val isSelected = selected == index
                var isPressed by remember { mutableStateOf(false) }
                val onSurfaceContainerColor = MiuixTheme.colorScheme.onSurfaceContainer
                val onSurfaceContainerVariantColor = MiuixTheme.colorScheme.onSurfaceContainerVariant

                val activeColor = if (enabled) onSurfaceContainerColor else onSurfaceContainerColor.copy(alpha = 0.32f)
                val inactiveColor = if (enabled) onSurfaceContainerVariantColor else onSurfaceContainerVariantColor.copy(alpha = 0.32f)
                val tint = when {
                    isPressed -> if (isSelected) activeColor.copy(alpha = 0.5f) else inactiveColor.copy(alpha = 0.5f)
                    isSelected -> activeColor
                    else -> inactiveColor
                }
                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

                val pressModifier = if (enabled) {
                    Modifier.pointerInput(index) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = { currentOnClick(index) },
                        )
                    }
                } else {
                    Modifier
                }

                Column(
                    modifier = Modifier
                        .height(itemHeight)
                        .weight(itemWeight)
                        .then(pressModifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Image(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(26.dp),
                        painter = painterResource(id = item.iconResId),
                        contentDescription = context.getString(item.labelResId),
                        colorFilter = ColorFilter.tint(tint),
                    )
                    Text(
                        modifier = Modifier.padding(top = 1.dp, bottom = 8.dp),
                        text = context.getString(item.labelResId),
                        color = tint,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = fontWeight,
                    )
                }
            }
        }
        val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(navigationBarsPadding.calculateBottomPadding() + animatedCaptionBarHeight)
                .pointerInput(Unit) { detectTapGestures { } }
        )
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
