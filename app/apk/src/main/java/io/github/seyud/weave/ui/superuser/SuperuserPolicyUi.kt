package io.github.seyud.weave.ui.superuser

import android.content.pm.ApplicationInfo
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.core.model.su.SuPolicy
import kotlin.math.roundToInt

internal fun normalizeSuperuserListMode(mode: Int): Int {
    return when (mode) {
        Config.Value.SU_MODE_BLACKLIST -> Config.Value.SU_MODE_BLACKLIST
        else -> Config.Value.SU_MODE_WHITELIST
    }
}

internal fun isWhitelistMode(mode: Int): Boolean =
    normalizeSuperuserListMode(mode) == Config.Value.SU_MODE_WHITELIST

internal fun defaultShowSystemAppsForMode(mode: Int): Boolean = !isWhitelistMode(mode)

internal fun shouldShowPolicySlider(
    policy: Int,
    suRestrict: Boolean,
): Boolean = suRestrict || policy == SuPolicy.RESTRICT

internal fun isInstalledPackage(flags: Int): Boolean =
    flags and ApplicationInfo.FLAG_INSTALLED != 0

internal fun isInstalledPackage(applicationInfo: ApplicationInfo): Boolean =
    isInstalledPackage(applicationInfo.flags)

internal fun isSystemApp(flags: Int): Boolean =
    flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

internal fun isSystemApp(applicationInfo: ApplicationInfo): Boolean =
    isSystemApp(applicationInfo.flags)

internal fun policyToSliderValue(policy: Int): Float {
    return when (policy) {
        SuPolicy.QUERY -> 0f
        SuPolicy.DENY -> 1f
        SuPolicy.RESTRICT -> 2f
        SuPolicy.ALLOW -> 3f
        else -> 0f
    }
}

internal fun sliderValueToPolicy(value: Float): Int {
    return when (value.roundToInt().coerceIn(0, 3)) {
        0 -> SuPolicy.QUERY
        1 -> SuPolicy.DENY
        2 -> SuPolicy.RESTRICT
        3 -> SuPolicy.ALLOW
        else -> SuPolicy.QUERY
    }
}

internal fun policyToTextRes(policy: Int): Int {
    return when (policy) {
        SuPolicy.QUERY -> CoreR.string.prompt
        SuPolicy.DENY -> CoreR.string.deny
        SuPolicy.RESTRICT -> CoreR.string.restrict
        SuPolicy.ALLOW -> CoreR.string.grant
        else -> CoreR.string.prompt
    }
}
