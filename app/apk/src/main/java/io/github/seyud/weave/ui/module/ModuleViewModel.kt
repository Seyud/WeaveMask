package io.github.seyud.weave.ui.module

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.arch.AsyncLoadViewModel
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.base.ContentResultCallback
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.model.module.LocalModule
import io.github.seyud.weave.core.model.module.OnlineModule
import io.github.seyud.weave.dialog.LocalModuleInstallDialog
import io.github.seyud.weave.dialog.OnlineModuleInstallDialog
import io.github.seyud.weave.events.GetContentEvent
import io.github.seyud.weave.events.SnackbarEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import io.github.seyud.weave.core.R as CoreR

import androidx.compose.runtime.Immutable

/**
 * 模块页 UI 状态
 * 使用 data class 符合 Compose 单向数据流原则
 */
@Immutable
data class ModuleUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val query: String = "",
    val sortEnabledFirst: Boolean = false,
    val sortUpdateFirst: Boolean = false,
    val sortExecutableFirst: Boolean = false,
    val modules: List<ModuleInfo> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 模块页 ViewModel
 * 使用纯 Compose 状态管理，移除 DataBinding
 */
class ModuleViewModel : AsyncLoadViewModel() {

    val data get() = uri

    var loading by mutableStateOf(true)
        private set

    private val _uiState = mutableStateOf(ModuleUiState())
    val uiState: ModuleUiState get() = _uiState.value

    // 在线模块安装对话框状态
    var onlineInstallDialogState by mutableStateOf(OnlineModuleInstallDialog.DialogState())
        private set

    // 本地模块安装确认对话框状态
    var localInstallDialogState by mutableStateOf(LocalModuleInstallDialog.DialogState())
        private set

    private var changelogLoadJob: Job? = null
    private var updateInfoJob: Job? = null

    private var allModules: List<ModuleInfo> = emptyList()
    private var moduleSnapshotVersion = 0

