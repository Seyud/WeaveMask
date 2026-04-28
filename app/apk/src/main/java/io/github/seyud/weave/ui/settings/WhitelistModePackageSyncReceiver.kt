package io.github.seyud.weave.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.ui.superuser.SuperuserModeSyncCoordinator
import io.github.seyud.weave.ui.superuser.normalizeSuperuserListMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class WhitelistModePackageSyncReceiver(
    private val modeSync: SuperuserModeSyncCoordinator = SuperuserModeSyncCoordinator(),
    private val denyListCoordinator: WhitelistModeDenyListCoordinator = WhitelistModeDenyListCoordinator(),
    private val receiverScope: CoroutineScope = RECEIVER_SCOPE,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val packageName = intent?.let(::resolveAutoSyncPackageName) ?: return
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val currentMode = normalizeSuperuserListMode(Config.suListMode)
                val zygiskNextActive = modeSync.isZygiskNextActive()
                if (shouldUseLocalWhitelistDenyListSync(currentMode, zygiskNextActive)) {
                    denyListCoordinator.ensurePackageSynced(packageName)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val RECEIVER_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
