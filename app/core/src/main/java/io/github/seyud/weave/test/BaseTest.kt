package io.github.seyud.weave.test

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import androidx.annotation.Keep
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.seyud.weave.core.R
import io.github.seyud.weave.core.utils.RootUtils
import com.topjohnwu.superuser.Shell
import org.junit.Assert.assertTrue

@Keep
interface BaseTest {
    val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()
    val appContext: Context get() = instrumentation.targetContext
    val testContext: Context get() = instrumentation.context
    val uiAutomation: UiAutomation get() = instrumentation.uiAutomation
    val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    companion object {
        private const val ROOT_RETRY_COUNT = 8
        private const val ROOT_RETRY_DELAY_MS = 750L
        private const val SU_DIALOG_WAIT_MS = 2_000L
        private const val SU_GRANT_DELAY_MS = 11_000L
        private const val UI_IDLE_TIMEOUT_MS = 5_000L

        fun prerequisite() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)

            launchTargetApp(context, device)

            val hasRoot = (0 until ROOT_RETRY_COUNT).any { attempt ->
                val shell = Shell.getShell()
                if (shell.isRoot) {
                    true
                } else {
                    maybeGrantRootPrompt(context, device)
                    if (attempt != ROOT_RETRY_COUNT - 1) {
                        Thread.sleep(ROOT_RETRY_DELAY_MS)
                    }
                    false
                }
            }

            assertTrue("Should have root access", hasRoot)
            assertTrue("Root service should be connected", RootUtils.ensureServiceConnected())
        }

        private fun launchTargetApp(context: Context, device: UiDevice) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: return
            instrumentation.runOnMainSync {
                context.startActivity(launchIntent)
            }
            device.wait(Until.hasObject(By.pkg(context.packageName)), UI_IDLE_TIMEOUT_MS)
            device.waitForIdle()
        }

        private fun maybeGrantRootPrompt(context: Context, device: UiDevice) {
            val title = context.getString(R.string.su_request_title)
            val grant = context.getString(R.string.grant)
            val prompt = device.wait(Until.findObject(By.text(title)), SU_DIALOG_WAIT_MS) ?: return
            if (!prompt.isEnabled) return

            Thread.sleep(SU_GRANT_DELAY_MS)
            device.wait(Until.findObject(By.text(grant)), SU_DIALOG_WAIT_MS)?.click()
            device.waitForIdle()
        }
    }
}
