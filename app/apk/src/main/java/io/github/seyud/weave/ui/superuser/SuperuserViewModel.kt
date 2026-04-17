package io.github.seyud.weave.ui.superuser

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Process
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.arch.AsyncLoadViewModel
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.R
import io.github.seyud.weave.core.data.magiskdb.PolicyDao
import io.github.seyud.weave.core.ktx.getLabel
import io.github.seyud.weave.core.model.su.SuPolicy
import io.github.seyud.weave.dialog.SuperuserRevokeDialog
import io.github.seyud.weave.events.AuthEvent
import io.github.seyud.weave.events.SnackbarEvent
import io.github.seyud.weave.core.utils.InstalledPackageLoader
import io.github.seyud.weave.utils.asText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Stable
data class SuperuserUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val query: String = "",
    val showSystemApps: Boolean = false,
    val policies: List<PolicyCardUiState> = emptyList(),
    val errorMessage: String? = null,
    val revision: Long = 0L,
    val revokeDialogState: SuperuserRevokeDialog.DialogState = SuperuserRevokeDialog.DialogState(),
)

@Stable
data class PolicyCardUiState(
    val key: String,
    val uid: Int,
    val packageName: String,
    val appName: String,
    val applicationInfo: ApplicationInfo,
    val policy: Int,
    val shouldNotify: Boolean,
    val shouldLog: Boolean,
    val showSlider: Boolean,
    val isEnabled: Boolean,
    val isSystemApp: Boolean,
)

private data class PolicyEntry(
    val item: SuPolicy,
    val packageName: String,
    val isSharedUid: Boolean,
    val applicationInfo: ApplicationInfo,
    val appName: String,
)

