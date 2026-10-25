package io.github.seyud.weave.core

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import io.github.seyud.weave.core.integration.AppIconManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

open class App() : Application() {

    companion object {
        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod(
                    "setEnableOnBackInvokedCallback",
                    Boolean::class.javaPrimitiveType
                )
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppIconManager.sync(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"
            )
            // Use the config value instead of always enabling
            setEnableOnBackInvokedCallback(applicationInfo, Config.enablePredictiveBack)
        }
    }

    override fun attachBaseContext(context: Context) {
        if (context is Application) {
            AppContext.attachApplication(context)
        } else {
            super.attachBaseContext(context)
            AppContext.attachApplication(this)
        }
    }
}
