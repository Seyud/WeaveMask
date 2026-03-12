package io.github.seyud.weave.ui

import android.Manifest
import android.Manifest.permission.REQUEST_INSTALL_PACKAGES
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import io.github.seyud.weave.arch.BaseViewModel
import io.github.seyud.weave.arch.ActivityExecutor
import io.github.seyud.weave.arch.ContextExecutor
import io.github.seyud.weave.arch.VMFactory
import io.github.seyud.weave.arch.ViewEvent
import io.github.seyud.weave.arch.ViewModelHolder
import io.github.seyud.weave.arch.viewModel
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.base.ActivityExtension
import io.github.seyud.weave.core.base.IActivityExtension
import io.github.seyud.weave.core.base.SplashController
import io.github.seyud.weave.core.base.SplashScreenHost
import io.github.seyud.weave.core.isRunningAsStub
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.tasks.AppMigration
import io.github.seyud.weave.ui.flash.FlashViewModel
import io.github.seyud.weave.ui.home.HomeViewModel
import io.github.seyud.weave.ui.install.InstallViewModel
import io.github.seyud.weave.ui.log.LogViewModel
import io.github.seyud.weave.ui.module.ModuleViewModel
import io.github.seyud.weave.ui.component.MiuixConfirmDialog
import io.github.seyud.weave.ui.settings.SettingsViewModel
import io.github.seyud.weave.ui.superuser.SuperuserViewModel
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBar
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBarBlur
import io.github.seyud.weave.ui.theme.Theme
import io.github.seyud.weave.view.MagiskDialog
import io.github.seyud.weave.view.Shortcuts
import kotlinx.coroutines.launch
import java.io.File
import io.github.seyud.weave.core.R as CoreR

/**
 * 主 Activity 的 ViewModel
 * 继承自 BaseViewModel，用于管理主界面的基础状态
 */
class MainViewModel : BaseViewModel()

/**
 * 应用主 Activity
 * 使用 Jetpack Compose 构建用户界面
 * 实现 SplashScreenHost 接口以支持启动画面控制
 * 实现 IActivityExtension 接口以支持权限请求等功能
 */
class MainActivity : AppCompatActivity(), SplashScreenHost, IActivityExtension, ViewModelHolder {

    companion object {
        const val EXTRA_START_MAIN_TAB = "start_main_tab"
        const val EXTRA_FLASH_ACTION = "extra_flash_action"
        const val EXTRA_FLASH_URI = "extra_flash_uri"
    }

    /** Activity 扩展，用于处理权限请求等通用功能 */
    override val extension = ActivityExtension(this)

    /** 主 ViewModel 实例 */
    override val viewModel by viewModel<MainViewModel>()

    /** 启动画面控制器 */
    override val splashController = SplashController(this)

    /** 主页 ViewModel */
    private val homeViewModel: HomeViewModel by viewModels { VMFactory }

    /** 模块 ViewModel */
    private val moduleViewModel: ModuleViewModel by viewModels { VMFactory }

    /** 超级用户 ViewModel */
    private val superuserViewModel: SuperuserViewModel by viewModels { VMFactory }

    /** 日志 ViewModel */
    private val logViewModel: LogViewModel by viewModels { VMFactory }

    /** 刷写 ViewModel */
    private val flashViewModel: FlashViewModel by viewModels { VMFactory }

    /** 安装 ViewModel */
    private val installViewModel: InstallViewModel by viewModels { VMFactory }

    /** 设置 ViewModel */
    private val settingsViewModel: SettingsViewModel by viewModels { VMFactory }

    private var showAddShortcutDialog by mutableStateOf(false)

    /**
     * Activity 创建时的生命周期回调
     * 设置主题并初始化启动画面控制器
     *
     * @param savedInstanceState 保存的实例状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Theme.themeRes)
        applySystemBarStyle(resolveDarkMode(Config.colorMode))
        splashController.preOnCreate()
        super.onCreate(savedInstanceState)
        splashController.onCreate(savedInstanceState)
    }

    /**
     * Activity 恢复时的生命周期回调
     * 通知启动画面控制器
     */
    override fun onResume() {
        super.onResume()
        splashController.onResume()
    }

