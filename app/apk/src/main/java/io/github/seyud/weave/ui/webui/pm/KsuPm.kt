package io.github.seyud.weave.ui.webui.pm

import android.content.Context
import android.webkit.JavascriptInterface
import io.github.seyud.weave.ui.webui.WebUiPackageRegistry

/**
 * Package manager API exposed to module WebUIs as `ksu.pm()`.
 * Ported from KernelSU manager (`ui/webui/pm/KsuPm.kt`); backed by the app's
 * root-assisted package registry so it is not limited by package visibility rules.
 */
class KsuPm(context: Context) {
    private val appContext = context.applicationContext

    @JavascriptInterface
    fun version(): Int = KSU_PM_VERSION

    @JavascriptInterface
    fun listPackages(type: String): String = WebUiPackageRegistry.listPackages(appContext, type)

    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String =
        WebUiPackageRegistry.getPackagesInfo(appContext, packageNamesJson)

    companion object {
        private const val KSU_PM_VERSION = 1
    }
}
