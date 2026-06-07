package io.github.seyud.weave.ui.modulerepo

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.model.module.RepoModuleSummary
import io.github.seyud.weave.core.repository.ModuleRepoRepository
import io.github.seyud.weave.core.repository.ModuleRepoRepositoryImpl
import kotlinx.coroutines.launch

@Immutable
data class ModuleRepoUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val query: String = "",
    val sortByName: Boolean = false,
    val modules: List<RepoModuleSummary> = emptyList(),
    val errorMessage: String? = null,
    val baseUrl: String = "",
)

class ModuleRepoViewModel(
    private val repository: ModuleRepoRepository = ModuleRepoRepositoryImpl(),
) : ViewModel() {

    var uiState by mutableStateOf(
        ModuleRepoUiState(
            sortByName = Config.moduleRepoSortByName,
            baseUrl = Config.moduleRepoBaseUrl,
        )
    )
        private set

    private var allModules: List<RepoModuleSummary> = emptyList()
    private var lastLoadedBaseUrl: String? = null

    fun ensureLoaded() {
        val currentBaseUrl = Config.moduleRepoBaseUrl
        if (lastLoadedBaseUrl != currentBaseUrl || (allModules.isEmpty() && uiState.isLoading)) {
            refresh(forceLoading = true)
        }
    }

    fun refresh(forceLoading: Boolean = false) {
        if (uiState.isRefreshing) return
        val baseUrl = Config.moduleRepoBaseUrl
        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = forceLoading || allModules.isEmpty(),
                isRefreshing = !forceLoading && allModules.isNotEmpty(),
                errorMessage = null,
                baseUrl = baseUrl,
            )

            repository.fetchModules(baseUrl).onSuccess { modules ->
                lastLoadedBaseUrl = baseUrl
                allModules = modules
                publishModules(errorMessage = null)
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = throwable.message ?: "Failed to load module repository",
                    baseUrl = baseUrl,
                )
            }
        }
    }

    fun setQuery(query: String) {
        uiState = uiState.copy(query = query)
        publishModules()
    }

    fun toggleSortByName() {
        val newValue = !uiState.sortByName
        Config.moduleRepoSortByName = newValue
        uiState = uiState.copy(sortByName = newValue)
        publishModules()
    }

    private fun publishModules(errorMessage: String? = uiState.errorMessage) {
        val query = uiState.query.trim()
        var modules = if (query.isEmpty()) {
            allModules
        } else {
            allModules.filter { module ->
                module.moduleId.contains(query, ignoreCase = true) ||
                    module.displayName.contains(query, ignoreCase = true) ||
                    module.authorsText.contains(query, ignoreCase = true) ||
                    module.summary.contains(query, ignoreCase = true)
            }
        }

        modules = if (uiState.sortByName) {
            modules.sortedBy { it.displayName.lowercase() }
        } else {
            modules.sortedWith(
                compareByDescending<RepoModuleSummary> { it.latestRelease?.time.orEmpty() }
                    .thenBy { it.displayName.lowercase() }
            )
        }

        uiState = uiState.copy(
            isLoading = false,
            isRefreshing = false,
            modules = modules,
            errorMessage = errorMessage,
            baseUrl = Config.moduleRepoBaseUrl,
        )
    }
}
