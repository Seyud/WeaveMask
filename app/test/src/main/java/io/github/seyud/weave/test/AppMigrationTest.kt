package io.github.seyud.weave.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import androidx.annotation.Keep
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Keep
@RunWith(AndroidJUnit4::class)
class AppMigrationTest {

    companion object {
        private const val APP_PKG = "io.github.seyud.weave"
        private const val STUB_PKG = "repackaged.$APP_PKG"
        private const val RECEIVER_TIMEOUT = 20L
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.context
    private val uiAutomation get() = instrumentation.uiAutomation
    private val device get() = UiDevice.getInstance(instrumentation)
    private val registeredReceivers = mutableListOf<BroadcastReceiver>()

    class PackageRemoveMonitor(
        context: Context,
        private val packageName: String
    ) : BroadcastReceiver() {

        val latch = CountDownLatch(1)

        init {
            val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED)
            filter.addDataScheme("package")
            context.registerReceiver(this, filter)
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_PACKAGE_REMOVED)
                return
            val data = intent.data ?: return
            val pkg = data.schemeSpecificPart
            if (pkg == packageName) latch.countDown()
        }
    }

    @After
    fun tearDown() {
        device.pressHome()
        registeredReceivers.forEach(context::unregisterReceiver)
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(path: String): PackageInfo {
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_PROVIDERS
        return context.packageManager.getPackageArchiveInfo(path, flags)
            ?: throw AssertionError("Cannot parse package info: $path")
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(packageName: String): PackageInfo {
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_PROVIDERS
        return context.packageManager.getPackageInfo(packageName, flags)
    }

    private fun extractBundledStubApk(): File {
        val sourceDir = context.packageManager.getApplicationInfo(APP_PKG, 0).sourceDir
        val dataDir = File("/data/user/0/${context.packageName}").apply { mkdirs() }
        val dir = File(dataDir, "files").apply { mkdirs() }
        val output = File(dir, "bundled-stub.apk")
        val apk = java.util.zip.ZipFile(sourceDir)
        try {
            val entry = apk.getEntry("assets/stub.apk")
                ?: throw AssertionError("Cannot find bundled assets/stub.apk in $sourceDir")
            apk.getInputStream(entry).use { input ->
                output.outputStream().use { input.copyTo(it) }
            }
        } finally {
            apk.close()
        }
        return output
    }

    private fun normalizeTaskAffinity(info: ActivityInfo): String {
        val affinity = info.taskAffinity ?: return "<null>"
        return if (affinity == info.packageName) "<default>" else affinity
    }

    private fun assertDistinctActivityGroups(info: PackageInfo, label: String) {
        val duplicates = info.activities.orEmpty()
            .groupBy {
                "${it.exported}:${it.directBootAware}:${it.configChanges}:${normalizeTaskAffinity(it)}"
            }
            .filterValues { it.size > 1 }

        assertTrue(
            "$label has ambiguous activity groups: ${duplicates.keys}",
            duplicates.isEmpty()
        )
    }

    private fun assertBundledStubManifest() {
        val stubInfo = archivePackageInfo(extractBundledStubApk().path)
        assertDistinctActivityGroups(stubInfo, "Bundled stub APK")
        assertFalse(
            "Bundled stub should not ship androidx.startup.InitializationProvider",
            stubInfo.providers.orEmpty().any { it.name == "androidx.startup.InitializationProvider" }
        )
        assertFalse(
            "Bundled stub should not ship androidx.profileinstaller.ProfileInstallReceiver",
            stubInfo.receivers.orEmpty().any { it.name == "androidx.profileinstaller.ProfileInstallReceiver" }
        )
        assertFalse(
            "Bundled stub should not ship androidx.room.MultiInstanceInvalidationService",
            stubInfo.services.orEmpty().any { it.name == "androidx.room.MultiInstanceInvalidationService" }
        )
    }

    private fun assertInstalledAppMappingShape() {
        val launcherIntent = context.packageManager.getLaunchIntentForPackage(APP_PKG)
            ?: throw AssertionError("Cannot resolve launcher intent for $APP_PKG")
        assertTrue(
            "Installed app launcher should target the main package",
            launcherIntent.component?.packageName == APP_PKG
        )
    }

    private fun shell(command: String): String {
        val pfd = uiAutomation.executeShellCommand(command)
        return AutoCloseInputStream(pfd).reader().use { it.readText() }
    }

    private fun assertHiddenAppColdStart() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(STUB_PKG)
            ?: throw AssertionError("Cannot resolve launcher intent for $STUB_PKG")
        val component = launchIntent.component
            ?: throw AssertionError("Launcher intent for $STUB_PKG has no explicit component")

        shell("am force-stop $STUB_PKG")
        device.pressHome()

        val output = shell("am start -W -n ${component.flattenToShortString()}")
        assertFalse("Hidden app launch failed: $output", output.contains("Error:", ignoreCase = true))
        assertFalse("Hidden app launch failed: $output", output.contains("Exception", ignoreCase = true))
        assertTrue(
            "Hidden app did not reach foreground",
            device.wait(Until.hasObject(By.pkg(STUB_PKG)), TimeUnit.SECONDS.toMillis(10))
        )
    }

    private fun testAppMigration(pkg: String, method: String) {
        val receiver = PackageRemoveMonitor(context, pkg)
        registeredReceivers.add(receiver)

        // Trigger the test to run migration
        val pfd = uiAutomation.executeShellCommand(
            "am instrument -w --user 0 -e class .Environment#$method " +
                "$pkg.test/${AppTestRunner::class.java.name}"
        )
        val output = AutoCloseInputStream(pfd).reader().use { it.readText() }
        assertTrue("$method failed, inst out: $output", output.contains("OK ("))

        // Wait for migration to complete
        assertTrue(
            "$pkg uninstallation failed",
            receiver.latch.await(RECEIVER_TIMEOUT, TimeUnit.SECONDS)
        )
    }

    @Test
    fun testAppHide() {
        assertBundledStubManifest()
        assertInstalledAppMappingShape()
        testAppMigration(APP_PKG, "setupAppHide")
        assertHiddenAppColdStart()
    }

    @Test
    fun testAppRestore() {
        testAppMigration(STUB_PKG, "setupAppRestore")
    }
}
