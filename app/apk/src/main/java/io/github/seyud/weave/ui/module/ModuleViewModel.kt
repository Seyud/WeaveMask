package io.github.seyud.weave.ui.module

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.arch.AsyncLoadViewModel
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.ui.module.state.ModulePreferencesRepository
import io.github.seyud.weave.ui.module.state.SortOptions
import io.github.seyud.weave.core.base.ContentResultCallback
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.model.module.LocalModule
import io.github.seyud.weave.core.model.module.OnlineModule
import io.github.seyud.weave.dialog.LocalModuleInstallDialog
import io.github.seyud.weave.dialog.OnlineModuleInstallDialog
import io.github.seyud.weave.events.GetContentEvent
import io.github.seyud.weave.utils.TextHolder
import io.github.seyud.weave.utils.asText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import io.github.seyud.weave.core.R as CoreR

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
    /**
     * 管理模式：长按底栏模块页图标切换。
     * 开启后已禁用模块显示「隐藏」按钮，被隐藏的模块暂时显示并带「再现」按钮
     */
    val managementMode: Boolean = false,
    val modules: List<ModuleInfo> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 模块页 ViewModel
 * 使用纯 Compose 状态管理驱动模块页状态
 */
class ModuleViewModel(
    private val prefsRepo: ModulePreferencesRepository,
) : AsyncLoadViewModel() {

    sealed interface ModuleEvent {
        data class ShowSnackbar(
            val message: TextHolder,
            val duration: SnackbarDuration = SnackbarDuration.Short,
        ) : ModuleEvent
    }

    private val _event = Channel<ModuleEvent>(Channel.BUFFERED)
    val event: Flow<ModuleEvent> = _event.receiveAsFlow()

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
     * 被隐藏的模块 ID 集合（持久化于偏好设置，仅影响 UI 显示）
     */
    private var hiddenModuleIds: Set<String> = emptySet()

    private fun currentModuleSnapshot(): Map<String, ModuleInfo> = allModules.associateBy { it.id }

    private fun List<ModuleInfo>.retainUpdateStateFrom(
        previousModules: Map<String, ModuleInfo>,
    ): List<ModuleInfo> = map { moduleInfo ->
        moduleInfo.retainUpdateStateFrom(previousModules[moduleInfo.id])
    }

    private fun ModuleInfo.retainUpdateStateFrom(previous: ModuleInfo?): ModuleInfo {
        if (previous == null || previous.versionCode != versionCode) {
            return this
        }
        return copy(
            updateInfo = previous.updateInfo,
            outdated = previous.outdated,
        )
    }

    /**
     * 设置搜索查询
     */
    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        publishFilteredModules()
    }

    /**
     * 从持久化存储恢复排序选项（每次进入页面无条件调用）
     */
    fun initializePreferences() {
        val options = prefsRepo.loadSortOptions()
        hiddenModuleIds = prefsRepo.loadHiddenModuleIds()
        _uiState.value = _uiState.value.copy(
            sortEnabledFirst = options.enabledFirst,
            sortUpdateFirst = options.updateFirst,
            sortExecutableFirst = options.executableFirst,
        )
        publishFilteredModules()
    }

    fun toggleSortEnabledFirst() {
        val newValue = !_uiState.value.sortEnabledFirst
        _uiState.value = _uiState.value.copy(sortEnabledFirst = newValue)
        persistAndRepublish()
    }

    fun toggleSortUpdateFirst() {
        val newValue = !_uiState.value.sortUpdateFirst
        _uiState.value = _uiState.value.copy(sortUpdateFirst = newValue)
        persistAndRepublish()
    }

    fun toggleSortExecutableFirst() {
        val newValue = !_uiState.value.sortExecutableFirst
        _uiState.value = _uiState.value.copy(sortExecutableFirst = newValue)
        persistAndRepublish()
    }

    private fun persistAndRepublish() {
        val s = _uiState.value
        prefsRepo.saveSortOptions(
            SortOptions(
                enabledFirst = s.sortEnabledFirst,
                updateFirst = s.sortUpdateFirst,
                executableFirst = s.sortExecutableFirst,
            )
        )
        publishFilteredModules()
    }

    /**
     * 切换管理模式（长按底栏模块页图标触发）
     * 开启后：已禁用模块显示「隐藏」按钮，被隐藏的模块暂时显示并带「再现」按钮；
     * 关闭后：被隐藏的模块重新藏起来
     */
    fun toggleManagementMode() {
        _uiState.value = _uiState.value.copy(managementMode = !_uiState.value.managementMode)
        publishFilteredModules(resort = false)
    }

    /**
     * 隐藏模块卡片（仅 UI 层，不改变模块启用/安装状态）
     */
    fun hideModule(moduleId: String) {
        if (moduleId in hiddenModuleIds) return
        hiddenModuleIds = hiddenModuleIds + moduleId
        prefsRepo.saveHiddenModuleIds(hiddenModuleIds)
        publishFilteredModules(resort = false)
    }

    /**
     * 再现被隐藏的模块卡片，恢复普通显示
     */
    fun revealModule(moduleId: String) {
        if (moduleId !in hiddenModuleIds) return
        hiddenModuleIds = hiddenModuleIds - moduleId
        prefsRepo.saveHiddenModuleIds(hiddenModuleIds)
        publishFilteredModules(resort = false)
    }

    /**
     * 刷新模块列表
     */
    fun refresh(resort: Boolean = true) {
        viewModelScope.launch {
            loadModules(isInitialLoad = false, resort = resort)
        }
    }

    override suspend fun doLoadWork() {
        loadModules(isInitialLoad = true)
    }

    override fun onNetworkChanged(network: Boolean) = startLoading()

    private suspend fun loadModules(isInitialLoad: Boolean, resort: Boolean = true) {
        updateInfoJob?.cancel()
        val previousModules = currentModuleSnapshot()

        // 冷启动时模块 VM 可能先于模块页 UI 被触发(如网络切换回调),
        // 那时排序偏好与隐藏列表尚未读入,首次发布会按默认偏好排序;
        // 等用户切到模块页 initializePreferences() 读入真实偏好后再次
        // 发布,列表就会当场重排——即"进模块页才突然排序"。这里先
        // 初始化,保证任何路径的首次发布都带正确偏好。
        initializePreferences()

        if (isInitialLoad) {
            loading = true
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        } else {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
        }

        try {
            hiddenModuleIds = prefsRepo.loadHiddenModuleIds()
            val moduleLoaded = Info.env.isActive && withContext(Dispatchers.IO) { LocalModule.loaded() }
            val installedModules = if (moduleLoaded) {
                withContext(Dispatchers.IO) { LocalModule.installed() }
            } else {
                emptyList()
            }
            val installed = if (moduleLoaded) {
                withContext(Dispatchers.Default) {
                    installedModules
                        .map { ModuleInfo.from(it) }
                        .retainUpdateStateFrom(previousModules)
                }
            } else {
                emptyList()
            }

            allModules = installed
            val snapshotVersion = ++moduleSnapshotVersion
            publishFilteredModules(errorMessage = null, resort = resort)

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

    private fun publishFilteredModules(errorMessage: String? = _uiState.value.errorMessage, resort: Boolean = true) {
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

        // 打上隐藏标记；非管理模式下过滤掉被隐藏的模块卡片
        modules = modules.map { it.copy(hidden = it.id in hiddenModuleIds) }
        if (!state.managementMode) {
            modules = modules.filterNot { it.hidden }
        }

        modules = if (resort || state.modules.isEmpty()) {
            modules.sortedWith(
                compareBy<ModuleInfo> {
                    if (state.sortEnabledFirst) !it.enabled else false
                }.thenBy {
                    if (state.sortUpdateFirst) !it.showUpdate else false
                }.thenBy {
                    if (state.sortExecutableFirst) it.executablePriority else 0
                }.thenBy { it.name.lowercase() }
            )
        } else {
            // Order-preserving reload: keep order, refresh data, drop removed, append new
            val byId = modules.associateBy { it.id }
            val existingIds = state.modules.mapTo(HashSet()) { it.id }
            state.modules.mapNotNull { byId[it.id] } + modules.filter { it.id !in existingIds }
        }

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
            val previousModules = currentModuleSnapshot()
            withContext(Dispatchers.IO) {
                val localModule = LocalModule.installed().find { it.id == moduleId }
                localModule?.let {
                    it.enable = enabled
                    val index = allModules.indexOfFirst { m -> m.id == moduleId }
                    if (index >= 0) {
                        allModules = allModules.toMutableList().apply {
                            this[index] = ModuleInfo.from(it)
                                .retainUpdateStateFrom(previousModules[moduleId])
                        }
                    }
                }
            }
            publishFilteredModules(resort = false)
        }
    }

    /**
     * 切换模块移除/恢复状态
     */
    fun toggleModuleRemove(moduleId: String) {
        viewModelScope.launch {
            val previousModules = currentModuleSnapshot()
            withContext(Dispatchers.IO) {
                val localModule = LocalModule.installed().find { it.id == moduleId }
                localModule?.let {
                    it.remove = !it.remove
                    val index = allModules.indexOfFirst { m -> m.id == moduleId }
                    if (index >= 0) {
                        allModules = allModules.toMutableList().apply {
                            this[index] = ModuleInfo.from(it)
                                .retainUpdateStateFrom(previousModules[moduleId])
                        }
                    }
                }
            }
            publishFilteredModules(resort = false)
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
            _event.trySend(ModuleEvent.ShowSnackbar(CoreR.string.no_connection.asText()))
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
