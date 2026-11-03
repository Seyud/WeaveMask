package io.github.seyud.weave.ui.module

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ShortcutType {
    Action,
    WebUI,
}

@Stable
class ModuleShortcutState internal constructor(
    moduleId: String?,
    moduleDir: String?,
    name: String,
    iconUri: String?,
    selectedTypeName: String?,
    supportsActionShortcut: Boolean,
    supportsWebUiShortcut: Boolean,
    defaultActionIconUri: String?,
    defaultWebUiIconUri: String?,
) {
    var moduleId by mutableStateOf(moduleId)
        private set

    /** 模块目录名：action 快捷方式按它定位模块（id 可能重复/与目录名不同）。 */
    var moduleDir by mutableStateOf(moduleDir)
        private set

    var name by mutableStateOf(name)

    var iconUri by mutableStateOf(iconUri)
        private set

    var selectedType by mutableStateOf(selectedTypeName?.let(ShortcutType::valueOf))
        private set

    var supportsActionShortcut by mutableStateOf(supportsActionShortcut)
        private set

    var supportsWebUiShortcut by mutableStateOf(supportsWebUiShortcut)
        private set

    var defaultActionIconUri by mutableStateOf(defaultActionIconUri)
        private set

    var defaultWebUiIconUri by mutableStateOf(defaultWebUiIconUri)
        private set

    var previewIcon by mutableStateOf<ImageBitmap?>(null)
        internal set

    var hasExistingShortcut by mutableStateOf(false)
        internal set

    val availableTypes: List<ShortcutType>
        get() = buildList {
            if (supportsActionShortcut) {
                add(ShortcutType.Action)
            }
            if (supportsWebUiShortcut) {
                add(ShortcutType.WebUI)
            }
        }

    val defaultShortcutIconUri: String?
        get() = selectedType?.let(::defaultIconFor)

    fun bindModule(module: ModuleInfo) {
        moduleId = module.id
        moduleDir = module.dirName
        name = module.name
        iconUri = null
        selectedType = null
        supportsActionShortcut = module.supportsActionShortcut
        supportsWebUiShortcut = module.supportsWebUiShortcut
        defaultActionIconUri = module.actionIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::buildRootIconUri)
        defaultWebUiIconUri = module.webUiIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::buildRootIconUri)
    }

    fun selectType(type: ShortcutType) {
        selectedType = type
        iconUri = defaultIconFor(type)
    }

    fun updateName(value: String) {
        name = value
    }

    fun updateIconUri(value: String?) {
        iconUri = value
    }

    fun resetIconToDefault() {
        iconUri = defaultShortcutIconUri
    }

    suspend fun createShortcut(context: Context): Boolean {
        val currentModuleId = moduleId
        val type = selectedType
        if (currentModuleId.isNullOrBlank() || name.isBlank() || type == null) {
            return false
        }

        val created = createModuleShortcut(
            context = context,
            moduleId = currentModuleId,
            moduleDir = moduleDir ?: currentModuleId,
            name = name,
            iconUri = iconUri,
            type = type,
        )
        if (created) {
            hasExistingShortcut = true
        }
        return created
    }

    fun deleteShortcut(context: Context) {
        val currentModuleId = moduleId
        val type = selectedType
        if (currentModuleId.isNullOrBlank() || type == null) {
            return
        }

        deleteModuleShortcut(context, currentModuleId, type)
        hasExistingShortcut = false
    }

    private fun defaultIconFor(type: ShortcutType): String? {
        return when (type) {
            ShortcutType.Action -> defaultActionIconUri ?: defaultWebUiIconUri
            ShortcutType.WebUI -> defaultWebUiIconUri ?: defaultActionIconUri
        }
    }

    companion object {
        val Saver = listSaver<ModuleShortcutState, Any?>(
            save = {
                listOf(
                    it.moduleId,
                    it.moduleDir,
                    it.name,
                    it.iconUri,
                    it.selectedType?.name,
                    it.supportsActionShortcut,
                    it.supportsWebUiShortcut,
                    it.defaultActionIconUri,
                    it.defaultWebUiIconUri,
                )
            },
            restore = {
                ModuleShortcutState(
                    moduleId = it[0] as String?,
                    moduleDir = it[1] as String?,
                    name = it[2] as String,
                    iconUri = it[3] as String?,
                    selectedTypeName = it[4] as String?,
                    supportsActionShortcut = it[5] as Boolean,
                    supportsWebUiShortcut = it[6] as Boolean,
                    defaultActionIconUri = it[7] as String?,
                    defaultWebUiIconUri = it[8] as String?,
                )
            }
        )
    }
}

@Composable
fun rememberModuleShortcutState(
    context: Context,
): ModuleShortcutState {
    val state = rememberSaveable(saver = ModuleShortcutState.Saver) {
        ModuleShortcutState(
            moduleId = null,
            moduleDir = null,
            name = "",
            iconUri = null,
            selectedTypeName = null,
            supportsActionShortcut = false,
            supportsWebUiShortcut = false,
            defaultActionIconUri = null,
            defaultWebUiIconUri = null,
        )
    }

    LaunchedEffect(state.iconUri) {
        val iconUri = state.iconUri
        if (iconUri.isNullOrBlank()) {
            state.previewIcon = null
            return@LaunchedEffect
        }

        val bitmap = withContext(Dispatchers.IO) {
            loadShortcutBitmap(context, iconUri)
        }
        state.previewIcon = bitmap?.asImageBitmap()
    }

    LaunchedEffect(state.moduleId, state.selectedType) {
        val moduleId = state.moduleId
        val selectedType = state.selectedType
        if (moduleId.isNullOrBlank() || selectedType == null) {
            state.hasExistingShortcut = false
            return@LaunchedEffect
        }

        state.hasExistingShortcut = withContext(Dispatchers.IO) {
            hasModuleShortcut(context, moduleId, selectedType)
        }
    }

    return remember(state) { state }
}
