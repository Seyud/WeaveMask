package com.topjohnwu.magisk.ui.module

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.base.ContentResultCallback
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.core.model.module.OnlineModule
import com.topjohnwu.magisk.dialog.LocalModuleInstallDialog
import com.topjohnwu.magisk.dialog.OnlineModuleInstallDialog
import com.topjohnwu.magisk.events.GetContentEvent
import com.topjohnwu.magisk.events.SnackbarEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import com.topjohnwu.magisk.core.R as CoreR

/**
 * 模块排序模式
 */
enum class ModuleSortMode {
    NAME,
    ENABLED_FIRST,
    UPDATE_FIRST
}

/**
 * 模块页 UI 状态
 * 使用 data class 符合 Compose 单向数据流原则
 */
data class ModuleUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val query: String = "",
    val sortMode: ModuleSortMode = ModuleSortMode.NAME,
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

    private var allModules: List<ModuleInfo> = emptyList()

    /**
     * 设置搜索查询
     */
    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        publishFilteredModules()
    }

    /**
     * 设置排序模式
     */
    fun setSortMode(sortMode: ModuleSortMode) {
        _uiState.value = _uiState.value.copy(sortMode = sortMode)
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
        if (isInitialLoad) {
            loading = true
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        } else {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
        }

        try {
            val moduleLoaded = Info.env.isActive && withContext(Dispatchers.IO) { LocalModule.loaded() }
            val installed = if (moduleLoaded) {
                withContext(Dispatchers.Default) {
                    LocalModule.installed().map { ModuleInfo.from(it) }
                }
            } else {
                emptyList()
            }

            allModules = installed
            publishFilteredModules(errorMessage = null)

            if (moduleLoaded) {
                loadUpdateInfo()
                publishFilteredModules(errorMessage = null)
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

    private suspend fun loadUpdateInfo() {
        withContext(Dispatchers.IO) {
            allModules.forEach { moduleInfo ->
                val localModule = LocalModule.installed().find { it.id == moduleInfo.id }
                localModule?.let {
                    if (it.fetch()) {
                        val index = allModules.indexOf(moduleInfo)
                        if (index >= 0) {
                            allModules = allModules.toMutableList().apply {
                                this[index] = ModuleInfo.from(it)
                            }
                        }
                    }
                }
            }
        }
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

        modules = when (state.sortMode) {
            ModuleSortMode.NAME -> modules.sortedBy { it.name.lowercase() }
            ModuleSortMode.ENABLED_FIRST -> modules.sortedWith(
                compareByDescending<ModuleInfo> { it.enabled }
                    .thenBy { it.name.lowercase() }
            )
            ModuleSortMode.UPDATE_FIRST -> modules.sortedWith(
                compareByDescending<ModuleInfo> { it.showUpdate }
                    .thenBy { it.name.lowercase() }
            )
        }

        _uiState.value = _uiState.value.copy(
            modules = modules,
            errorMessage = errorMessage
        )
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

    fun downloadPressed(item: OnlineModule?) =
        if (item != null && Info.isConnected.value == true) {
            withExternalRW { OnlineModuleInstallDialog(item).show() }
        } else {
            SnackbarEvent(CoreR.string.no_connection).publish()
        }

    fun installPressed() = withExternalRW {
        GetContentEvent("application/zip", UriCallback()).publish()
    }

    fun requestInstallLocalModule(uri: Uri, displayName: String) {
        LocalModuleInstallDialog(this, uri, displayName).show()
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

    companion object {
        private val uri = MutableLiveData<Uri?>()
    }
}
