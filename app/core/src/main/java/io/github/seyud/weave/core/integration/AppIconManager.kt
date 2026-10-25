package io.github.seyud.weave.core.integration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.R

enum class AppIconVariant(
    val configValue: String,
    private val aliasNameSuffix: String,
) {
    CURRENT("current", ".ui.MainActivityLauncherCurrent"),
    LEGACY_WEAVE("legacy_weave", ".ui.MainActivityLauncherLegacyWeave"),
    LEGACY_MASK("legacy_mask", ".ui.MainActivityLauncherLegacyMask");

    fun componentName(context: Context) = ComponentName(
        context.packageName,
        context.packageName + aliasNameSuffix,
    )

    companion object {
        fun fromConfigValue(value: String?): AppIconVariant {
            return entries.firstOrNull { it.configValue == value } ?: CURRENT
        }
    }
}

object AppIconManager {
    private const val MAIN_ACTIVITY_SUFFIX = ".ui.MainActivity"

    fun isSupported(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = true

    fun currentVariant(): AppIconVariant = AppIconVariant.fromConfigValue(Config.appIconVariant)

    fun currentIconResId(context: Context): Int {
        if (!isSupported(context)) {
            return R.mipmap.ic_launcher
        }
        return when (currentVariant()) {
            AppIconVariant.CURRENT -> R.mipmap.ic_launcher
            AppIconVariant.LEGACY_WEAVE -> R.mipmap.ic_launcher_legacy_weave_icon
            AppIconVariant.LEGACY_MASK -> R.mipmap.ic_launcher_legacy_mask_icon
        }
    }

    fun currentShortcutIconResId(context: Context): Int {
        if (!isSupported(context)) {
            return R.mipmap.ic_launcher
        }
        return when (currentVariant()) {
            AppIconVariant.CURRENT -> R.drawable.ic_launcher_shortcut_current
            AppIconVariant.LEGACY_WEAVE -> R.drawable.ic_launcher_shortcut_legacy_weave
            AppIconVariant.LEGACY_MASK -> R.drawable.ic_launcher_shortcut_legacy_mask
        }
    }

    fun currentShortcutPreviewResId(context: Context): Int {
        if (!isSupported(context)) {
            return R.drawable.ic_launcher_preview_current
        }
        return when (currentVariant()) {
            AppIconVariant.CURRENT -> R.drawable.ic_launcher_preview_current
            AppIconVariant.LEGACY_WEAVE -> R.drawable.ic_launcher_legacy_weave_preview
            AppIconVariant.LEGACY_MASK -> R.drawable.ic_launcher_legacy_mask
        }
    }

    fun sync(context: Context): AppIconVariant {
        val variant = currentVariant()
        if (!isSupported(context)) {
            return variant
        }
        applyVariant(context, variant)
        if (Config.appIconVariant != variant.configValue) {
            Config.appIconVariant = variant.configValue
        }
        return variant
    }

    fun setVariant(context: Context, variant: AppIconVariant): Boolean {
        if (!isSupported(context)) {
            return false
        }
        Config.appIconVariant = variant.configValue
        applyVariant(context, variant)
        return true
    }

    fun createMainActivityIntent(context: Context): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            setClassName(context.packageName, context.packageName + MAIN_ACTIVITY_SUFFIX)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
    }

    private fun applyVariant(context: Context, selected: AppIconVariant) {
        val pm = context.packageManager
        AppIconVariant.entries.forEach { variant ->
            val newState = if (variant == selected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val component = variant.componentName(context)
            if (pm.getComponentEnabledSetting(component) != newState) {
                pm.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
