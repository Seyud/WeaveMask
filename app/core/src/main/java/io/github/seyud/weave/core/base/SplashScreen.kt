package io.github.seyud.weave.core.base

import android.Manifest.permission.REQUEST_INSTALL_PACKAGES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.github.seyud.weave.StubApk
import io.github.seyud.weave.core.BuildConfig
import io.github.seyud.weave.core.BuildConfig.APP_PACKAGE_NAME
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.JobService
import io.github.seyud.weave.core.R
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.isRunningAsStub
import io.github.seyud.weave.core.ktx.writeTo
import io.github.seyud.weave.core.tasks.AppMigration
import io.github.seyud.weave.core.utils.RootUtils
import io.github.seyud.weave.view.Notifications
import io.github.seyud.weave.view.Shortcuts
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException

interface SplashScreenHost : IActivityExtension {
    val splashController: SplashController<*>

    fun onCreateUi(savedInstanceState: Bundle?)
    fun showInvalidStateMessage()
}

class SplashController<T>(private val activity: T)
    where T : ComponentActivity, T: SplashScreenHost {

    companion object {
        private var splashShown = false
    }

    private var shouldCreateUiOnResume = false
    private var uiReady = splashShown

    fun notifyUiReady() {
        uiReady = true
    }

    fun preOnCreate() {
        if (isRunningAsStub && !splashShown) {
            // Manually apply splash theme for stub
            activity.theme.applyStyle(R.style.StubSplashTheme, true)
        }
    }

    fun onCreate(savedInstanceState: Bundle?) {
        if (!isRunningAsStub) {
            val splashScreen = activity.installSplashScreen()
            splashScreen.setKeepOnScreenCondition { !splashShown || !uiReady }
        }

        if (splashShown) {
            doCreateUi(savedInstanceState)
        } else {
            Shell.getShell(Shell.EXECUTOR) {
                if (isRunningAsStub && !it.isRoot) {
                    activity.showInvalidStateMessage()
                    return@getShell
                }
                activity.initializeApp()
                activity.runOnUiThread {
                    splashShown = true
                    if (isRunningAsStub) {
                        // Re-launch main activity without splash theme
                        activity.relaunch()
                    } else {
                        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            doCreateUi(savedInstanceState)
                        } else {
                            shouldCreateUiOnResume = true
                        }
                    }
                }
            }
        }
    }

    fun onResume() {
        if (shouldCreateUiOnResume) {
            doCreateUi(null)
        }
    }

    private fun doCreateUi(savedInstanceState: Bundle?) {
        shouldCreateUiOnResume = false
        activity.onCreateUi(savedInstanceState)
    }

    private fun T.initializeApp() {
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
                // Relaunch the process after package migration
                StubApk.restartProcess(this)
            }
            return
        }

        // Validate stub APK
        if (isRunningAsStub && (
                // Version mismatch
                Info.stub!!.version != BuildConfig.STUB_VERSION ||
                // Not properly patched
                intent.component!!.className.contains(AppMigration.PLACEHOLDER))
        ) {
            withPermission(REQUEST_INSTALL_PACKAGES) { granted ->
                if (granted) {
                    lifecycleScope.launch {
                        val apk = File(cacheDir, "stub.apk")
                        try {
                            assets.open("stub.apk").writeTo(apk)
                            AppMigration.upgradeStub(activity, apk)?.let {
                                startActivity(it)
                            }
                        } catch (e: IOException) {
                            Timber.e(e)
                        }
                    }
                }
            }
            return
        }

        Notifications.setup()
        JobService.schedule(this)
        Shortcuts.setupDynamic(this)

        // Pre-fetch network services
        ServiceLocator.networkService

        // Wait for root service
        RootUtils.Connection.await()
    }
}
