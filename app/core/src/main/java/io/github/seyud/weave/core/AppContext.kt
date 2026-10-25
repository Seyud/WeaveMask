package io.github.seyud.weave.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.system.Os
import androidx.profileinstaller.ProfileInstaller
import io.github.seyud.weave.core.base.UntrackedActivity
import io.github.seyud.weave.core.utils.LocaleSetting
import io.github.seyud.weave.core.utils.NetworkObserver
import io.github.seyud.weave.core.utils.RootUtils
import io.github.seyud.weave.core.utils.ShellInit
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.UiThreadHandler
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference

lateinit var AppApkPath: String
    private set

object AppContext : ContextWrapper(null),
    Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {

    val foregroundActivity: Activity? get() = ref.get()

    val appScope = CoroutineScope(SupervisorJob())

    private var ref = WeakReference<Activity>(null)
    private lateinit var application: Application
    private lateinit var networkObserver: NetworkObserver

    init {
        // Always log full stack trace with Timber
        Timber.plant(Timber.DebugTree())
        // Log first, then hand over to the system's default crash handling,
        // so crashes still go through the normal crash channel
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Timber.e(e)
            previousHandler?.uncaughtException(t, e)
        }

        Os.setenv("PATH", "${Os.getenv("PATH")}:/debug_ramdisk:/sbin", true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        LocaleSetting.instance.updateResource(resources)
    }

    override fun onActivityStarted(activity: Activity) {
        networkObserver.postCurrentState()
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is UntrackedActivity) return
        ref = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity is UntrackedActivity) return
        ref.clear()
    }

    override fun getApplicationContext() = application

    // The APK manifest already declares android:localeConfig and per-app locales
    // are handled natively via LocaleManager on 33+; Play Core is not used because
    // the app is never installed as a split bundle, so this library-scope check
    // does not apply
    @SuppressLint("AppBundleLocaleChanges")
    fun attachApplication(app: Application) {
        application = app
        val base = app.baseContext
        attachBaseContext(base)
        app.registerActivityLifecycleCallbacks(this)
        app.registerComponentCallbacks(this)

        AppApkPath = base.packageResourcePath
        resources.patch()

        val shellBuilder = Shell.Builder.create()
            .setFlags(Shell.FLAG_MOUNT_MASTER)
            .setInitializers(ShellInit::class.java)
            .setContext(this)
            .setTimeout(2)
        Shell.setDefaultBuilder(shellBuilder)
        Shell.EXECUTOR = Dispatchers.IO.asExecutor()
        RootUtils.bindTask = RootService.bindOrTask(
            intent<RootUtils>(),
            UiThreadHandler.executor,
            RootUtils.Connection
        )
        // Pre-heat the shell ASAP
        Shell.getShell(null) {}

        networkObserver = NetworkObserver.init(this)
        if (!BuildConfig.DEBUG) {
            appScope.launch(Dispatchers.IO) {
                ProfileInstaller.writeProfile(this@AppContext)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {}
    override fun onTrimMemory(level: Int) {}
}
