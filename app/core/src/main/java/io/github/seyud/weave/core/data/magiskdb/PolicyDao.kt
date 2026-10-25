package io.github.seyud.weave.core.data.magiskdb

import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.model.su.SuPolicy

private const val SELECT_QUERY = "SELECT (until - strftime(\"%s\", \"now\")) AS remain, *"

class PolicyDao : MagiskDB() {

    private var backupRestored = false

    suspend fun deleteOutdated() {
        val query = "DELETE FROM ${Table.POLICY} WHERE " +
            "(until > 0 AND until < strftime(\"%s\", \"now\")) OR until < 0"
        exec(query)
    }

    suspend fun delete(uid: Int) {
        val query = "DELETE FROM ${Table.POLICY} WHERE uid=${normalizeUid(uid)}"
        exec(query)
        PolicyBackupStore.remove(uid)
    }

    suspend fun fetch(uid: Int): SuPolicy? {
        // Deliberately NOT running ensureBackupRestored() here: fetch is on the
        // su request path (SuRequestHandler.init), and a restore write at that
        // point would flip an undetermined (Query) request into a stale policy
        // while the interactive dialog is about to be shown. Restoration only
        // happens via fetchAll() on the Superuser list load.
        val query = "$SELECT_QUERY FROM ${Table.POLICY} WHERE uid=${normalizeUid(uid)} LIMIT 1"
        return exec(query, ::toPolicy).firstOrNull()
    }

    suspend fun update(policy: SuPolicy) {
        policy.uid = normalizeUid(policy.uid)
        val map = policy.toMap()
        if (!Const.Version.atLeast_25_0()) {
            // Put in package_name for old database
            map["package_name"] = AppContext.packageManager.getNameForUid(policy.uid)!!
        }
        val query = "REPLACE INTO ${Table.POLICY} ${map.toQuery()}"
        exec(query)
        PolicyBackupStore.backupSingle(policy)
    }

    suspend fun fetchAll(): List<SuPolicy> {
        ensureBackupRestored()
        val query = "$SELECT_QUERY FROM ${Table.POLICY} WHERE uid/100000=${Const.USER_ID} " +
            "OR uid < $FIRST_APPLICATION_UID"
        val policies = exec(query, ::toPolicy).filterNotNull()
        if (PolicyBackupStore.load().isEmpty() && policies.isNotEmpty()) {
            PolicyBackupStore.backup(policies)
        }
        return policies
    }

    private suspend fun ensureBackupRestored() {
        if (backupRestored) return
        backupRestored = true
        PolicyBackupStore.restoreIfNeeded(this)
    }

    private fun toPolicy(map: Map<String, String>): SuPolicy? {
        val uid = map["uid"]?.toInt() ?: return null
        val policy = SuPolicy(uid)

        map["until"]?.toLong()?.let { until ->
            if (until <= 0) {
                policy.remain = until
            } else {
                map["remain"]?.toLong()?.let { policy.remain = it }
            }
        }

        map["policy"]?.toInt()?.let { policy.policy = it }
        map["logging"]?.toInt()?.let { policy.logging = it != 0 }
        map["notification"]?.toInt()?.let { policy.notification = it != 0 }
        return policy
    }


    companion object {
        private const val FIRST_APPLICATION_UID = 10000

        // Revert user insertion for policies, to normalize the uid to app id
        fun normalizeUid(uid: Int): Int {
            val appId = uid % 100000
            return if (appId < FIRST_APPLICATION_UID) appId else uid
        }

        // Revert normalizeUid
        fun denormalizeUid(uid: Int): Int {
            return if (uid < FIRST_APPLICATION_UID) (uid + Const.USER_ID * 100000) else uid
        }
    }
}