class SuperuserViewModel(
    private val db: PolicyDao,
) : AsyncLoadViewModel() {

    private val _uiState = MutableStateFlow(SuperuserUiState())
    val uiState: StateFlow<SuperuserUiState> = _uiState.asStateFlow()

    private var allPolicies: List<PolicyEntry> = emptyList()

    internal fun policyKey(uid: Int, packageName: String) = "$uid:$packageName"

    private fun PolicyEntry.toCardUiState() = PolicyCardUiState(
        key = policyKey(item.uid, packageName),
        uid = item.uid,
        packageName = packageName,
        appName = if (isSharedUid) "[SharedUID] $appName" else appName,
        applicationInfo = applicationInfo,
        policy = item.policy,
        shouldNotify = item.notification,
        shouldLog = item.logging,
        showSlider = Config.suRestrict || item.policy == SuPolicy.RESTRICT,
        isEnabled = item.policy >= SuPolicy.ALLOW,
        isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
    )

    private fun findPolicyByKey(key: String) =
        allPolicies.firstOrNull { policyKey(it.item.uid, it.packageName) == key }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        publishFilteredPolicies()
    }

    fun toggleShowSystemApps() {
        _uiState.update { it.copy(showSystemApps = !it.showSystemApps) }
        publishFilteredPolicies()
    }

    fun refresh() {
        viewModelScope.launch {
            loadPolicies(isInitialLoad = false)
        }
    }

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        loadPolicies(isInitialLoad = true)
    }

    @SuppressLint("InlinedApi")
    private suspend fun loadPolicies(isInitialLoad: Boolean) {
        if (!Info.showSuperUser) {
            allPolicies = emptyList()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    policies = emptyList(),
                    errorMessage = null,
                    revision = it.revision + 1,
                )
            }
            return
        }

        if (isInitialLoad) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        } else {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        }

        try {
            val policies = withContext(Dispatchers.IO) {
                db.deleteOutdated()
                val myUid = AppContext.applicationInfo.uid
                db.delete(myUid)

                val allDbPolicies = db.fetchAll().associateBy { it.uid }.toMutableMap()
                val pm = AppContext.packageManager
                val packageInfos = InstalledPackageLoader.loadPackages(MATCH_UNINSTALLED_PACKAGES).items
                val installedUids = packageInfos.mapNotNull { it.applicationInfo?.uid }.toSet()


                allDbPolicies.keys.filter { it !in installedUids && it != Process.SYSTEM_UID }.forEach { uid ->
                    db.delete(uid)
                    allDbPolicies.remove(uid)
                }

                packageInfos.asSequence()
                    .mapNotNull { info ->
                        val appInfo = info.applicationInfo ?: return@mapNotNull null
                        if (appInfo.uid == myUid) return@mapNotNull null
                        
                        val policy = allDbPolicies.getOrPut(appInfo.uid) { SuPolicy(appInfo.uid) }
                        PolicyEntry(
                            item = policy,
                            packageName = info.packageName,
                            isSharedUid = info.sharedUserId != null,
                            applicationInfo = appInfo,
                            appName = appInfo.getLabel(pm),
                        )
                    }
                    .sortedWith(
                        compareByDescending<PolicyEntry> { it.item.policy >= SuPolicy.ALLOW }
                            .thenBy { it.appName.lowercase(Locale.ROOT) }
                            .thenBy { it.packageName }
                    )
                    .toList()
            }

            allPolicies = policies
            publishFilteredPolicies(errorMessage = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            allPolicies = emptyList()
            _uiState.update {
                it.copy(
                    policies = emptyList(),
                    errorMessage = e.message,
                    revision = it.revision + 1,
                )
            }
        } finally {
            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    private fun publishFilteredPolicies(errorMessage: String? = _uiState.value.errorMessage) {
        val state = _uiState.value
        val query = state.query.trim()
        val base = if (state.showSystemApps) {
            allPolicies
        } else {
            allPolicies.filter { (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        }
        val filtered = if (query.isEmpty()) {
            base
        } else {
            base.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        val mapped = filtered.map { it.toCardUiState() }
        _uiState.update {
            it.copy(
                policies = mapped,
                errorMessage = errorMessage,
                revision = it.revision + 1,
            )
        }
    }

    fun deleteByKey(key: String) {
        findPolicyByKey(key)?.let { onRevokePressed(key) }
    }

    fun toggleNotifyByKey(key: String) {
        findPolicyByKey(key)?.let { entry ->
            entry.item.notification = !entry.item.notification
            updateNotify(entry)
        }
    }

    fun toggleLogByKey(key: String) {
        findPolicyByKey(key)?.let { entry ->
            entry.item.logging = !entry.item.logging
            updateLogging(entry)
        }
    }

    fun updatePolicyByKey(key: String, policy: Int) {
        findPolicyByKey(key)?.let { entry ->
            updatePolicy(entry, policy)
        }
    }

    fun showRevokeDialog(key: String) {
        val item = findPolicyByKey(key) ?: return
        _uiState.update {
            it.copy(
                revokeDialogState = SuperuserRevokeDialog.DialogState(
                    visible = true,
                    appName = item.appName,
                ),
            )
        }
    }

    fun dismissRevokeDialog() {
        _uiState.update {
            it.copy(
                revokeDialogState = it.revokeDialogState.copy(visible = false),
            )
        }
    }

    fun confirmRevoke(key: String) {
        dismissRevokeDialog()
        findPolicyByKey(key)?.let { entry ->
            viewModelScope.launch {
                db.delete(entry.item.uid)
                entry.item.policy = SuPolicy.QUERY
                entry.item.notification = true
                entry.item.logging = true
                publishFilteredPolicies()
            }
        }
    }

    fun onRevokePressed(key: String) {
        val entry = findPolicyByKey(key) ?: return

        fun doRevoke() {
            viewModelScope.launch {
                db.delete(entry.item.uid)
                entry.item.policy = SuPolicy.QUERY
                entry.item.notification = true
                entry.item.logging = true
                publishFilteredPolicies()
            }
        }

        if (Config.suAuth) {
            AuthEvent { doRevoke() }.publish()
        } else {
            showRevokeDialog(key)
        }
    }

    private fun updateNotify(entry: PolicyEntry) {
        publishFilteredPolicies()
        viewModelScope.launch {
            db.update(entry.item)
            val res = if (entry.item.notification) {
                R.string.su_snack_notif_on
            } else {
                R.string.su_snack_notif_off
            }
            publishFilteredPolicies()
            SnackbarEvent(res.asText(entry.appName)).publish()
        }
    }

    private fun updateLogging(entry: PolicyEntry) {
        publishFilteredPolicies()
        viewModelScope.launch {
            db.update(entry.item)
            val res = if (entry.item.logging) {
                R.string.su_snack_log_on
            } else {
                R.string.su_snack_log_off
            }
            publishFilteredPolicies()
            SnackbarEvent(res.asText(entry.appName)).publish()
        }
    }

    private fun updatePolicy(entry: PolicyEntry, policy: Int) {
        if (entry.item.policy == policy) return

        fun updateState() {
            viewModelScope.launch {
                val isRevoking = policy < SuPolicy.DENY
                if (isRevoking) {
                    db.delete(entry.item.uid)
                    entry.item.policy = SuPolicy.QUERY
                    entry.item.remain = -1
                    entry.item.notification = true
                    entry.item.logging = true
                } else {

                    val actualPolicy = if (policy == SuPolicy.ALLOW && Config.suRestrict) {
                        SuPolicy.RESTRICT
                    } else {
                        policy
                    }
                    entry.item.policy = actualPolicy

                    entry.item.remain = 0
                    db.update(entry.item)
                }
                
                publishFilteredPolicies()
                
                val res = when {
                    isRevoking -> R.string.superuser_toggle_revoke
                    policy >= SuPolicy.ALLOW -> R.string.su_snack_grant
                    else -> R.string.su_snack_deny
                }
                SnackbarEvent(res.asText(entry.appName)).publish()
            }
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            updateState()
        }
    }
}
