package io.github.seyud.weave.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.arch.BaseViewModel
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.integration.AppIconManager
import io.github.seyud.weave.core.integration.AppIconVariant
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.tasks.AppMigration
import io.github.seyud.weave.core.utils.RootUtils
import io.github.seyud.weave.events.AddHomeIconEvent
import io.github.seyud.weave.events.AuthEvent
import io.github.seyud.weave.ui.superuser.SuperuserModeSyncCoordinator
import io.github.seyud.weave.ui.superuser.normalizeSuperuserListMode
import kotlinx.coroutines.launch
import io.github.seyud.weave.core.R as CoreR

/**
 * 设置页面 ViewModel
 * 处理设置页面的业务逻辑和用户交互
 */
class SettingsViewModel internal constructor(
    private val superuserModeSync: SuperuserModeSyncCoordinator = SuperuserModeSyncCoordinator(),
) : BaseViewModel() {

    constructor() : this(SuperuserModeSyncCoordinator())

    /** 日志页面导航回调 */
    var onNavigateToLog: (() -> Unit)? = null

    /** DenyList 配置页面导航回调 */
    var onNavigateToDenyListConfig: (() -> Unit)? = null

    /** 超级用户模式切换完成后的联动回调 */
    var onSuperuserModeChanged: (() -> Unit)? = null

    /**
     * 添加桌面快捷方式
     */
    fun addShortcut() {
        AddHomeIconEvent().publish()
    }

    fun updateAppIcon(context: Context, variant: AppIconVariant): Boolean {
        return AppIconManager.setVariant(context, variant)
    }

    /**
     * 创建 Systemless Hosts
     */
    fun createHosts() {
        viewModelScope.launch {
            RootUtils.addSystemlessHosts()
            AppContext.toast(CoreR.string.settings_hosts_toast, Toast.LENGTH_SHORT)
        }
    }

    /**
     * 导航到 DenyList 配置页面
     */
    fun navigateToDenyListConfig() {
        onNavigateToDenyListConfig?.invoke()
    }

    /**
     * 恢复应用
     */
    suspend fun restoreApp(context: Context): Boolean {
        return AppMigration.restoreApp(context)
    }

    /**
     * 隐藏应用
     * @param newName 新的应用名称
     */
    suspend fun hideApp(context: Context, newName: String): Boolean {
        return AppMigration.hideApp(context, newName)
    }

    /**
     * 执行生物认证
     * @param callback 认证结果回调
     */
    fun authenticate(callback: (Boolean) -> Unit) {
        AuthEvent { callback(true) }.publish()
    }

    /**
     * 切换设置项前进行生物认证
     * @param checked 目标状态
     * @param callback 认证结果回调
     */
    fun authenticateAndToggle(checked: Boolean, callback: (Boolean) -> Unit) {
        AuthEvent { callback(true) }.publish()
    }

    fun setSuperuserListMode(mode: Int, onComplete: (Int) -> Unit = {}) {
        val normalizedMode = normalizeSuperuserListMode(mode)
        if (normalizeSuperuserListMode(Config.suListMode) == normalizedMode) {
            onComplete(normalizedMode)
            return
        }

        viewModelScope.launch {
            val result = superuserModeSync.applyMode(normalizedMode)
            if (result.success) {
                Config.suListMode = result.appliedMode
                onSuperuserModeChanged?.invoke()
                onComplete(result.appliedMode)
            } else {
                onComplete(normalizeSuperuserListMode(Config.suListMode))
            }
        }
    }

    fun refreshSuperuserListMode(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val currentMode = normalizeSuperuserListMode(Config.suListMode)
            val resolvedMode = superuserModeSync.resolveMode(currentMode)
            if (resolvedMode != currentMode) {
                Config.suListMode = resolvedMode
            }
            onComplete(resolvedMode)
        }
    }

}