    /**
     * 设置搜索查询
     */
    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        publishFilteredModules()
    }

    fun setSortEnabledFirst(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(sortEnabledFirst = enabled)
        publishFilteredModules()
    }

    fun setSortUpdateFirst(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(sortUpdateFirst = enabled)
        publishFilteredModules()
    }

    fun setSortExecutableFirst(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(sortExecutableFirst = enabled)
        publishFilteredModules()
    }

    fun restoreSortOptions(
        sortEnabledFirst: Boolean,
        sortUpdateFirst: Boolean,
        sortExecutableFirst: Boolean,
    ) {
        _uiState.value = _uiState.value.copy(
            sortEnabledFirst = sortEnabledFirst,
            sortUpdateFirst = sortUpdateFirst,
            sortExecutableFirst = sortExecutableFirst,
        )
        publishFilteredModules()
    }

    /**
     * 刷新模块列表
     */
    fun refresh() {
        viewModelScope.launch {
            loadModules(isInitialLoad = false)
        }
    }

    override suspend fun doLoadWork() {
        loadModules(isInitialLoad = true)
    }

    override fun onNetworkChanged(network: Boolean) = startLoading()

    private suspend fun loadModules(isInitialLoad: Boolean) {
        updateInfoJob?.cancel()

        if (isInitialLoad) {
            loading = true
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        } else {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
        }

        try {
            val moduleLoaded = Info.env.isActive && withContext(Dispatchers.IO) { LocalModule.loaded() }
            val installedModules = if (moduleLoaded) {
                withContext(Dispatchers.IO) { LocalModule.installed() }
            } else {
                emptyList()
            }
            val installed = if (moduleLoaded) {
                withContext(Dispatchers.Default) {
                    installedModules.map { ModuleInfo.from(it) }
                }
            } else {
                emptyList()
            }

            allModules = installed
            val snapshotVersion = ++moduleSnapshotVersion
            publishFilteredModules(errorMessage = null)

            if (moduleLoaded) {
                startUpdateInfoRefresh(installedModules, snapshotVersion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            allModules = emptyList()
            _uiState.value = _uiState.value.copy(
                modules = emptyList(),
                errorMessage = e.message
            )
        } finally {
            loading = false
            _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
        }
    }

    private fun startUpdateInfoRefresh(
        installedModules: List<LocalModule>,
        snapshotVersion: Int,
    ) {
        updateInfoJob = viewModelScope.launch {
            try {
                val updates = loadUpdateInfo(installedModules)
                if (snapshotVersion != moduleSnapshotVersion || updates.isEmpty()) {
                    return@launch
                }

                allModules = allModules.map { moduleInfo ->
                    updates[moduleInfo.id] ?: moduleInfo
                }
                publishFilteredModules()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.w(e, "Failed to refresh module update info")
            }
        }
    }

    private suspend fun loadUpdateInfo(installedModules: List<LocalModule>): Map<String, ModuleInfo> =
        supervisorScope {
            installedModules
                .map { localModule ->
                    async(Dispatchers.IO) {
                        if (localModule.fetch()) {
                            localModule.id to ModuleInfo.from(localModule)
                        } else {
                            null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }

    private fun publishFilteredModules(errorMessage: String? = _uiState.value.errorMessage) {
        val state = _uiState.value
        val query = state.query.trim()
        var modules = if (query.isEmpty()) {
            allModules
        } else {
            allModules.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true) ||
                    it.author.contains(query, ignoreCase = true)
            }
        }

        modules = modules.sortedWith(
            compareBy<ModuleInfo> {
                if (state.sortEnabledFirst) !it.enabled else false
            }.thenBy {
                if (state.sortUpdateFirst) !it.showUpdate else false
            }.thenBy {
                if (state.sortExecutableFirst) it.executablePriority else 0
            }.thenBy { it.name.lowercase() }
        )

        _uiState.value = _uiState.value.copy(
            modules = modules,
            errorMessage = errorMessage
        )
    }

    private val ModuleInfo.executablePriority: Int
        get() = when {
            hasAction && hasWebUi -> 0
            hasWebUi -> 1
            hasAction -> 2
            else -> 3
        }

    /**
     * 切换模块启用/禁用状态
     */
    fun toggleModule(moduleId: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val localModule = LocalModule.installed().find { it.id == moduleId }
                localModule?.let {
                    it.enable = enabled
                    val index = allModules.indexOfFirst { m -> m.id == moduleId }
                    if (index >= 0) {
                        allModules = allModules.toMutableList().apply {
                            this[index] = ModuleInfo.from(it)
                        }
                    }
                }
            }
            publishFilteredModules()
        }
    }

    /**
     * 切换模块移除/恢复状态
     */
    fun toggleModuleRemove(moduleId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val localModule = LocalModule.installed().find { it.id == moduleId }
                localModule?.let {
                    it.remove = !it.remove
                    val index = allModules.indexOfFirst { m -> m.id == moduleId }
                    if (index >= 0) {
                        allModules = allModules.toMutableList().apply {
                            this[index] = ModuleInfo.from(it)
                        }
                    }
                }
            }
            publishFilteredModules()
        }
    }

    fun downloadPressed(item: OnlineModule?) {
        if (item != null && Info.isConnected.value == true) {
            if (onlineInstallDialogState.visible && onlineInstallDialogState.module?.id == item.id) {
                return
            }
            changelogLoadJob?.cancel()
            onlineInstallDialogState = OnlineModuleInstallDialog.DialogState(
                visible = true,
                module = item,
                changelog = null,
                isLoadingChangelog = true
            )
            changelogLoadJob = viewModelScope.launch {
                loadChangelog(item)
            }
        } else {
            SnackbarEvent(CoreR.string.no_connection).publish()
        }
    }

    private suspend fun loadChangelog(item: OnlineModule) {
        try {
            val text = withContext(Dispatchers.IO) {
                ServiceLocator.networkService.fetchString(item.changelog)
            }
            if (!onlineInstallDialogState.visible || onlineInstallDialogState.module?.id != item.id) {
                return
            }
            val changelog = if (text.length > 1000) text.substring(0, 1000) else text
            onlineInstallDialogState = onlineInstallDialogState.copy(
                changelog = changelog,
                isLoadingChangelog = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            if (!onlineInstallDialogState.visible || onlineInstallDialogState.module?.id != item.id) {
                return
            }
            onlineInstallDialogState = onlineInstallDialogState.copy(
                isLoadingChangelog = false,
                errorMessage = e.message ?: "Failed to load changelog"
            )
        }
    }

    fun dismissOnlineInstallDialog() {
        changelogLoadJob?.cancel()
        changelogLoadJob = null
        onlineInstallDialogState = OnlineModuleInstallDialog.DialogState()
    }

    fun dismissLocalInstallDialog() {
        localInstallDialogState = LocalModuleInstallDialog.DialogState()
    }

    fun installPressed() = withExternalRW {
        GetContentEvent("application/zip", UriCallback()).publish()
    }

    fun requestInstallLocalModule(modules: List<ModuleInstallTarget>) {
        localInstallDialogState = LocalModuleInstallDialog.DialogState(
            visible = true,
            modules = modules
        )
    }

    @Parcelize
    class UriCallback : ContentResultCallback {
        override fun onActivityResult(result: Uri) {
            uri.value = result
        }
    }

    /**
     * 运行模块操作的回调
     * 由 UI 层设置，用于处理导航到操作页面
     */
    var onRunAction: ((String, String) -> Unit)? = null

    /**
     * 运行模块操作
     * 通过回调通知 UI 层进行导航
     *
     * @param id 模块 ID
     * @param name 模块名称
     */
    fun runAction(id: String, name: String) {
        onRunAction?.invoke(id, name)
    }

    override fun onCleared() {
        changelogLoadJob?.cancel()
        changelogLoadJob = null
        super.onCleared()
    }

    companion object {
        private val uri = MutableLiveData<Uri?>()
    }
}
