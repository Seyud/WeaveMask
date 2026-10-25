package io.github.seyud.weave.core.data.magiskdb

import android.content.Context
import androidx.core.content.edit
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.ktx.deviceProtectedContext
import io.github.seyud.weave.core.model.su.SuPolicy

/**
 * Backs up su policies to SharedPreferences so they survive the native daemon's
 * prune_su_access() which may delete all policies from the database during early
 * boot when /data/user_de is not yet accessible.
 */
object PolicyBackupStore {

    private const val PREFS_NAME = "su_policy_backup"
    private const val KEY_POLICIES = "policies"

    private fun prefs() = AppContext.deviceProtectedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun backup(policies: List<SuPolicy>) {
        val entries = policies.joinToString(";") { p ->
            "${p.uid}:${p.policy}:${p.remain}:${if (p.logging) 1 else 0}:${if (p.notification) 1 else 0}"
        }
        // commit() on purpose: the backup must be durable before the daemon's
        // early-boot prune can wipe the policy database
        prefs().edit(commit = true) { putString(KEY_POLICIES, entries) }
    }

    fun backupSingle(policy: SuPolicy) {
        val current = load()
        val updated = current.filter { it.uid != policy.uid } + policy
        backup(updated)
    }

    fun remove(uid: Int) {
        val current = load().filter { it.uid != uid }
        backup(current)
    }

    fun load(): List<SuPolicy> {
        val raw = prefs().getString(KEY_POLICIES, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 5) {
                SuPolicy(
                    uid = parts[0].toIntOrNull() ?: return@mapNotNull null,
                    policy = parts[1].toIntOrNull() ?: SuPolicy.QUERY,
                    remain = parts[2].toLongOrNull() ?: 0L,
                ).apply {
                    logging = parts[3] == "1"
                    notification = parts[4] == "1"
                }
            } else null
        }
    }

    suspend fun restoreIfNeeded(dao: PolicyDao) {
        val backed = load()
        if (backed.isEmpty()) return
        for (policy in backed) {
            // Only permanent grants are worth resurrecting: an absent row already
            // means "not granted", so restoring a DENY just turns the interactive
            // Query flow into a hard deny the user never explicitly confirmed in
            // this session, and restoring a temporary (remain > 0) grant would
            // silently extend its expiry window on every restore.
            if (policy.policy < SuPolicy.ALLOW || policy.remain > 0) continue
            if (policy.remain < 0) {
                // Normalize so the restored row is a permanent (until=0) grant,
                // not an until<0 row that deleteOutdated() would purge
                policy.remain = 0
            }
            if (dao.fetch(policy.uid) == null) {
                dao.update(policy)
            }
        }
    }
}
