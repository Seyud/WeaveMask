package io.github.seyud.weave.core.utils

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.os.UserManager
import android.system.Os
import androidx.core.content.getSystemService
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.intent
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.internal.UiThreadHandler
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.parcelablelist.ParcelableListSlice
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.AbstractQueuedSynchronizer

class RootUtils(stub: Any?) : RootService() {

    private val className: String = stub?.javaClass?.name ?: javaClass.name
    private lateinit var am: ActivityManager

    constructor() : this(null)

    init {
        Timber.plant(object : Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                super.log(priority, "Magisk", message, t)
            }
        })
    }

    override fun onCreate() {
        am = getSystemService()!!
    }

    override fun getComponentName(): ComponentName {
        return ComponentName(packageName, className)
    }

    override fun onBind(intent: Intent): IBinder {
        return object : IRootUtils.Stub() {
            override fun getAppProcess(pid: Int) = safe(null) { getAppProcessImpl(pid) }
            override fun getFileSystem(): IBinder = FileSystemManager.getService()
            override fun addSystemlessHosts() = safe(false) { addSystemlessHostsImpl() }
            override fun getInstalledApplications(flags: Int) = safe(emptyList()) { getInstalledApplicationsImpl(flags) }
            override fun getUserIds() = safe(intArrayOf(0)) { getAllUserIds() }
            override fun getPackages(flags: Int) = ParcelableListSlice(safe(emptyList()) { getPackagesImpl(flags) })
        }
    }

    private fun getAppProcessImpl(_pid: Int): ActivityManager.RunningAppProcessInfo? {
        val procList = am.runningAppProcesses
        var pid = _pid
        while (pid > 1) {
            val proc = procList.find { it.pid == pid }
            if (proc != null)
                return proc

            // Stop find when root process
            if (Os.stat("/proc/$pid").st_uid == 0) {
                return null
            }

            // Find PPID
            File("/proc/$pid/status").useLines {
                val line = it.find { l -> l.startsWith("PPid:") } ?: return null
                pid = line.substring(5).trim().toInt()
            }
        }
        return null
    }

    private fun addSystemlessHostsImpl(): Boolean {
        val module = File(Const.MODULE_PATH, "hosts")
        if (module.exists()) return true
        val hosts = File(module, "system/etc/hosts")
        if (hosts.parentFile?.mkdirs() != true) return false
        File(module, "module.prop").outputStream().writer().use {
            it.write("""
                id=hosts
                name=Systemless Hosts
                version=1.0
                versionCode=1
                author=Magisk
                description=Magisk app built-in systemless hosts module
            """.trimIndent())
        }
        File("/system/etc/hosts").copyTo(hosts)
        File(module, "update").createNewFile()
        return true
    }

    private fun getInstalledApplicationsAsUser(flags: Int, userId: Int): List<ApplicationInfo> {
        return try {
            val pm: PackageManager = packageManager
            val method = pm.javaClass.getDeclaredMethod(
                "getInstalledApplicationsAsUser",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(pm, flags, userId).toTypedList()
        } catch (e: Throwable) {
            Timber.e(e, "getInstalledApplicationsAsUser reflection failed")
            emptyList()
        }
    }

    private fun getAllUserIds(): IntArray {
        val um = getSystemService(USER_SERVICE) as? UserManager ?: return intArrayOf(0)
        // Try getUsers(boolean excludeDying) - API 17+
        try {
            val method = um.javaClass.getMethod("getUsers", Boolean::class.javaPrimitiveType)
            val users = method.invoke(um, true) as? List<*>
            if (!users.isNullOrEmpty()) {
                return users.mapNotNull { 
                    it?.javaClass?.getMethod("getUserId")?.invoke(it) as? Int 
                }.toIntArray()
            }
        } catch (e: Exception) {
            Timber.w(e, "getUsers reflection failed")
        }
        // Try getAliveUsers() - API 31+
        try {
            val method = um.javaClass.getMethod("getAliveUsers")
            val users = method.invoke(um) as? List<*>
            if (!users.isNullOrEmpty()) {
                return users.mapNotNull { 
                    it?.javaClass?.getMethod("getUserId")?.invoke(it) as? Int 
                }.toIntArray()
            }
        } catch (e: Exception) {
            Timber.e(e, "getAliveUsers reflection failed")
        }
        return intArrayOf(0)
    }

    private fun getInstalledApplicationsImpl(flags: Int): List<ApplicationInfo> {
        val apps = ArrayList<ApplicationInfo>()
        for (userId in getAllUserIds()) {
            apps.addAll(getInstalledApplicationsAsUser(flags, userId))
        }
        return apps
    }

    /**
     * Get installed packages for a specific user via reflection.
     * This bypasses the QUERY_ALL_PACKAGES permission requirement.
     */
    private fun getInstalledPackagesAsUser(flags: Int, userId: Int): List<PackageInfo> {
        return try {
            val pm: PackageManager = packageManager
            val method = pm.javaClass.getDeclaredMethod(
                "getInstalledPackagesAsUser",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(pm, flags, userId).toTypedList()
        } catch (e: Throwable) {
            Timber.e(e, "getInstalledPackagesAsUser reflection failed")
            emptyList()
        }
    }

    /**
     * Get all installed packages across all users.
     * Uses root privileges to bypass package visibility restrictions.
     */
    private fun getPackagesImpl(flags: Int): List<PackageInfo> {
        val packages = ArrayList<PackageInfo>()
        for (userId in getAllUserIds()) {
            packages.addAll(getInstalledPackagesAsUser(flags, userId))
        }
        return packages
    }

    object Connection : AbstractQueuedSynchronizer(), ServiceConnection {
        init {
            state = 1
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Timber.d("onServiceConnected")
            IRootUtils.Stub.asInterface(service).let {
                remoteService = it
                fs = FileSystemManager.getRemote(it.fileSystem)
            }
            releaseShared(1)
            Info.isRootServiceConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            state = 1
            remoteService = null
            Info.isRootServiceConnected.value = false
            bind(Intent().setComponent(name), this)
        }

        override fun tryAcquireShared(acquires: Int) = if (state == 0) 1 else -1

        override fun tryReleaseShared(releases: Int): Boolean {
            // Decrement count; signal when transition to zero
            while (true) {
                val c = state
                if (c == 0)
                    return false
                val n = c - 1
                if (compareAndSetState(c, n))
                    return n == 0
            }
        }

        fun await() {
            if (!Info.isRooted)
                return
            if (!ShellUtils.onMainThread()) {
                acquireSharedInterruptibly(1)
            } else if (state != 0) {
                throw IllegalStateException("Cannot await on the main thread")
            }
        }

        fun isReady(): Boolean = state == 0
    }

    companion object {
        var bindTask: Shell.Task? = null
        var fs: FileSystemManager = FileSystemManager.getLocal()
            get() {
                Connection.await()
                return field
            }
            private set
        @Volatile
        private var remoteService: IRootUtils? = null
        private val obj: IRootUtils?
            get() {
                Connection.await()
                return remoteService
            }

        fun getAppProcess(pid: Int) = safe(null) { obj?.getAppProcess(pid) }

        suspend fun addSystemlessHosts() =
            withContext(Dispatchers.IO) { safe(false) { obj?.addSystemlessHosts() ?: false } }

        fun getInstalledApplications(flags: Int): List<ApplicationInfo> =
            safe(emptyList()) { obj?.getInstalledApplications(flags) ?: emptyList() }

        /**
         * Get all installed packages via RootService.
         * This bypasses the QUERY_ALL_PACKAGES permission requirement on Android 11+.
         */
        fun getPackages(flags: Int): List<PackageInfo> =
            safe(emptyList()) { obj?.getPackages(flags)?.list?.filterIsInstance<PackageInfo>().orEmpty() }

        fun getUserIds(): IntArray = safe(intArrayOf(0)) { obj?.userIds ?: intArrayOf(0) }

        fun isServiceConnected(): Boolean = Connection.isReady() && remoteService != null

        fun ensureServiceConnected(timeoutMs: Long = ROOT_SERVICE_CONNECT_TIMEOUT_MS): Boolean {
            if (isServiceConnected()) return true

            val shell = runCatching { Shell.getShell() }
                .onFailure { Timber.w(it, "Failed to acquire root shell for RootService") }
                .getOrNull()
                ?: return false

            if (!shell.isRoot) {
                return false
            }

            if (ShellUtils.onMainThread()) {
                val task = bindTask ?: RootService.bindOrTask(
                    AppContext.intent<RootUtils>(),
                    UiThreadHandler.executor,
                    Connection,
                )
                bindTask = null
                runCatching {
                    task?.let { shell.execTask(it) }
                }.onFailure {
                    Timber.w(it, "Failed to execute RootService bind task")
                }
            } else {
                UiThreadHandler.handler.post {
                    runCatching {
                        RootService.bindOrTask(
                            AppContext.intent<RootUtils>(),
                            UiThreadHandler.executor,
                            Connection,
                        )?.let { shell.execTask(it) }
                    }.onFailure {
                        Timber.w(it, "Failed to bind RootService from background thread")
                    }
                }
            }

            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                if (isServiceConnected()) {
                    return true
                }
                try {
                    Thread.sleep(ROOT_SERVICE_POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }

            return isServiceConnected()
        }

        private inline fun <T> safe(default: T, block: () -> T): T {
            return try {
                block()
            } catch (e: Throwable) {
                // The process died unexpectedly
                Timber.e(e)
                default
            }
        }

        private const val ROOT_SERVICE_CONNECT_TIMEOUT_MS = 1_500L
        private const val ROOT_SERVICE_POLL_INTERVAL_MS = 50L
    }
}

private inline fun <reified T> Any?.toTypedList(): List<T> {
    val rawList = this as? List<*> ?: return emptyList()
    return rawList.filterIsInstance<T>()
}
