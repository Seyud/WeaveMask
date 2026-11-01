package io.github.seyud.weave.ui.modulerepo

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.core.model.module.RepoModuleDetail
import io.github.seyud.weave.core.registry.ModuleNotInIndexException
import io.github.seyud.weave.core.repository.ModuleRepoRepository
import io.github.seyud.weave.core.repository.ModuleRepoRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ModuleRepoDetailUiState(
    val moduleId: String = "",
    val detail: RepoModuleDetail? = null,
    /** At least one load attempt finished (successfully or not). */
    val loaded: Boolean = false,
    /** The module is not in the current index (e.g. suspended): no install entry is offered. */
    val notInIndex: Boolean = false,
    val loadFailed: Boolean = false,
    /** Only set for modules; scripts are never installed. */
    val installedVersionCode: Int? = null,
) {
    /** One-shot script: releases are run, not installed. */
    val isScript: Boolean get() = detail?.module?.isScript == true

    val canInstall: Boolean get() = detail != null && !notInIndex
}

/** Loads the registry detail (and README) of one module for the detail screen. */
class ModuleRepoDetailViewModel(
    private val repository: ModuleRepoRepository = ModuleRepoRepositoryImpl(),
) : ViewModel() {

    var uiState by mutableStateOf(ModuleRepoDetailUiState())
        private set

    private var job: Job? = null

    /** Loads [moduleId] unless it is already loaded; [force] reloads (retry / pull to refresh). */
    fun load(moduleId: String, force: Boolean = false) {
        val current = uiState
        if (!force && current.moduleId == moduleId && (current.loaded || job?.isActive == true)) return
        // Show whatever was loaded before immediately, then refresh in the background.
        val cached = if (current.detail?.module?.moduleId == moduleId) current.detail else null
        uiState = if (current.moduleId == moduleId) {
            current.copy(loaded = cached != null, loadFailed = false, notInIndex = false)
        } else {
            ModuleRepoDetailUiState(moduleId = moduleId, detail = cached)
        }
        job?.cancel()
        job = viewModelScope.launch {
            val result = repository.fetchDetail(moduleId)
            withContext(Dispatchers.Main) {
                result.onSuccess { detail ->
                    uiState = uiState.copy(
                        moduleId = moduleId,
                        detail = detail,
                        loaded = true,
                        notInIndex = false,
                        loadFailed = false,
                    )
                }.onFailure { throwable ->
                    uiState = if (throwable is ModuleNotInIndexException) {
                        uiState.copy(moduleId = moduleId, detail = null, notInIndex = true, loaded = true, loadFailed = false)
                    } else {
                        uiState.copy(moduleId = moduleId, loaded = true, loadFailed = uiState.detail == null)
                    }
                }
            }
        }
    }

    fun setInstalledVersionCode(versionCode: Int?) {
        if (uiState.installedVersionCode != versionCode) {
            uiState = uiState.copy(installedVersionCode = versionCode)
        }
    }
}
