package io.github.seyud.weave.ui.superuser

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
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
import io.github.seyud.weave.core.utils.InstalledItemLoadResult
import io.github.seyud.weave.core.utils.InstalledItemSource
import io.github.seyud.weave.dialog.SuperuserRevokeDialog
import io.github.seyud.weave.events.AuthEvent
import io.github.seyud.weave.events.SnackbarEvent
import io.github.seyud.weave.core.utils.InstalledPackageLoader
import io.github.seyud.weave.core.utils.RootUtils
import io.github.seyud.weave.utils.asText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

internal interface SuperuserPolicyStore {
    suspend fun deleteOutdated()
    suspend fun delete(uid: Int)
    suspend fun fetchAll(): List<SuPolicy>
    suspend fun update(policy: SuPolicy)
}

private class PolicyDaoSuperuserPolicyStore(
    private val dao: PolicyDao,
) : SuperuserPolicyStore {
    override suspend fun deleteOutdated() = dao.deleteOutdated()

    override suspend fun delete(uid: Int) = dao.delete(uid)

    override suspend fun fetchAll(): List<SuPolicy> = dao.fetchAll()

    override suspend fun update(policy: SuPolicy) = dao.update(policy)
}

private const val ROOT_REFRESH_INTERVAL_MS = 350L
private const val ROOT_REFRESH_MAX_ATTEMPTS = 15

internal data class SuperuserLoadConfig(
    val loadPackages: (Int) -> InstalledItemLoadResult<PackageInfo> = { flags ->
        InstalledPackageLoader.loadPackages(flags)
    },
    val isSuperuserVisible: () -> Boolean = { Info.showSuperUser },
    val isRestrictEnabled: () -> Boolean = { Config.suRestrict },
    val appUid: () -> Int = { AppContext.applicationInfo.uid },
    val resolveAppName: (ApplicationInfo) -> String = { appInfo ->
        appInfo.getLabel(AppContext.packageManager)
    },
    val rootServiceConnected: () -> Boolean = { RootUtils.isServiceConnected() },
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val delayMillis: suspend (Long) -> Unit = { delay(it) },
    val rootRefreshIntervalMs: Long = ROOT_REFRESH_INTERVAL_MS,
    val rootRefreshMaxAttempts: Int = ROOT_REFRESH_MAX_ATTEMPTS,
)

private data class LoadedPolicies(
    val policies: List<PolicyEntry>,
    val source: InstalledItemSource,
    val shouldRefreshFromRoot: Boolean,
)

