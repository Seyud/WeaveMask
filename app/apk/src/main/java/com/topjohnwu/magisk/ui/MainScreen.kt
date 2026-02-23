package com.topjohnwu.magisk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.R as CoreR
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.ui.home.HomeScreen
import com.topjohnwu.magisk.ui.home.HomeViewModel
import com.topjohnwu.magisk.ui.log.LogScreen
import com.topjohnwu.magisk.ui.log.LogViewModel
import com.topjohnwu.magisk.ui.module.ModuleScreen
import com.topjohnwu.magisk.ui.module.ModuleViewModel
import com.topjohnwu.magisk.ui.settings.SettingsScreen
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.superuser.SuperuserScreen
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.topjohnwu.magisk.ui.theme.WeaveMagiskTheme

/**
 * 主屏幕路由定义
 * 使用 sealed class 定义所有页面路由
 */
sealed class Screen(val route: String) {
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
    /** 安装 */
    object Install : Screen("install")
    /** 主题 */
    object Theme : Screen("theme")
    /** 刷写 */
    object Flash : Screen("flash")
    /** 操作 */
    object Action : Screen("action")
    /** 拒绝列表 */
    object Deny : Screen("deny")
}

/**
 * 底部导航项定义
 * 固定四个导航项：主页、超级用户、模块、设置
 *
 * @param screen 对应的页面路由
 * @param labelResId 标签字符串资源 ID
 * @param icon 图标（使用 MiuixIcons）
 */
data class BottomNavItem(
    val screen: Screen,
    val labelResId: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * 主屏幕 Composable 函数
 * 作为应用的主容器，包含底部导航栏和页面导航
 *
 * @param homeViewModel 主页 ViewModel
 * @param moduleViewModel 模块 ViewModel
 * @param superuserViewModel 超级用户 ViewModel
 * @param logViewModel 日志 ViewModel
 * @param settingsViewModel 设置 ViewModel
 * @param colorMode 颜色模式 (0-5)
 * @param keyColor Monet 种子色，null 表示使用壁纸色
 * @param modifier Modifier
 */
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    moduleViewModel: ModuleViewModel,
    superuserViewModel: SuperuserViewModel,
    logViewModel: LogViewModel,
    settingsViewModel: SettingsViewModel,
    colorMode: Int = 0,
    keyColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    WeaveMagiskTheme(colorMode = colorMode, keyColor = keyColor) {
        val hazeState = remember { HazeState() }
        val hazeStyle = HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
        )

        Scaffold(
            modifier = modifier,
            bottomBar = {
                MainBottomBar(
                    navController = navController,
                    hazeState = hazeState,
                    hazeStyle = hazeStyle
                )
            },
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            val layoutDirection = LocalLayoutDirection.current
            val bottomPadding = paddingValues.calculateBottomPadding()
            MainNavHost(
                navController = navController,
                homeViewModel = homeViewModel,
                moduleViewModel = moduleViewModel,
                superuserViewModel = superuserViewModel,
                logViewModel = logViewModel,
                settingsViewModel = settingsViewModel,
                bottomPadding = bottomPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        top = paddingValues.calculateTopPadding(),
                        end = paddingValues.calculateEndPadding(layoutDirection)
                    )
                    .hazeSource(state = hazeState)
            )
        }
    }
}

/**
 * 获取固定的底部导航项列表
 * 固定四个导航项：主页、超级用户、模块、设置
 *
 * @return 底部导航项列表
 */
private fun getBottomNavItems(): List<BottomNavItem> {
    return listOf(
        BottomNavItem(
            screen = Screen.Home,
            labelResId = CoreR.string.section_home,
            icon = MiuixIcons.Download
        ),
        BottomNavItem(
            screen = Screen.Superuser,
            labelResId = CoreR.string.superuser,
            icon = MiuixIcons.Lock
        ),
        BottomNavItem(
            screen = Screen.Module,
            labelResId = CoreR.string.modules,
            icon = MiuixIcons.Folder
        ),
        BottomNavItem(
            screen = Screen.Settings,
            labelResId = CoreR.string.settings,
            icon = MiuixIcons.Settings
        )
    )
}

/**
 * 主底部导航栏
 * 使用 Miuix NavigationBar 组件实现，固定显示四个导航项
 *
 * @param navController 导航控制器
 * @param hazeState Haze 状态
 * @param hazeStyle Haze 样式
 */
@Composable
private fun MainBottomBar(
    navController: NavHostController,
    hazeState: HazeState,
    hazeStyle: HazeStyle
) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = getBottomNavItems()

    val isSuperuserEnabled = Info.showSuperUser
    val isModuleEnabled = Info.env.isActive && LocalModule.loaded()

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
        NavigationBar(
            color = Color.Transparent,
            showDivider = false
        ) {
            bottomNavItems.forEach { item ->
                val isEnabled = when (item.screen) {
                    Screen.Superuser -> isSuperuserEnabled
                    Screen.Module -> isModuleEnabled
                    else -> true
                }
                NavigationBarItem(
                    selected = currentRoute == item.screen.route,
                    onClick = {
                        if (isEnabled && currentRoute != item.screen.route) {
                            navController.navigate(item.screen.route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = item.icon,
                    label = context.getString(item.labelResId),
                    enabled = isEnabled
                )
            }
        }
    }
}

/**
 * 主导航宿主
 * 使用 NavHost 管理页面导航
 *
 * @param navController 导航控制器
 * @param homeViewModel 主页 ViewModel
 * @param moduleViewModel 模块 ViewModel
 * @param superuserViewModel 超级用户 ViewModel
 * @param logViewModel 日志 ViewModel
 * @param settingsViewModel 设置 ViewModel
 * @param bottomPadding 底部内边距，用于避免内容被底部导航栏遮挡
 * @param modifier Modifier
 */
@Composable
private fun MainNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    moduleViewModel: ModuleViewModel,
    superuserViewModel: SuperuserViewModel,
    logViewModel: LogViewModel,
    settingsViewModel: SettingsViewModel,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                bottomPadding = bottomPadding
            )
        }

        composable(Screen.Superuser.route) {
            SuperuserScreen(
                viewModel = superuserViewModel,
                bottomPadding = bottomPadding
            )
        }

        composable(Screen.Module.route) {
            ModuleScreen(
                viewModel = moduleViewModel,
                bottomPadding = bottomPadding
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                bottomPadding = bottomPadding,
                onNavigateToLog = {
                    navController.navigate(Screen.Log.route)
                }
            )
        }

        composable(Screen.Log.route) {
            LogScreen(
                viewModel = logViewModel
            )
        }
    }
}
