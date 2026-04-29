package io.github.seyud.weave.ui

import android.Manifest
import android.Manifest.permission.REQUEST_INSTALL_PACKAGES
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.topjohnwu.superuser.Shell
import io.github.seyud.weave.StubApk
import io.github.seyud.weave.arch.BaseViewModel
import io.github.seyud.weave.arch.ActivityExecutor
import io.github.seyud.weave.arch.ContextExecutor
import io.github.seyud.weave.arch.VMFactory
import io.github.seyud.weave.arch.UiEvent
import io.github.seyud.weave.arch.ViewModelHolder
import io.github.seyud.weave.arch.viewModel
import io.github.seyud.weave.core.BuildConfig
import io.github.seyud.weave.core.BuildConfig.APP_PACKAGE_NAME
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.JobService
import io.github.seyud.weave.core.base.ActivityExtension
import io.github.seyud.weave.core.base.IActivityExtension
import io.github.seyud.weave.core.base.launchPackage
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.integration.AppNotifications
import io.github.seyud.weave.core.integration.AppShortcuts
import io.github.seyud.weave.core.isRunningAsStub
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.ktx.writeTo
import io.github.seyud.weave.core.tasks.AppMigration
import io.github.seyud.weave.core.utils.RootUtils
import io.github.seyud.weave.events.SnackbarEvent
import io.github.seyud.weave.ui.component.MiuixConfirmDialog
import io.github.seyud.weave.ui.dialog.WeaveDialog
import io.github.seyud.weave.ui.dialog.WeaveDialogHost
import io.github.seyud.weave.ui.dialog.WeaveDialogHostContent
import io.github.seyud.weave.ui.flash.FlashRequest
import io.github.seyud.weave.ui.flash.FlashViewModel
import io.github.seyud.weave.ui.home.HomeViewModel
import io.github.seyud.weave.ui.install.InstallViewModel
import io.github.seyud.weave.ui.log.LogViewModel
import io.github.seyud.weave.ui.modulerepo.ModuleRepoViewModel
import io.github.seyud.weave.ui.module.ModuleInstallTarget
import io.github.seyud.weave.ui.module.ModuleViewModel
import io.github.seyud.weave.ui.module.state.copyModuleDocumentsToCache
import io.github.seyud.weave.ui.settings.SettingsViewModel
import io.github.seyud.weave.ui.superuser.SuperuserViewModel
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBar
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBarBlur
import io.github.seyud.weave.ui.theme.LocalHomeLayoutMode
import io.github.seyud.weave.ui.theme.WeaveMagiskTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import java.io.File
import java.io.IOException
import io.github.seyud.weave.core.R as CoreR

/**
 * 主 Activity 的 ViewModel
 * 继承自 BaseViewModel，用于管理主界面的基础状态
 */
class MainViewModel : BaseViewModel()

/**
 * 应用主 Activity
 * 使用 Jetpack Compose 构建用户界面
 * 实现 IActivityExtension 接口以支持权限请求等功能
 */
class MainActivity : AppCompatActivity(), IActivityExtension, ViewModelHolder, WeaveDialogHost {

    companion object {
        const val EXTRA_START_MAIN_TAB = "start_main_tab"
        const val EXTRA_FLASH_ACTION = "extra_flash_action"
        const val EXTRA_FLASH_URI = "extra_flash_uri"
        const val EXTRA_FLASH_URIS = "extra_flash_uris"

        private val startupLock = Any()
        private val pendingUiCreation = mutableListOf<(Boolean) -> Unit>()

        @Volatile
        private var appInitialized = false

        @Volatile
        private var initializationInProgress = false
    }

    /** Activity 扩展，用于处理权限请求等通用功能 */
    override val extension = ActivityExtension(this)

    /** 主 ViewModel 实例 */
    override val viewModel by viewModel<MainViewModel>()

    /** 主页 ViewModel */
    private val homeViewModel: HomeViewModel by viewModels { VMFactory }

