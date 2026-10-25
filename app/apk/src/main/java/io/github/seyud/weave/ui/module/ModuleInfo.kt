package io.github.seyud.weave.ui.module

import androidx.compose.runtime.Immutable
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.model.module.LocalModule
import io.github.seyud.weave.core.model.module.OnlineModule
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.utils.TextHolder
import io.github.seyud.weave.utils.asText

/**
 * 模块信息数据类
 * 使用 @Immutable 注解标记为不可变，符合 Compose 最佳实践
 */
@Immutable
data class ModuleInfo(
    val id: String,
    /** 模块目录名；模块 action 以目录而非 module.prop 的 id 定位（两者可能不同） */
    val dirName: String,
    val name: String,
    val author: String,
    val version: String,
    val versionCode: Int,
    val description: String,
    val enabled: Boolean,
    val removed: Boolean,
    val updated: Boolean,
    val updateInfo: OnlineModule?,
    val isZygisk: Boolean,
    val isRiru: Boolean,
    val zygiskUnloaded: Boolean,
    val hasAction: Boolean,
    val hasWebUi: Boolean,
    val actionIconPath: String?,
    val webUiIconPath: String?,
    val outdated: Boolean,
    val banner: String = "",
    /**
     * 是否被用户在管理模式下隐藏（仅 UI 层过滤，不影响模块本身状态）
     */
    val hidden: Boolean = false,
) {
    /**
     * 是否显示警告信息
     */
    val showNotice: Boolean
        get() = zygiskUnloaded ||
            (Info.isZygiskEnabled && isRiru) ||
            (!Info.isZygiskEnabled && isZygisk)

    private val hidesActionByNotice: Boolean
        get() = zygiskUnloaded || (Info.isZygiskEnabled && isRiru)

    /**
     * 是否显示 Action 按钮
     */
    val showAction: Boolean
        get() = hasAction && !hidesActionByNotice

    /**
     * 是否显示 WebUI 按钮
     */
    val showWebUi: Boolean
        get() = hasWebUi && enabled && !removed

    val supportsActionShortcut: Boolean
        get() = showAction && enabled && !removed && !actionIconPath.isNullOrBlank()

    val supportsWebUiShortcut: Boolean
        get() = showWebUi && !webUiIconPath.isNullOrBlank()

    val showShortcutButton: Boolean
        get() = supportsActionShortcut || supportsWebUiShortcut

    /**
     * 警告文本
     */
    val noticeText: TextHolder
        get() = when {
            zygiskUnloaded -> CoreR.string.zygisk_module_unloaded.asText()
            isRiru -> CoreR.string.suspend_text_riru.asText(CoreR.string.zygisk.asText())
            else -> CoreR.string.suspend_text_zygisk.asText(CoreR.string.zygisk.asText())
        }

    /**
     * 是否显示更新按钮
     * 只有当有可用更新(outdated)且获取到了更新信息时才显示
     */
    val showUpdate: Boolean
        get() = outdated && updateInfo != null

    /**
     * 更新按钮是否可用
     */
    val updateReady: Boolean
        get() = updateInfo != null && !removed && enabled

    companion object {
        /**
         * 从 LocalModule 创建 ModuleInfo
         */
        fun from(localModule: LocalModule): ModuleInfo {
            return ModuleInfo(
                id = localModule.id,
                dirName = localModule.base.name,
                name = localModule.name,
                author = localModule.author,
                version = localModule.version,
                versionCode = localModule.versionCode,
                description = localModule.description,
                enabled = localModule.enable,
                removed = localModule.remove,
                updated = localModule.updated,
                updateInfo = localModule.updateInfo,
                isZygisk = localModule.isZygisk,
                isRiru = localModule.isRiru,
                zygiskUnloaded = localModule.zygiskUnloaded,
                hasAction = localModule.hasAction,
                hasWebUi = localModule.hasWebUi,
                actionIconPath = localModule.actionIconPath,
                webUiIconPath = localModule.webUiIconPath,
                outdated = localModule.outdated,
                banner = localModule.banner,
            )
        }
    }
}
