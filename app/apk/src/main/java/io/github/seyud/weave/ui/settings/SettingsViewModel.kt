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
import io.github.seyud.weave.events.SnackbarEvent
import io.github.seyud.weave.ui.superuser.SuperuserModeSyncCoordinator
import io.github.seyud.weave.ui.superuser.normalizeSuperuserListMode
import io.github.seyud.weave.ui.superuser.superuserModeUsesWhitelist
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.seyud.weave.core.R as CoreR

/**
 * 设置页面 ViewModel
 * 处理设置页面的业务逻辑和用户交互
 */
class SettingsViewModel internal constructor(
    private val superuserModeSync: SuperuserModeSyncCoordinator = SuperuserModeSyncCoordinator(),
    private val whitelistModeDenyListCoordinator: WhitelistModeDenyListCoordinator = WhitelistModeDenyListCoordinator(),
) : BaseViewModel() {

    private data class LocalDenyListSyncRequest(
        val serial: Int,
        val targetMode: Int,
        val fallbackMode: Int,
        val notifyOnFailure: Boolean,
    )

    private fun newLocalDenyListSyncRequest(
        targetMode: Int,
        fallbackMode: Int,
    ): LocalDenyListSyncRequest {
        val serial = Config.suListModeDenyListPendingSerial + 1
        return LocalDenyListSyncRequest(
            serial = serial,
            targetMode = targetMode,
            fallbackMode = fallbackMode,
            notifyOnFailure = true,
        )
    }

    private fun passiveLocalDenyListSyncRequest(targetMode: Int): LocalDenyListSyncRequest =
        LocalDenyListSyncRequest(
            serial = 0,
            targetMode = targetMode,
            fallbackMode = targetMode,
            notifyOnFailure = false,
        )

    private fun pendingLocalDenyListSyncRequest(): LocalDenyListSyncRequest? {
        if (!Config.suListModeDenyListPendingValid) {
            return null
        }
        return LocalDenyListSyncRequest(
            serial = Config.suListModeDenyListPendingSerial,
            targetMode = normalizeSuperuserListMode(Config.suListModeDenyListPendingTargetMode),
            fallbackMode = normalizeSuperuserListMode(Config.suListModeDenyListPendingFallbackMode),
            notifyOnFailure = true,
        )
    }

    private fun setPendingLocalDenyListSyncRequest(request: LocalDenyListSyncRequest?) {
        if (request == null) {
            Config.suListModeDenyListPendingTargetMode = Config.Value.SU_MODE_WHITELIST
            Config.suListModeDenyListPendingFallbackMode = Config.Value.SU_MODE_BLACKLIST
            Config.suListModeDenyListPendingSerial = 0
            Config.suListModeDenyListPendingValid = false
            return
        }
        Config.suListModeDenyListPendingSerial = request.serial
        Config.suListModeDenyListPendingTargetMode = request.targetMode
        Config.suListModeDenyListPendingFallbackMode = request.fallbackMode
        Config.suListModeDenyListPendingValid = true
    }

    constructor() : this(
        SuperuserModeSyncCoordinator(),
        WhitelistModeDenyListCoordinator(),
    )

    private val _superuserListMode = MutableStateFlow(normalizeSuperuserListMode(Config.suListMode))
    val superuserListMode: StateFlow<Int> = _superuserListMode.asStateFlow()
    private val localDenyListSyncRequests = Channel<LocalDenyListSyncRequest>(Channel.CONFLATED)

    /** 日志页面导航回调 */
    var onNavigateToLog: (() -> Unit)? = null

    /** DenyList 配置页面导航回调 */
    var onNavigateToDenyListConfig: (() -> Unit)? = null

    /** 超级用户模式切换完成后的联动回调 */
    var onSuperuserModeChanged: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            val pendingRequest = pendingLocalDenyListSyncRequest()
            val zygiskNextActive = superuserModeSync.isZygiskNextActive()
            if (pendingRequest != null) {
                if (!zygiskNextActive) {
                    if (normalizeSuperuserListMode(Config.suListMode) != pendingRequest.targetMode) {
                        Config.suListMode = pendingRequest.targetMode
                        _superuserListMode.value = pendingRequest.targetMode
                    }
                    localDenyListSyncRequests.trySend(pendingRequest)
                }
                return@launch
            }

            val currentMode = normalizeSuperuserListMode(Config.suListMode)
            if (shouldQueuePassiveWhitelistReconcile(
                    hasPendingLocalSync = false,
                    currentMode = currentMode,
                    zygiskNextActive = zygiskNextActive,
                )
            ) {
                localDenyListSyncRequests.trySend(passiveLocalDenyListSyncRequest(currentMode))
            }
        }

        viewModelScope.launch {
            for (request in localDenyListSyncRequests) {
                val denyListResult = if (superuserModeUsesWhitelist(request.targetMode)) {
                    whitelistModeDenyListCoordinator.applyWhitelistMode()
                } else {
                    whitelistModeDenyListCoordinator.restoreBlacklistMode()
                }

                Config.denyList = denyListResult.denyListEnabled
                if (pendingLocalDenyListSyncRequest() == request) {
                    if (!denyListResult.success &&
                        request.notifyOnFailure &&
                        normalizeSuperuserListMode(Config.suListMode) == request.targetMode &&
                        _superuserListMode.value == request.targetMode
                    ) {
                        Config.suListMode = request.fallbackMode
                        _superuserListMode.value = request.fallbackMode
                        onSuperuserModeChanged?.invoke()
                        SnackbarEvent("Superuser mode sync failed").publish()
                    }
                    setPendingLocalDenyListSyncRequest(null)
                }
            }
        }
    }

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
            _superuserListMode.value = normalizedMode
            onComplete(normalizedMode)
            return
        }

        viewModelScope.launch {
            val currentMode = normalizeSuperuserListMode(Config.suListMode)
            val result = superuserModeSync.applyMode(normalizedMode)
            if (!result.success) {
                _superuserListMode.value = currentMode
                onComplete(currentMode)
                return@launch
            }

            val localSyncRequest = newLocalDenyListSyncRequest(
                targetMode = result.appliedMode,
                fallbackMode = currentMode,
            )
            setPendingLocalDenyListSyncRequest(localSyncRequest)

            Config.suListMode = result.appliedMode
            _superuserListMode.value = result.appliedMode
            onSuperuserModeChanged?.invoke()
            onComplete(result.appliedMode)
            if (!result.zygiskNextActive) {
                localDenyListSyncRequests.trySend(localSyncRequest)
            }
        }
    }

    fun refreshSuperuserListMode(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val currentMode = normalizeSuperuserListMode(Config.suListMode)
            val resolvedMode = superuserModeSync.resolveMode(currentMode)
            if (resolvedMode != currentMode) {
                setPendingLocalDenyListSyncRequest(
                    newLocalDenyListSyncRequest(
                        targetMode = resolvedMode,
                        fallbackMode = currentMode,
                    ),
                )
                Config.suListMode = resolvedMode
            }
            _superuserListMode.value = resolvedMode
            onComplete(resolvedMode)
        }
    }

}
