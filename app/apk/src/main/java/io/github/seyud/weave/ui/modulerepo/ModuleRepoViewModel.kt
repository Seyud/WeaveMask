package io.github.seyud.weave.ui.modulerepo

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.model.module.RepoModule
import io.github.seyud.weave.core.model.module.RepoSort
import io.github.seyud.weave.core.registry.UnsupportedSchemaException
import io.github.seyud.weave.core.repository.ModuleRepoRepository
import io.github.seyud.weave.core.repository.ModuleRepoRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why the list could not be (fully) loaded; the UI maps this to a string resource. */
enum class RepoLoadError {
    /** The repository speaks a newer protocol than this build understands. */
    UNSUPPORTED_SCHEMA,

    /** No network at all. */
    OFFLINE,

    /** Any other failure (HTTP, malformed documents, ...). */
    FAILED,
}

@Immutable
data class ModuleRepoUiState(
    val isRefreshing: Boolean = false,
    /** A list (possibly empty) has been loaded at least once. */
    val hasLoaded: Boolean = false,
    val sortOrder: RepoSort = RepoSort.UPDATED,
    val offline: Boolean = false,
    val modules: List<RepoModule> = emptyList(),
    val query: String = "",
    val searchResults: List<RepoModule> = emptyList(),
    val error: RepoLoadError? = null,
    /** The list shown is the last good cached copy. */
    val stale: Boolean = false,
    /** Installed module id -> versionCode, for the installed / update badges. */
    val installedVersions: Map<String, Int> = emptyMap(),
    /** Site root of the repository in use, shown in the "source" popup. */
    val baseUrl: String = "",
) {
    /** Rows to render: search hits while searching, the whole list otherwise. */
    val visibleModules: List<RepoModule>
        get() = if (query.isBlank()) modules else searchResults

    /** Loaded, but the repository lists nothing: show the empty state instead of the list. */
    val showEmptyState: Boolean
        get() = hasLoaded && modules.isEmpty() && !isRefreshing && error == null
}

class ModuleRepoViewModel(
    private val repository: ModuleRepoRepository = ModuleRepoRepositoryImpl(),
) : ViewModel() {

    var uiState by mutableStateOf(
        ModuleRepoUiState(
            sortOrder = Config.moduleRepoSortOrder,
            baseUrl = Config.moduleRepoBaseUrl,
            offline = isOffline(),
        )
    )
        private set

    private var loadedBaseUrl: String? = null

    fun ensureLoaded() {
        val baseUrl = Config.moduleRepoUrl
        if (loadedBaseUrl != baseUrl && !uiState.isRefreshing) {
            loadedBaseUrl = baseUrl
            refresh()
        }
    }

    fun refresh() {
        if (uiState.isRefreshing) return
        viewModelScope.launch {
            uiState = uiState.copy(
                isRefreshing = true,
                offline = isOffline(),
                baseUrl = Config.moduleRepoBaseUrl,
            )
            val result = repository.fetchModules()
            withContext(Dispatchers.Main) {
                result.onSuccess { list ->
                    uiState = uiState.copy(
                        isRefreshing = false,
                        hasLoaded = true,
                        modules = sortWith(list.modules, uiState.sortOrder),
                        stale = list.stale,
                        error = list.error?.let(::classify),
                        offline = isOffline(),
                        baseUrl = Config.moduleRepoBaseUrl,
                    )
                    rebuildSearchResults()
                }.onFailure { throwable ->
                    uiState = uiState.copy(
                        isRefreshing = false,
                        error = classify(throwable),
                        offline = isOffline(),
                    )
                }
            }
        }
    }

    fun setInstalledVersions(versions: Map<String, Int>) {
        if (uiState.installedVersions != versions) {
            uiState = uiState.copy(installedVersions = versions)
        }
    }

    fun setQuery(query: String) {
        if (uiState.query == query) return
        uiState = uiState.copy(query = query)
        rebuildSearchResults()
    }

    fun setSortOrder(order: RepoSort) {
        if (uiState.sortOrder == order) return
        Config.moduleRepoSortOrder = order
        uiState = uiState.copy(
            sortOrder = order,
            modules = sortWith(uiState.modules, order),
            searchResults = sortWith(uiState.searchResults, order),
        )
    }

    private fun rebuildSearchResults() {
        val query = uiState.query.trim()
        if (query.isEmpty()) {
            uiState = uiState.copy(searchResults = emptyList())
            return
        }
        val hits = uiState.modules.filter { module ->
            module.moduleId.contains(query, ignoreCase = true) ||
                module.displayName.contains(query, ignoreCase = true) ||
                module.authors.contains(query, ignoreCase = true) ||
                module.summary.contains(query, ignoreCase = true) ||
                module.tags.any { it.contains(query, ignoreCase = true) }
        }
        uiState = uiState.copy(searchResults = sortWith(hits, uiState.sortOrder))
    }

    private fun sortWith(modules: List<RepoModule>, order: RepoSort): List<RepoModule> = order.sort(modules)

    private fun classify(throwable: Throwable): RepoLoadError = when {
        throwable is UnsupportedSchemaException -> RepoLoadError.UNSUPPORTED_SCHEMA
        isOffline() -> RepoLoadError.OFFLINE
        else -> RepoLoadError.FAILED
    }

    private fun isOffline(): Boolean = Info.isConnected.value == false
}
