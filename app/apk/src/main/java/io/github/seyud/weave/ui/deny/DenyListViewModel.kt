package io.github.seyud.weave.ui.deny

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import io.github.seyud.weave.arch.AsyncLoadViewModel
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.ktx.concurrentMap
import io.github.seyud.weave.core.utils.InstalledItemLoadResult
import io.github.seyud.weave.core.utils.InstalledItemSource
import io.github.seyud.weave.core.utils.InstalledPackageLoader
import io.github.seyud.weave.core.utils.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DenyListViewModel : AsyncLoadViewModel() {

    private data class LoadedApps(
        val apps: List<DenyListAppInfo>,
        val source: InstalledItemSource,
        val shouldRefreshFromRoot: Boolean,
    )

    private var hasLoaded = false

    private val _loading = MutableStateFlow(false)
    private val _loadCompleted = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    val loadCompleted = _loadCompleted.asStateFlow()

    private val _query = MutableStateFlow("")
    private val _showSystem = MutableStateFlow(false)
    private val _showOS = MutableStateFlow(false)
    private val _allApps = MutableStateFlow<List<DenyListAppInfo>>(emptyList())
    private val _denyList = MutableStateFlow<List<CmdlineListItem>>(emptyList())
    private val _processCache = MutableStateFlow<Map<String, List<ProcessInfo>>>(emptyMap())
    private val _loadingPackages = MutableStateFlow<Set<String>>(emptySet())

    private val processJobs = mutableMapOf<String, Job>()
    private var delayedLoadingJob: Job? = null
    private var rootRefreshJob: Job? = null

    var query: String
        get() = _query.value
        set(value) {
            _query.value = value
        }

    var isShowSystem: Boolean
        get() = _showSystem.value
        set(value) {
            _showSystem.value = value
            if (!value && _showOS.value) {
                _showOS.value = false
            }
        }

    var isShowOS: Boolean
        get() = _showOS.value
        set(value) {
            _showOS.value = if (_showSystem.value) value else false
        }

    val showSystem = _showSystem.asStateFlow()
    val showOS = _showOS.asStateFlow()
    val searchQuery = _query.asStateFlow()

    private data class FilterState(
        val query: String,
        val showSystem: Boolean,
        val showOS: Boolean,
    )

    private data class CacheState(
        val denyList: List<CmdlineListItem>,
        val processCache: Map<String, List<ProcessInfo>>,
        val loadingPackages: Set<String>,
    )

    private val filterState = combine(_query, _showSystem, _showOS) { query, showSystem, showOS ->
        FilterState(query = query, showSystem = showSystem, showOS = showOS)
    }

    private val cacheState = combine(_denyList, _processCache, _loadingPackages) {
            denyList,
            processCache,
            loadingPackages,
        ->
        CacheState(
            denyList = denyList,
            processCache = processCache,
            loadingPackages = loadingPackages,
        )
    }

    val items = combine(_allApps, filterState, cacheState) { allApps, filters, caches ->
        allApps.mapNotNull { app ->
            val processes = caches.processCache[app.packageName].orEmpty()
            val toggleState = computeToggleState(app, processes, caches.denyList)
            val queryMatched = matchesQuery(app, processes, filters.query)
            val matchesFilters = toggleState != ToggleableState.Off || (
                (filters.showSystem || !app.isSystemApp) &&
                    (!filters.showOS || app.isApp)
            )
            if (!matchesFilters || !queryMatched) {
                null
            } else {
                DenyListAppUiModel(
                    info = app,
                    toggleState = toggleState,
                    processes = processes,
                    isLoadingProcesses = caches.loadingPackages.contains(app.packageName),
                    hasLoadedProcesses = caches.processCache.containsKey(app.packageName),
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        if (hasLoaded) return
        delayedLoadingJob?.cancel()
        delayedLoadingJob = viewModelScope.launch {
            delay(200L)
            _loading.value = true
        }
        try {
            val (denyList, loadedApps) = withContext(Dispatchers.IO) {
                val pm = AppContext.packageManager
                val denyEntries = Shell.cmd("magisk --denylist ls").exec().out.map(::CmdlineListItem)
                denyEntries to loadApps(pm, denyEntries)
            }
            val apps = loadedApps.apps
            _denyList.value = denyList
            _allApps.value = apps
            hasLoaded = true
            preloadSelectedProcesses(apps, denyList)
            scheduleRootRefreshIfNeeded(loadedApps.shouldRefreshFromRoot)
        } finally {
            val waitingForRootRefresh = _allApps.value.isEmpty() && rootRefreshJob?.isActive == true
            delayedLoadingJob?.cancel()
            delayedLoadingJob = null
            _loading.value = waitingForRootRefresh
            _loadCompleted.value = !waitingForRootRefresh
        }
    }

    fun loadProcesses(packageName: String) {
        if (_processCache.value.containsKey(packageName) || processJobs[packageName]?.isActive == true) {
            return
        }
        val appInfo = _allApps.value.firstOrNull { it.packageName == packageName } ?: return
        _loadingPackages.update { it + packageName }
        processJobs[packageName] = viewModelScope.launch {
            try {
                val processes = withContext(Dispatchers.IO) {
                    fetchProcesses(AppContext.packageManager, appInfo, _denyList.value)
                }
                _processCache.update { it + (packageName to processes) }
            } finally {
                _loadingPackages.update { it - packageName }
            }
        }
    }

    fun toggleApp(
        packageName: String,
        includeAllProcesses: Boolean,
        disableIndeterminate: Boolean = false,
    ) {
        val processes = _processCache.value[packageName]
        if (processes == null) {
            loadProcesses(packageName)
            val pendingJob = processJobs[packageName]
            viewModelScope.launch {
                pendingJob?.join()
                toggleApp(packageName, includeAllProcesses, disableIndeterminate)
            }
            return
        }
        val appInfo = _allApps.value.firstOrNull { it.packageName == packageName } ?: return
        when (computeToggleState(appInfo, processes, _denyList.value)) {
            ToggleableState.On -> disableApp(packageName, processes)
            ToggleableState.Off -> enableApp(packageName, processes, includeAllProcesses)
            ToggleableState.Indeterminate -> {
                if (disableIndeterminate) {
                    disableApp(packageName, processes)
                } else {
                    enableApp(packageName, processes, includeAllProcesses)
                }
            }
        }
    }

    private fun removeCommand(process: ProcessInfo): String {
        if (!process.isIsolated) return "magisk --denylist rm ${process.packageName} '${process.name}'"
        // Isolated processes are matched by name prefix. Older versions of the app added one
        // entry per isolated service (e.g. "com.foo:com.foo.Service") instead of the prefix
        // itself, so remove every entry starting with the prefix.
        return "magisk --denylist ls | while IFS='|' read -r pkg proc; do " +
            "[ \"\$pkg\" = $ISOLATED_MAGIC ] && case \"\$proc\" in '${process.name}'*) " +
            "magisk --denylist rm $ISOLATED_MAGIC \"\$proc\";; esac; done"
    }

    fun toggleProcess(packageName: String, process: ProcessInfo, enabled: Boolean) {
        if (process.isEnabled == enabled) return
        val cmd = if (enabled) {
            "magisk --denylist add ${process.packageName} '${process.name}'"
        } else {
            removeCommand(process)
        }
        Shell.cmd(cmd).submit()
        _processCache.update { cache ->
            cache + (packageName to cache[packageName].orEmpty().map {
                if (it.name == process.name && it.packageName == process.packageName) {
                    it.copy(isEnabled = enabled)
                } else {
                    it
                }
            })
        }
        _denyList.update { entries ->
            if (enabled) {
                if (entries.any { it.packageName == process.packageName && it.process == process.name }) {
                    entries
                } else {
                    entries + CmdlineListItem("${process.packageName}|${process.name}")
                }
            } else {
                entries.filterNot { it.packageName == process.packageName && it.process == process.name }
            }
        }
    }

    private fun enableApp(packageName: String, processes: List<ProcessInfo>, includeAllProcesses: Boolean) {
        val targets = processes.filterNot { it.isEnabled }.filter { includeAllProcesses || it.defaultSelection }
        if (targets.isEmpty()) return
        targets.forEach { process ->
            Shell.cmd("magisk --denylist add ${process.packageName} '${process.name}'").submit()
        }
        _processCache.update { cache ->
            cache + (packageName to cache[packageName].orEmpty().map { process ->
                if (targets.any { it.name == process.name && it.packageName == process.packageName }) {
                    process.copy(isEnabled = true)
                } else {
                    process
                }
            })
        }
        _denyList.update { entries ->
            val toAdd = targets.filterNot { process ->
                entries.any { it.packageName == process.packageName && it.process == process.name }
            }.map { CmdlineListItem("${it.packageName}|${it.name}") }
            entries + toAdd
        }
    }

    private fun disableApp(packageName: String, processes: List<ProcessInfo>) {
        Shell.cmd("magisk --denylist rm $packageName").submit()
        processes.filter { it.isIsolated && it.isEnabled }.forEach { process ->
            Shell.cmd(removeCommand(process)).submit()
        }
        _processCache.update { cache ->
            cache + (packageName to cache[packageName].orEmpty().map { it.copy(isEnabled = false) })
        }
        _denyList.update { entries ->
            entries.filterNot { entry ->
                entry.packageName == packageName ||
                    processes.any { it.isIsolated && it.name == entry.process && entry.packageName == it.packageName }
            }
        }
    }

    private fun computeToggleState(
        app: DenyListAppInfo,
        processes: List<ProcessInfo>,
        denyList: List<CmdlineListItem>,
    ): ToggleableState {
        if (processes.isNotEmpty()) {
            val enabledCount = processes.count { it.isEnabled }
            return when {
                enabledCount == 0 -> ToggleableState.Off
                enabledCount == processes.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
        }
        return if (hasPotentialEntryForApp(app, denyList)) {
            ToggleableState.Indeterminate
        } else {
            ToggleableState.Off
        }
    }

    private fun matchesQuery(app: DenyListAppInfo, processes: List<ProcessInfo>, query: String): Boolean {
        if (query.isBlank()) return true
        return app.label.contains(query, ignoreCase = true) ||
            app.packageName.contains(query, ignoreCase = true) ||
            processes.any { it.name.contains(query, ignoreCase = true) }
    }

    private fun preloadSelectedProcesses(
        apps: List<DenyListAppInfo>,
        denyList: List<CmdlineListItem>,
    ) {
        apps.asSequence()
            .filter { hasPotentialEntryForApp(it, denyList) }
            .forEach { loadProcesses(it.packageName) }
    }

    private fun hasPotentialEntryForApp(
        app: DenyListAppInfo,
        denyList: List<CmdlineListItem>,
    ): Boolean {
        val packageName = app.packageName
        val defaultProcess = app.applicationInfo.processName ?: packageName
        return denyList.any { entry ->
            entry.packageName == packageName ||
                entry.process == packageName ||
                entry.process == defaultProcess ||
                entry.process == "${packageName}_zygote" ||
                entry.process == "${defaultProcess}_zygote" ||
                entry.process.startsWith("$packageName:") ||
                entry.process.startsWith("$defaultProcess:")
        }
    }

    @SuppressLint("InlinedApi")
    private fun scheduleRootRefreshIfNeeded(shouldRefreshFromRoot: Boolean) {
        if (!shouldRefreshFromRoot || rootRefreshJob?.isActive == true) return
        rootRefreshJob = viewModelScope.launch {
            try {
                repeat(ROOT_REFRESH_MAX_ATTEMPTS) { attempt ->
                    if (RootUtils.isServiceConnected()) {
                        refreshAppsFromRoot()
                        return@launch
                    }
                    if (attempt < ROOT_REFRESH_MAX_ATTEMPTS - 1) {
                        delay(ROOT_REFRESH_INTERVAL_MS)
                    }
                }
            } finally {
                _loading.value = false
                _loadCompleted.value = true
            }
        }
    }

    @SuppressLint("InlinedApi")
    private suspend fun refreshAppsFromRoot() {
        val loadedApps = withContext(Dispatchers.IO) {
            loadApps(AppContext.packageManager, _denyList.value)
        }
        if (loadedApps.source != InstalledItemSource.ROOT || loadedApps.apps.isEmpty()) {
            return
        }
        _allApps.value = loadedApps.apps
        preloadSelectedProcesses(loadedApps.apps, _denyList.value)
    }

    @SuppressLint("InlinedApi")
    private suspend fun loadApps(
        pm: PackageManager,
        denyEntries: List<CmdlineListItem>,
    ): LoadedApps {
        val loadResult: InstalledItemLoadResult<android.content.pm.ApplicationInfo> =
            InstalledPackageLoader.loadApplications(MATCH_UNINSTALLED_PACKAGES, pm)
        val collected = loadResult.items.run {
            asFlow()
                .filter { AppContext.packageName != it.packageName }
                .concurrentMap { buildDenyListAppInfo(it, pm, denyEntries) }
                .toCollection(ArrayList(size))
        }
        collected.sort()
        return LoadedApps(
            apps = collected,
            source = loadResult.source,
            shouldRefreshFromRoot = loadResult.shouldRefreshFromRoot,
        )
    }

    private companion object {
        const val ROOT_REFRESH_INTERVAL_MS = 350L
        const val ROOT_REFRESH_MAX_ATTEMPTS = 15
    }

}
