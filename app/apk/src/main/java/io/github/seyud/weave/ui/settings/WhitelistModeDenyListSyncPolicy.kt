package io.github.seyud.weave.ui.settings

import android.content.Intent
import io.github.seyud.weave.ui.superuser.isWhitelistMode

internal fun shouldUseLocalWhitelistDenyListSync(
    currentMode: Int,
    zygiskNextActive: Boolean,
): Boolean = isWhitelistMode(currentMode) && !zygiskNextActive

internal fun shouldQueuePassiveWhitelistReconcile(
    hasPendingLocalSync: Boolean,
    currentMode: Int,
    zygiskNextActive: Boolean,
): Boolean = !hasPendingLocalSync && shouldUseLocalWhitelistDenyListSync(currentMode, zygiskNextActive)

internal fun resolveAutoSyncPackageName(
    action: String?,
    packageName: String?,
    replacing: Boolean,
): String? {
    val resolvedPackageName = packageName ?: return null
    return when (action) {
        Intent.ACTION_PACKAGE_ADDED -> if (replacing) null else resolvedPackageName
        Intent.ACTION_PACKAGE_REPLACED -> resolvedPackageName
        else -> null
    }
}

internal fun resolveAutoSyncPackageName(intent: Intent): String? =
    resolveAutoSyncPackageName(
        action = intent.action,
        packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) ?: intent.data?.schemeSpecificPart,
        replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false),
    )