    /** 模块 ViewModel */
    private val moduleViewModel: ModuleViewModel by viewModels { VMFactory }

    /** 模块仓库 ViewModel */
    private val moduleRepoViewModel: ModuleRepoViewModel by viewModels { VMFactory }

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
    private val activeDialogs = mutableStateListOf<WeaveDialog>()

    /** Intent 状态流，用于触发 LaunchedEffect 重新执行 */
    private val intentState = MutableStateFlow(0)
    private val pendingFlashRequestState = MutableStateFlow<FlashRequest?>(null)
    private val snackbarHostState = SnackbarHostState()

    /**
     * 处理新的 Intent
     * 当 Activity 已存在且收到新的 Intent 时调用
     * 用于处理外部应用通过"打开方式"打开 ZIP 文件
     *
     * @param intent 新的 Intent
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingFlashRequestState.value = consumePendingFlashRequest()
        intentState.value += 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Config.init(intent.getBundleExtra(Const.Key.PREV_CONFIG))
        val splashThemeRes = resolveSplashThemeRes()
        setTheme(splashThemeRes)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        extension.onCreate(savedInstanceState)

        syncPlatformSplashTheme(splashThemeRes)
        applySystemBarStyle(resolveDarkForSplash())
        splashScreen.setKeepOnScreenCondition { !appInitialized }

        ensureAppInitialized(savedInstanceState)
    }

    /**
     * 创建用户界面
     * 使用 Compose setContent 设置内容视图，并初始化业务逻辑
     *
     * @param savedInstanceState 保存的实例状态
     */
    @SuppressLint("InlinedApi")
    private fun createUi(savedInstanceState: Bundle?) {
        val initialMainTab = intent.getIntExtra(EXTRA_START_MAIN_TAB, 0)
        intent.removeExtra(EXTRA_START_MAIN_TAB)
        pendingFlashRequestState.value = consumePendingFlashRequest()

        // 检查是否通过"打开方式"启动（首次启动时检查）
        val initialExternalZipUris = checkForExternalZipIntent(intent)

        // 设置 Compose 内容
        setContent {
            val intentVersion by intentState.collectAsStateWithLifecycle()
            val pendingFlashRequest by pendingFlashRequestState.collectAsStateWithLifecycle()
            var colorMode by remember { mutableIntStateOf(Config.colorMode) }
            var keyColorInt by remember { mutableIntStateOf(Config.keyColor) }
            val keyColor = remember(keyColorInt) {
                if (keyColorInt == 0) null else Color(keyColorInt)
            }
            var enableBlur by remember { mutableStateOf(Config.enableBlur) }
            var enableFloatingBottomBar by remember { mutableStateOf(Config.enableFloatingBottomBar) }
            var enableFloatingBottomBarBlur by remember { mutableStateOf(Config.enableFloatingBottomBarBlur) }
            var enableSmoothCorner by remember { mutableStateOf(Config.enableSmoothCorner) }
            var pageScale by remember { mutableFloatStateOf(Config.pageScale) }
            var homeLayoutMode by remember { mutableIntStateOf(Config.homeLayoutMode) }

            val darkMode = when (colorMode) {
                2, 5 -> true
                0, 3 -> isSystemInDarkTheme()
                else -> false
            }

            DisposableEffect(darkMode) {
                applySystemBarStyle(darkMode)
                onDispose {}
            }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        Config.Key.COLOR_MODE -> {
                            colorMode = Config.colorMode
                            syncPlatformSplashTheme()
                        }
                        Config.Key.KEY_COLOR -> {
                            keyColorInt = Config.keyColor
                            syncPlatformSplashTheme()
                        }
                        Config.Key.ENABLE_BLUR -> enableBlur = Config.enableBlur
                        Config.Key.ENABLE_FLOATING_BOTTOM_BAR -> enableFloatingBottomBar = Config.enableFloatingBottomBar
                        Config.Key.ENABLE_FLOATING_BOTTOM_BAR_BLUR -> enableFloatingBottomBarBlur = Config.enableFloatingBottomBarBlur
                        Config.Key.ENABLE_SMOOTH_CORNER -> enableSmoothCorner = Config.enableSmoothCorner
                        Config.Key.PAGE_SCALE -> pageScale = Config.pageScale
                        Config.Key.HOME_LAYOUT_MODE -> homeLayoutMode = Config.homeLayoutMode
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
                LocalHomeLayoutMode provides homeLayoutMode,
            ) {
                // 处理外部应用通过"打开方式"打开 ZIP 文件
                var externalZipUris by remember { mutableStateOf(initialExternalZipUris) }

                // 监听外部 Intent 打开的 ZIP 文件（用于 Activity 已在后台时）
                ZipFileIntentHandler(
                    intentState = intentState,
                    onZipReceived = { uris -> externalZipUris = uris }
                )

                // 根 Scaffold 用于承载 overlay 弹层组件，使 OverlayDialog 等组件正确渲染
                WeaveMagiskTheme(
                    colorMode = colorMode,
                    keyColor = keyColor,
                    enableSmoothCorner = enableSmoothCorner,
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                        MainScreen(
                            homeViewModel = homeViewModel,
                            flashViewModel = flashViewModel,
                            moduleViewModel = moduleViewModel,
                            moduleRepoViewModel = moduleRepoViewModel,
                            superuserViewModel = superuserViewModel,
                            logViewModel = logViewModel,
                            installViewModel = installViewModel,
                            settingsViewModel = settingsViewModel,
                            initialMainTab = initialMainTab,
                            intentVersion = intentVersion,
                            pendingFlashRequest = pendingFlashRequest,
                            onPendingFlashRequestConsumed = {
                                pendingFlashRequestState.value = null
                            },
                            externalZipUris = externalZipUris,
                            onExternalZipHandled = { externalZipUris = null },
                            snackbarHostState = snackbarHostState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    MiuixConfirmDialog(
                        show = showAddShortcutDialog,
                        title = getString(CoreR.string.add_shortcut_title),
                        summary = getString(CoreR.string.add_shortcut_msg),
                        confirmText = getString(android.R.string.ok),
                        dismissText = getString(android.R.string.cancel),
                        onDismissRequest = { showAddShortcutDialog = false },
                        onConfirm = {
                            showAddShortcutDialog = false
                            AppShortcuts.addHomeIcon(this@MainActivity)
                        },
                    )

                    WeaveDialogHostContent(
                        dialog = activeDialogs.firstOrNull()
                    )
                }
            }
        }

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

    private fun ensureAppInitialized(savedInstanceState: Bundle?) {
        if (appInitialized) {
            createUi(savedInstanceState)
            return
        }

        val shouldStartInitialization: Boolean
        synchronized(startupLock) {
            if (appInitialized) {
                createUi(savedInstanceState)
                return
            }
            pendingUiCreation += { shouldCreateUi ->
                if (shouldCreateUi) {
                    runOnUiThread {
                        if (!isDestroyed && !isFinishing) {
                            createUi(savedInstanceState)
                        }
                    }
                }
            }
            shouldStartInitialization = !initializationInProgress
            if (shouldStartInitialization) {
                initializationInProgress = true
            }
        }

        if (!shouldStartInitialization) return

        Shell.getShell(Shell.EXECUTOR) { shell ->
            val shouldCreateUi = if (isRunningAsStub && !shell.isRoot) {
                showInvalidStateMessage()
                false
            } else {
                initializeApp()
            }
            val callbacks = synchronized(startupLock) {
                if (shouldCreateUi) {
                    appInitialized = true
                }
                initializationInProgress = false
                pendingUiCreation.toList().also { pendingUiCreation.clear() }
            }
            callbacks.forEach { callback -> callback(shouldCreateUi) }
        }
    }

    private fun initializeApp(): Boolean {
        val prevPkg = intent.getStringExtra(Const.Key.PREV_PKG) ?: launchPackage
        val prevConfig = intent.getBundleExtra(Const.Key.PREV_CONFIG)
        val isPackageMigration = prevPkg != null && prevConfig != null

        Config.init(prevConfig)

        if (packageName != APP_PACKAGE_NAME) {
            runCatching {
                // Hidden, remove io.github.seyud.weave if exist as it could be malware
                packageManager.getApplicationInfo(APP_PACKAGE_NAME, 0)
                Shell.cmd("(pm uninstall $APP_PACKAGE_NAME)& >/dev/null 2>&1").exec()
            }
        } else {
            if (Config.suManager.isNotEmpty()) {
                Config.suManager = ""
            }
            if (isPackageMigration) {
                Shell.cmd("(pm uninstall $prevPkg)& >/dev/null 2>&1").exec()
            }
        }

        if (isPackageMigration) {
            runOnUiThread {
                StubApk.restartProcess(this)
            }
            return false
        }

        if (isRunningAsStub && (
                Info.stub!!.version != BuildConfig.STUB_VERSION ||
                    intent.component!!.className.contains(AppMigration.PLACEHOLDER))
        ) {
            runOnUiThread {
                withPermission(REQUEST_INSTALL_PACKAGES) { granted ->
                    if (granted) {
                        lifecycleScope.launch {
                            val apk = File(cacheDir, "stub.apk")
                            try {
                                assets.open("stub.apk").writeTo(apk)
                                AppMigration.upgradeStub(this@MainActivity, apk)?.let {
                                    startActivity(it)
                                }
                            } catch (e: IOException) {
                                Timber.e(e)
                            }
                        }
                    }
                }
            }
            return false
        }

        AppNotifications.setup()
        JobService.schedule(this)
        AppShortcuts.setupDynamic(this)
        ServiceLocator.networkService
        RootUtils.Connection.await()
        return true
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
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !darkMode
        controller.isAppearanceLightNavigationBars = !darkMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun isSystemDarkForSplash(): Boolean {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun resolveDarkForSplash(): Boolean {
        return SplashThemeResolver.resolve(
            colorMode = Config.colorMode,
            keyColor = Config.keyColor,
            sdkInt = Build.VERSION.SDK_INT,
            isSystemDark = isSystemDarkForSplash(),
        ).dark
    }

    private fun resolveSplashThemeRes(): Int {
        return SplashThemeResolver.resolveThemeRes(
            colorMode = Config.colorMode,
            keyColor = Config.keyColor,
            sdkInt = Build.VERSION.SDK_INT,
            isSystemDark = isSystemDarkForSplash(),
        )
    }

    private fun syncPlatformSplashTheme(themeRes: Int = resolveSplashThemeRes()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setSplashScreenTheme(themeRes)
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
        viewModel.uiEvents.observe(this, this::onUiEventDispatched)
        homeViewModel.uiEvents.observe(this, this::onUiEventDispatched)
        moduleViewModel.uiEvents.observe(this, this::onUiEventDispatched)
        superuserViewModel.uiEvents.observe(this, this::onUiEventDispatched)
        logViewModel.uiEvents.observe(this, this::onUiEventDispatched)
        installViewModel.uiEvents.observe(this, this::onUiEventDispatched)
        flashViewModel.uiEvents.observe(this, this::onUiEventDispatched)
        settingsViewModel.uiEvents.observe(this, this::onUiEventDispatched)
        Info.isConnected.observe(this) { connected ->
            viewModel.onNetworkChanged(connected)
            moduleViewModel.onNetworkChanged(connected)
            homeViewModel.onNetworkChanged(connected)
            logViewModel.onNetworkChanged(connected)
            superuserViewModel.onNetworkChanged(connected)
        }
    }

    /**
     * 处理 UI 事件
     *
     * @param event 要处理的事件
     */
    fun showSnackbar(event: SnackbarEvent) {
        lifecycleScope.launch {
            while (true) {
                val current = snackbarHostState.newestSnackbarData() ?: break
                current.dismiss()
            }
            snackbarHostState.showSnackbar(
                message = event.resolveMessage(this@MainActivity),
                duration = event.resolveDuration(),
            )
        }
    }

    override fun onUiEventDispatched(event: UiEvent) {
        when (event) {
            is SnackbarEvent -> showSnackbar(event)
            is ContextExecutor -> event(this)
            is ActivityExecutor -> event(this)
            else -> Unit
        }
    }

    override fun showWeaveDialog(dialog: WeaveDialog) {
        runOnUiThread {
            if (!activeDialogs.contains(dialog)) {
                activeDialogs.add(dialog)
            }
        }
    }

    override fun dismissWeaveDialog(dialog: WeaveDialog) {
        runOnUiThread {
            activeDialogs.remove(dialog)
        }
    }

    /**
     * 显示无效状态消息
     * 当应用以 stub 模式运行但没有 root 权限时显示
     */
    @SuppressLint("InlinedApi")
    private fun showInvalidStateMessage(): Unit = runOnUiThread {
        WeaveDialog(this).apply {
            setTitle(CoreR.string.unsupport_nonroot_stub_title)
            setMessage(CoreR.string.unsupport_nonroot_stub_msg)
            setButton(WeaveDialog.ButtonType.POSITIVE) {
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
            WeaveDialog(this).apply {
                setTitle(CoreR.string.unsupport_magisk_title)
                setMessage(CoreR.string.unsupport_magisk_msg, Const.Version.MIN_VERSION)
                setButton(WeaveDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }

        // 检查是否存在其他 su 二进制文件
        if (!Info.isEmulator && Info.env.isActive && System.getenv("PATH")
                ?.split(':')
                ?.filterNot { File("$it/magisk").exists() }
                ?.any { File("$it/su").exists() } == true) {
            WeaveDialog(this).apply {
                setTitle(CoreR.string.unsupport_general_title)
                setMessage(CoreR.string.unsupport_other_su_msg)
                setButton(WeaveDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }

        // 检查是否为系统应用
        if (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            WeaveDialog(this).apply {
                setTitle(CoreR.string.unsupport_general_title)
                setMessage(CoreR.string.unsupport_system_app_msg)
                setButton(WeaveDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }

        // 检查是否安装在外部存储
        if (applicationInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0) {
            WeaveDialog(this).apply {
                setTitle(CoreR.string.unsupport_general_title)
                setMessage(CoreR.string.unsupport_external_storage_msg)
                setButton(WeaveDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
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

    /**
     * 检查 Intent 是否为外部应用通过"打开方式"打开 ZIP 文件
     * 用于首次启动时检查
     *
     * 注意：必须在 Activity 中立即处理 content URI，因为临时读取权限只授予给 Activity。
     * 这里会将文件复制到缓存目录，然后返回缓存文件的 URI。
     *
     * @param intent 要检查的 Intent
     * @return 如果是有效的 ZIP 文件则返回缓存文件的 URI，否则返回 null
     */
    private fun checkForExternalZipIntent(intent: Intent): List<ModuleInstallTarget>? {
        val uris = extractExternalZipUris(intent)
        if (uris.isEmpty()) return null

        // 检查 MIME type，允许 null（某些文件管理器不设置 type）
        val mimeType = intent.type
        if (!isSupportedZipMimeType(mimeType)) {
            return null
        }

        // 清除 Intent 数据防止重复处理
        intent.data = null
        intent.type = null
        intent.clipData = null

        // 立即将文件复制到缓存目录，因为此时 Activity 拥有临时读取权限
        return try {
            copyUriToCache(uris)
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy external ZIP to cache")
            null
        }
    }

    /**
     * 将 content URI 复制到缓存目录
     * 必须在 Activity 中调用，因为临时读取权限只授予给 Activity
     *
     * @param uris content URI 列表
     * @return 缓存文件的 file:// URI
     */
    private fun copyUriToCache(uris: List<Uri>): List<ModuleInstallTarget> {
        return copyModuleDocumentsToCache(
            context = this,
            sourceUris = uris,
            cacheDirectoryName = "external_module",
            fallbackName = "module.zip",
        )
    }

    private fun extractExternalZipUris(intent: Intent): List<Uri> {
        val orderedUris = LinkedHashSet<Uri>()
        intent.data?.let { orderedUris.add(it) }
        val clipData = intent.clipData
        if (clipData != null) {
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index)?.uri?.let { orderedUris.add(it) }
            }
        }
        return orderedUris.filter { it.scheme == "content" }
    }

    private fun isSupportedZipMimeType(mimeType: String?): Boolean {
        return mimeType == null ||
            mimeType == "*/*" ||
            mimeType == "application/zip" ||
            mimeType == "application/octet-stream" ||
            mimeType.contains("zip")
    }

    internal fun consumePendingFlashRequest(): FlashRequest? {
        val currentIntent = intent ?: return null
        val action = currentIntent.getStringExtra(EXTRA_FLASH_ACTION) ?: return null
        val uris = currentIntent.getStringArrayListExtra(EXTRA_FLASH_URIS)
            ?.map(Uri::parse)
            ?.takeIf { it.isNotEmpty() }
            ?: currentIntent.getStringExtra(EXTRA_FLASH_URI)?.let { listOf(Uri.parse(it)) }
            ?: emptyList()
        val startMainTab = currentIntent.getIntExtra(EXTRA_START_MAIN_TAB, -1)
            .takeIf { it >= 0 }

        currentIntent.removeExtra(EXTRA_FLASH_ACTION)
        currentIntent.removeExtra(EXTRA_FLASH_URI)
        currentIntent.removeExtra(EXTRA_FLASH_URIS)
        currentIntent.removeExtra(EXTRA_START_MAIN_TAB)
        if (currentIntent.action == FlashRequest.INTENT_FLASH) {
            currentIntent.action = null
        }

        return FlashRequest(action = action, dataUris = uris, startMainTab = startMainTab)
    }

    /**
     * 处理外部应用通过"打开方式"打开 ZIP 文件的 Intent
     * - 验证 Intent 有效性（scheme、mimeType）
     * - 清除 Intent 数据防止重复处理
     * - 立即将文件复制到缓存目录（因为临时读取权限只授予给 Activity）
     * - 通知 MainScreen 有外部 ZIP 文件待处理
     *
     * @param intentState Intent 状态流，用于触发 LaunchedEffect 重新执行
     * @param onZipReceived 接收到有效 ZIP URI 时的回调
     */
    @Composable
    private fun ZipFileIntentHandler(
        intentState: MutableStateFlow<Int>,
        onZipReceived: (List<ModuleInstallTarget>) -> Unit
    ) {
        val activity = this
        val intentStateValue by intentState.collectAsStateWithLifecycle()

        LaunchedEffect(intentStateValue) {
            val currentIntent = activity.intent
            val uris = currentIntent?.let(::extractExternalZipUris).orEmpty()
            if (uris.isEmpty()) return@LaunchedEffect

            // 检查 MIME type，允许 null（某些文件管理器不设置 type）
            val mimeType = currentIntent.type
            if (!isSupportedZipMimeType(mimeType)) {
                return@LaunchedEffect
            }

            // 清除 Intent 数据防止重复处理
            activity.intent.data = null
            activity.intent.type = null
            activity.intent.clipData = null

            // 立即将文件复制到缓存目录，因为此时 Activity 拥有临时读取权限
            val cachedUris = try {
                copyUriToCache(uris)
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy external ZIP to cache")
                emptyList()
            }

            // 通知外部 ZIP 文件已接收
            if (cachedUris.isNotEmpty()) {
                onZipReceived(cachedUris)
            }
        }
    }
}