    /**
     * 创建用户界面
     * 使用 Compose setContent 设置内容视图，并初始化业务逻辑
     *
     * @param savedInstanceState 保存的实例状态
     */
    @SuppressLint("InlinedApi")
    override fun onCreateUi(savedInstanceState: Bundle?) {
        val initialMainTab = intent.getIntExtra(EXTRA_START_MAIN_TAB, 0)
        intent.removeExtra(EXTRA_START_MAIN_TAB)

        // 从下载完成的 PendingIntent 中提取 flash 参数
        val pendingFlashAction = intent.getStringExtra(EXTRA_FLASH_ACTION)
        val pendingFlashUri = intent.getStringExtra(EXTRA_FLASH_URI)?.let { Uri.parse(it) }
        intent.removeExtra(EXTRA_FLASH_ACTION)
        intent.removeExtra(EXTRA_FLASH_URI)

        // 设置 Compose 内容
        setContent {
            var colorMode by remember { mutableIntStateOf(Config.colorMode) }
            var keyColorInt by remember { mutableIntStateOf(Config.keyColor) }
            val keyColor = remember(keyColorInt) {
                if (keyColorInt == 0) null else Color(keyColorInt)
            }
            var enableBlur by remember { mutableStateOf(Config.enableBlur) }
            var enableFloatingBottomBar by remember { mutableStateOf(Config.enableFloatingBottomBar) }
            var enableFloatingBottomBarBlur by remember { mutableStateOf(Config.enableFloatingBottomBarBlur) }
            var pageScale by remember { mutableFloatStateOf(Config.pageScale) }

            val darkMode = when (colorMode) {
                2, 5 -> true
                0, 3 -> isSystemInDarkTheme()
                else -> false
            }

            DisposableEffect(darkMode) {
                updateSystemBarAppearance(darkMode)
                onDispose {}
            }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        Config.Key.COLOR_MODE -> colorMode = Config.colorMode
                        Config.Key.KEY_COLOR -> keyColorInt = Config.keyColor
                        Config.Key.ENABLE_BLUR -> enableBlur = Config.enableBlur
                        Config.Key.ENABLE_FLOATING_BOTTOM_BAR -> enableFloatingBottomBar = Config.enableFloatingBottomBar
                        Config.Key.ENABLE_FLOATING_BOTTOM_BAR_BLUR -> enableFloatingBottomBarBlur = Config.enableFloatingBottomBarBlur
                        Config.Key.PAGE_SCALE -> pageScale = Config.pageScale
                    }
                }
                Config.prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    Config.prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, pageScale) {
                Density(systemDensity.density * pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalDensity provides density,
                LocalEnableBlur provides enableBlur,
                LocalEnableFloatingBottomBar provides enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides enableFloatingBottomBarBlur,
            ) {
                MainScreen(
                    homeViewModel = homeViewModel,
                    flashViewModel = flashViewModel,
                    moduleViewModel = moduleViewModel,
                    superuserViewModel = superuserViewModel,
                    logViewModel = logViewModel,
                    installViewModel = installViewModel,
                    settingsViewModel = settingsViewModel,
                    initialMainTab = initialMainTab,
                    pendingFlashAction = pendingFlashAction,
                    pendingFlashUri = pendingFlashUri,
                    colorMode = colorMode,
                    keyColor = keyColor,
                    modifier = Modifier.fillMaxSize()
                )

                MiuixConfirmDialog(
                    show = showAddShortcutDialog,
                    title = getString(CoreR.string.add_shortcut_title),
                    summary = getString(CoreR.string.add_shortcut_msg),
                    confirmText = getString(android.R.string.ok),
                    dismissText = getString(android.R.string.cancel),
                    onDismissRequest = { showAddShortcutDialog = false },
                    onConfirm = {
                        showAddShortcutDialog = false
                        Shortcuts.addHomeIcon(this@MainActivity)
                    },
                )
            }
        }
        installSplashUiReadyObserver()

        // 显示不支持的消息对话框
        showUnsupportedMessage()
        // 询问是否创建主屏幕快捷方式
        askForHomeShortcut()

        // 请求通知权限（用于后台更新检查）
        if (Config.checkUpdate) {
            withPermission(Manifest.permission.POST_NOTIFICATIONS) {
                Config.checkUpdate = it
            }
        }

        // 开始观察 LiveData
        startObserveLiveData()
    }

    private fun installSplashUiReadyObserver() {
        val contentView = findViewById<ViewGroup>(android.R.id.content) ?: return
        val rootView = contentView.getChildAt(0) ?: contentView
        var handled = false

        fun markUiReady() {
            if (handled) return
            handled = true
            splashController.notifyUiReady()
            ViewCompat.setOnApplyWindowInsetsListener(rootView, null)
        }

        if (ViewCompat.getRootWindowInsets(rootView) != null) {
            markUiReady()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _: View, insets ->
            markUiReady()
            insets
        }

        rootView.post {
            if (!handled && ViewCompat.getRootWindowInsets(rootView) != null) {
                markUiReady()
            }
        }
        rootView.postDelayed({
            if (!handled) {
                markUiReady()
            }
        }, 500)
    }

    private fun applySystemBarStyle(darkMode: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { darkMode },
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ) { darkMode }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        updateSystemBarAppearance(darkMode)
    }

    private fun updateSystemBarAppearance(darkMode: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val useLightBars = !darkMode
        controller.isAppearanceLightStatusBars = useLightBars
        controller.isAppearanceLightNavigationBars = useLightBars
    }

    private fun resolveDarkMode(colorMode: Int): Boolean {
        return when (colorMode) {
            2, 5 -> true
            0, 3 -> {
                val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }
    }

    /**
     * 保存实例状态
     *
     * @param outState 输出的状态 Bundle
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        extension.onSaveInstanceState(outState)
    }

    /**
     * 开始观察 ViewModel 的 LiveData
     */
    override fun startObserveLiveData() {
        viewModel.viewEvents.observe(this, this::onEventDispatched)
        homeViewModel.viewEvents.observe(this, this::onEventDispatched)
        moduleViewModel.viewEvents.observe(this, this::onEventDispatched)
        superuserViewModel.viewEvents.observe(this, this::onEventDispatched)
        logViewModel.viewEvents.observe(this, this::onEventDispatched)
        installViewModel.viewEvents.observe(this, this::onEventDispatched)
        flashViewModel.viewEvents.observe(this, this::onEventDispatched)
        settingsViewModel.viewEvents.observe(this, this::onEventDispatched)
        Info.isConnected.observe(this) { connected ->
            viewModel.onNetworkChanged(connected)
            moduleViewModel.onNetworkChanged(connected)
            homeViewModel.onNetworkChanged(connected)
            logViewModel.onNetworkChanged(connected)
            superuserViewModel.onNetworkChanged(connected)
        }
    }

    /**
     * 处理 ViewEvent 事件
     *
     * @param event 要处理的事件
     */
    override fun onEventDispatched(event: ViewEvent) {
        when (event) {
            is ContextExecutor -> event(this)
            is ActivityExecutor -> event(this)
            else -> Unit
        }
    }

    /**
     * 显示无效状态消息
     * 当应用以 stub 模式运行但没有 root 权限时显示
     */
    @SuppressLint("InlinedApi")
    override fun showInvalidStateMessage(): Unit = runOnUiThread {
        MagiskDialog(this).apply {
            setTitle(CoreR.string.unsupport_nonroot_stub_title)
            setMessage(CoreR.string.unsupport_nonroot_stub_msg)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = CoreR.string.install
                onClick {
                    withPermission(REQUEST_INSTALL_PACKAGES) {
                        if (!it) {
                            toast(CoreR.string.install_unknown_denied, Toast.LENGTH_SHORT)
                            showInvalidStateMessage()
                        } else {
                            lifecycleScope.launch {
                                if (!AppMigration.restoreApp(this@MainActivity)) {
                                    toast(CoreR.string.failure, Toast.LENGTH_LONG)
                                }
                            }
                        }
                    }
                }
            }
            setCancelable(false)
            show()
        }
    }

    /**
     * 显示不支持的消息
     * 检查运行环境并显示相应的警告对话框
     */
    private fun showUnsupportedMessage() {
        // 检查 Magisk 版本是否不支持
        if (Info.env.isUnsupported) {
            MagiskDialog(this).apply {
                setTitle(CoreR.string.unsupport_magisk_title)
                setMessage(CoreR.string.unsupport_magisk_msg, Const.Version.MIN_VERSION)
                setButton(MagiskDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }

        // 检查是否存在其他 su 二进制文件
        if (!Info.isEmulator && Info.env.isActive && System.getenv("PATH")
                ?.split(':')
                ?.filterNot { File("$it/magisk").exists() }
                ?.any { File("$it/su").exists() } == true) {
            MagiskDialog(this).apply {
                setTitle(CoreR.string.unsupport_general_title)
                setMessage(CoreR.string.unsupport_other_su_msg)
                setButton(MagiskDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }

        // 检查是否为系统应用
        if (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            MagiskDialog(this).apply {
                setTitle(CoreR.string.unsupport_general_title)
                setMessage(CoreR.string.unsupport_system_app_msg)
                setButton(MagiskDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }

        // 检查是否安装在外部存储
        if (applicationInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0) {
            MagiskDialog(this).apply {
                setTitle(CoreR.string.unsupport_general_title)
                setMessage(CoreR.string.unsupport_external_storage_msg)
                setButton(MagiskDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }
    }

    /**
     * 询问是否创建主屏幕快捷方式
     * 仅在 stub 模式下且支持快捷方式时询问
     */
    private fun askForHomeShortcut() {
        if (isRunningAsStub && !Config.askedHome &&
            ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            // 标记已询问过
            Config.askedHome = true
            showAddShortcutDialog = true
        }
    }
}