class SuperuserViewModel internal constructor(
    private val db: SuperuserPolicyStore,
    private val loadConfig: SuperuserLoadConfig = SuperuserLoadConfig(),
) : AsyncLoadViewModel() {

    constructor(db: PolicyDao) : this(PolicyDaoSuperuserPolicyStore(db))

    private val _uiState = MutableStateFlow(
        SuperuserUiState(
            showSystemApps = defaultShowSystemAppsForMode(currentSuperuserListMode()),
        ),
    )
    val uiState: StateFlow<SuperuserUiState> = _uiState.asStateFlow()

    private var allPolicies: List<PolicyEntry> = emptyList()
    private var loadedListMode = currentSuperuserListMode()
    private var rootRefreshJob: Job? = null

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
        showSlider = shouldShowPolicySlider(item.policy, loadConfig.isRestrictEnabled()),
        isEnabled = item.policy >= SuPolicy.ALLOW,
        isSystemApp = isSystemApp(applicationInfo),
    )

    private fun findPolicyByKey(key: String) =
        allPolicies.firstOrNull { policyKey(it.item.uid, it.packageName) == key }

    private fun currentSuperuserListMode(): Int = normalizeSuperuserListMode(Config.suListMode)

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
    private suspend fun loadPolicies(
        isInitialLoad: Boolean,
        showProgress: Boolean = true,
    ) {
        if (!loadConfig.isSuperuserVisible()) {
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

        val listMode = syncListModeState()

        if (showProgress) {
            if (isInitialLoad) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            }
        }

        try {
            val loadedPolicies = fetchPolicies(listMode)
            allPolicies = loadedPolicies.policies
            publishFilteredPolicies(errorMessage = null)
            if (loadedPolicies.shouldRefreshFromRoot) {
                scheduleRootRefreshIfNeeded()
            } else {
                rootRefreshJob?.cancel()
                rootRefreshJob = null
            }
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
            if (showProgress) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    private fun syncListModeState(): Int {
        val listMode = currentSuperuserListMode()
        if (listMode == loadedListMode) {
            return listMode
        }
        loadedListMode = listMode
        rootRefreshJob?.cancel()
        rootRefreshJob = null
        _uiState.update {
            it.copy(showSystemApps = defaultShowSystemAppsForMode(listMode))
        }
        return listMode
    }

    @SuppressLint("InlinedApi")
    private suspend fun fetchPolicies(listMode: Int): LoadedPolicies = withContext(loadConfig.ioDispatcher) {
        if (isWhitelistMode(listMode)) {
            fetchWhitelistPolicies()
        } else {
            fetchBlacklistPolicies()
        }
    }

    @SuppressLint("InlinedApi")
    private suspend fun fetchWhitelistPolicies(): LoadedPolicies {
        db.deleteOutdated()
        val myUid = loadConfig.appUid()
        db.delete(myUid)

        val allDbPolicies = db.fetchAll().associateBy { it.uid }.toMutableMap()
        val loadResult = loadConfig.loadPackages(MATCH_UNINSTALLED_PACKAGES)
        val packageInfos = loadResult.items.filter { info ->
            val appInfo = info.applicationInfo ?: return@filter false
            appInfo.uid != myUid && isInstalledPackage(appInfo)
        }

        if (loadResult.source == InstalledItemSource.ROOT) {
            val installedUids = packageInfos.mapNotNull { it.applicationInfo?.uid }.toSet()
            allDbPolicies.keys
                .filter { it !in installedUids && it != Process.SYSTEM_UID }
                .forEach { uid ->
                    db.delete(uid)
                    allDbPolicies.remove(uid)
                }
        }

        val policies = packageInfos.asSequence()
            .mapNotNull { info ->
                val appInfo = info.applicationInfo ?: return@mapNotNull null
                val policy = allDbPolicies.getOrPut(appInfo.uid) { SuPolicy(appInfo.uid) }
                PolicyEntry(
                    item = policy,
                    packageName = info.packageName,
                    isSharedUid = info.sharedUserId != null,
                    applicationInfo = appInfo,
                    appName = loadConfig.resolveAppName(appInfo),
                )
            }
            .sortedWith(
                compareByDescending<PolicyEntry> { it.item.policy >= SuPolicy.ALLOW }
                    .thenBy { it.appName.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName }
            )
            .toList()

        return LoadedPolicies(
            policies = policies,
            source = loadResult.source,
            shouldRefreshFromRoot = loadResult.shouldRefreshFromRoot,
        )
    }

    @SuppressLint("InlinedApi")
    private suspend fun fetchBlacklistPolicies(): LoadedPolicies {
        db.deleteOutdated()
        val myUid = loadConfig.appUid()
        db.delete(myUid)

        val pm = AppContext.packageManager
        val policies = ArrayList<PolicyEntry>()
        for (policy in db.fetchAll()) {
            if (policy.policy < SuPolicy.DENY) {
                db.delete(policy.uid)
                continue
            }

            val packageNames =
                if (policy.uid == Process.SYSTEM_UID) arrayOf("android")
                else pm.getPackagesForUid(policy.uid)
            if (packageNames == null) {
                db.delete(policy.uid)
                continue
            }

            val entries = packageNames.mapNotNull { packageName ->
                val packageInfo = runCatching {
                    pm.getPackageInfo(packageName, MATCH_UNINSTALLED_PACKAGES)
                }.getOrNull() ?: return@mapNotNull null
                packageInfo.toPolicyEntry(policy, myUid)
            }
            if (entries.isEmpty()) {
                db.delete(policy.uid)
                continue
            }
            policies.addAll(entries)
        }

        return LoadedPolicies(
            policies = policies.sortedWith(
                compareBy(
                    { it.appName.lowercase(Locale.ROOT) },
                    { it.packageName },
                ),
            ),
            source = InstalledItemSource.FALLBACK,
            shouldRefreshFromRoot = false,
        )
    }

    private fun PackageInfo.toPolicyEntry(
        policy: SuPolicy,
        myUid: Int,
    ): PolicyEntry? {
        val appInfo = applicationInfo ?: return null
        if (appInfo.uid == myUid || !isInstalledPackage(appInfo)) {
            return null
        }
        return PolicyEntry(
            item = policy,
            packageName = packageName,
            isSharedUid = sharedUserId != null,
            applicationInfo = appInfo,
            appName = loadConfig.resolveAppName(appInfo),
        )
    }

    private fun scheduleRootRefreshIfNeeded() {
        if (!isWhitelistMode(loadedListMode) || rootRefreshJob?.isActive == true) return
        rootRefreshJob = viewModelScope.launch {
            try {
                repeat(loadConfig.rootRefreshMaxAttempts) { attempt ->
                    if (loadConfig.rootServiceConnected()) {
                        refreshPoliciesFromRoot()
                        return@launch
                    }
                    if (attempt < loadConfig.rootRefreshMaxAttempts - 1) {
                        loadConfig.delayMillis(loadConfig.rootRefreshIntervalMs)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Keep the current list if the background root refresh fails.
            }
        }
    }

    private suspend fun refreshPoliciesFromRoot() {
        if (!isWhitelistMode(currentSuperuserListMode())) {
            return
        }
        val loadedPolicies = fetchPolicies(Config.Value.SU_MODE_WHITELIST)
        if (loadedPolicies.source != InstalledItemSource.ROOT || loadedPolicies.policies.isEmpty()) {
            return
        }
        allPolicies = loadedPolicies.policies
        publishFilteredPolicies(errorMessage = null)
    }

    private fun publishFilteredPolicies(errorMessage: String? = _uiState.value.errorMessage) {
        val state = _uiState.value
        val query = state.query.trim()
        val base = if (state.showSystemApps) {
            allPolicies
        } else {
            allPolicies.filterNot { isSystemApp(it.applicationInfo) }
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
                revokeEntry(entry)
            }
        }
    }

    fun onRevokePressed(key: String) {
        val entry = findPolicyByKey(key) ?: return

        fun doRevoke() {
            viewModelScope.launch {
                revokeEntry(entry)
            }
        }

        if (Config.suAuth) {
            AuthEvent { doRevoke() }.publish()
        } else {
            showRevokeDialog(key)
        }
    }

    private suspend fun revokeEntry(entry: PolicyEntry) {
        db.delete(entry.item.uid)
        if (isWhitelistMode(loadedListMode)) {
            entry.item.policy = SuPolicy.QUERY
            entry.item.remain = -1
            entry.item.notification = true
            entry.item.logging = true
        } else {
            allPolicies = allPolicies.filterNot { it.item.uid == entry.item.uid }
        }
        publishFilteredPolicies()
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
                    revokeEntry(entry)
                } else {
                    entry.item.policy = policy
                    entry.item.remain = 0
                    db.update(entry.item)
                    publishFilteredPolicies()
                }

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
