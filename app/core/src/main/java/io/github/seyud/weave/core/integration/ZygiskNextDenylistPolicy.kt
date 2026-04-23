package io.github.seyud.weave.core.integration

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ZygiskNextDenylistPolicy {
    suspend fun getWhitelistMode(): Boolean?
    suspend fun setWhitelistMode(enabled: Boolean): Boolean
}

object ShellZygiskNextDenylistPolicy : ZygiskNextDenylistPolicy {

    private const val ZYGISKD_PATH = "/data/adb/modules/zygisksu/bin/zygiskd"
    private const val DENYLIST_POLICY_FILE = "/data/adb/zygisksu/denylist_policy"

    @Volatile
    private var availabilityCache: Boolean? = null

    override suspend fun getWhitelistMode(): Boolean? = withContext(Dispatchers.IO) {
        if (!isZygiskNextAvailable()) {
            return@withContext null
        }
        return@withContext when (readPolicyValue()) {
            "1", "true", "whitelist" -> true
            else -> false
        }
    }

    override suspend fun setWhitelistMode(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isZygiskNextAvailable()) {
            return@withContext false
        }
        val policy = if (enabled) "whitelist" else "default"
        Shell.cmd("$ZYGISKD_PATH denylist-policy $policy").exec().isSuccess
    }

    private fun isZygiskNextAvailable(): Boolean {
        availabilityCache
            ?.let { return it }

        return synchronized(this) {
            availabilityCache
                ?.let { return@synchronized it }

            val available = Shell.cmd("[ -x '$ZYGISKD_PATH' ]").exec().isSuccess
            availabilityCache = available
            available
        }
    }

    private fun readPolicyValue(): String =
        Shell.cmd("cat '$DENYLIST_POLICY_FILE' 2>/dev/null || echo 0").exec()
            .out
            .firstOrNull()
            ?.trim()
            .orEmpty()
}
