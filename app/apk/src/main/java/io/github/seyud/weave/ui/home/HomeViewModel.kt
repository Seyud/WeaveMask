package io.github.seyud.weave.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import io.github.seyud.weave.arch.ActivityExecutor
import io.github.seyud.weave.arch.AsyncLoadViewModel
import io.github.seyud.weave.arch.ContextExecutor
import io.github.seyud.weave.arch.UiEvent
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.BuildConfig
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.download.Subject
import io.github.seyud.weave.core.download.Subject.App
import io.github.seyud.weave.core.model.UpdateInfo
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.repository.NetworkService
import io.github.seyud.weave.dialog.EnvFixDialog
import io.github.seyud.weave.dialog.UninstallDialog
import io.github.seyud.weave.utils.TextHolder
import io.github.seyud.weave.utils.asText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import kotlin.math.roundToInt
import io.github.seyud.weave.core.R as CoreR

class HomeViewModel(
    private val svc: NetworkService
) : AsyncLoadViewModel() {

    sealed interface HomeEvent {
        data class ShowSnackbar(
            val message: TextHolder,
            val duration: SnackbarDuration = SnackbarDuration.Short,
        ) : HomeEvent
    }

    private val _event = Channel<HomeEvent>(Channel.BUFFERED)
    val event: Flow<HomeEvent> = _event.receiveAsFlow()

    enum class State {
        LOADING, INVALID, OUTDATED, UP_TO_DATE
    }

    data class ManagerInstallDialogState(
        val visible: Boolean = false,
        val updateInfo: UpdateInfo? = null,
        val version: String = "",
        val releaseNotes: String = "",
        val installEnabled: Boolean = false,
    )

    /**
     * 通知卡片是否可见
     * 使用 Compose State 以便在状态变化时自动重组 UI
     */
    var isNoticeVisible by mutableStateOf(Config.safetyNotice)
        private set

    /**
     * EnvFixDialog 状态
     */
    var envFixDialogState by mutableStateOf(EnvFixDialog.DialogState())
        private set

    /**
     * 卸载对话框状态
     */
    var uninstallDialogState by mutableStateOf(UninstallDialog.DialogState())
        private set

    val magiskState
        get() = when {
            Info.isRooted && Info.env.isUnsupported -> State.OUTDATED
            !Info.env.isActive -> State.INVALID
            Info.env.versionCode < BuildConfig.APP_VERSION_CODE -> State.OUTDATED
            else -> State.UP_TO_DATE
        }

    /**
     * App 状态
     * 使用 Compose State 以便在状态变化时自动重组 UI
     */
    var appState by mutableStateOf(State.LOADING)
        private set

    /**
     * 首页最近一次成功获取的 APP 更新信息
     */
    var managerUpdateSnapshot by mutableStateOf(UpdateInfo())
        private set

    /**
     * App 安装说明弹窗状态
     */
    var managerInstallDialogState by mutableStateOf(ManagerInstallDialogState())
        private set

    val magiskInstalledVersion
        get() = Info.env.run {
            if (isActive)
                ("$versionString ($versionCode)" + if (isDebug) " (D)" else "").asText()
            else
                CoreR.string.not_available.asText()
        }

    /**
     * 远程版本号
     * 使用 Compose State 以便在状态变化时自动重组 UI
     */
    var managerRemoteVersion by mutableStateOf<TextHolder>(CoreR.string.loading.asText())
        private set

    val managerInstalledVersion
        get() = "${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})" +
            if (BuildConfig.DEBUG) " (D)" else ""

    /**
     * 应用包名
     */
    val managerPackageName
        get() = AppContext.packageName

    var stateManagerProgress by mutableIntStateOf(0)
        private set

    companion object {
        private var checkedEnv = false
    }

    private var lastNetworkState: Boolean? = null

    override suspend fun doLoadWork() {
        appState = State.LOADING
        managerUpdateSnapshot = UpdateInfo()
        Info.fetchUpdate(svc)?.takeIf { it.hasValidDownload }?.let { updateInfo ->
            managerUpdateSnapshot = updateInfo
            appState = when {
                BuildConfig.APP_VERSION_CODE < updateInfo.versionCode -> State.OUTDATED
                else -> State.UP_TO_DATE
            }
            managerRemoteVersion = buildManagerVersion(updateInfo).asText()
        } ?: run {
            appState = State.INVALID
            managerRemoteVersion = CoreR.string.not_available.asText()
        }
        ensureEnv()
    }

    override fun onNetworkChanged(network: Boolean) {
        val previous = lastNetworkState
        lastNetworkState = network
        if (previous != null && previous != network) {
            startLoading()
        }
    }

    fun onProgressUpdate(progress: Float, subject: Subject) {
        if (subject is App)
            stateManagerProgress = progress.times(100f).roundToInt()
    }

    fun onLinkPressed(link: String) = object : UiEvent(), ContextExecutor {
        override fun invoke(context: Context) {
            val intent = Intent(Intent.ACTION_VIEW, link.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                context.toast(CoreR.string.open_link_failed_toast, Toast.LENGTH_SHORT)
            }
        }
    }.publish()

    /**
     * 显示卸载对话框
     */
    fun showUninstallDialog() {
        uninstallDialogState = UninstallDialog.DialogState(visible = true)
    }

    /**
     * 关闭卸载对话框
     */
    fun dismissUninstallDialog() {
        uninstallDialogState = UninstallDialog.DialogState()
    }

    /**
     * 开始恢复镜像
     */
    fun startRestoreImg() {
        uninstallDialogState = uninstallDialogState.copy(isRestoring = true)
    }

    fun onDeletePressed() = showUninstallDialog()

    fun onManagerPressed() = when (appState) {
        State.LOADING -> _event.trySend(HomeEvent.ShowSnackbar(CoreR.string.loading.asText()))
        State.INVALID -> _event.trySend(HomeEvent.ShowSnackbar(CoreR.string.no_connection.asText()))
        else -> withExternalRW {
            withInstallPermission {
                val updateInfo = managerUpdateSnapshot.takeIf { it.hasValidDownload }
                if (updateInfo == null) {
                    _event.trySend(HomeEvent.ShowSnackbar(CoreR.string.no_connection.asText()))
                    return@withInstallPermission
                }
                managerInstallDialogState = ManagerInstallDialogState(
                    visible = true,
                    updateInfo = updateInfo,
                    version = buildManagerVersion(updateInfo),
                    releaseNotes = buildManagerReleaseNotes(updateInfo),
                    installEnabled = updateInfo.hasValidDownload
                )
            }
        }
    }

    fun dismissManagerInstallDialog() {
        managerInstallDialogState = ManagerInstallDialogState()
    }

    fun onMagiskPressed() = withExternalRW {
    }

    /**
     * 隐藏通知卡片
     * 将状态保存到配置并更新 UI 状态
     */
    fun hideNotice() {
        Config.safetyNotice = false
        isNoticeVisible = false
    }

    /**
     * 显示 EnvFixDialog
     * 根据 code 值判断是普通修复还是完整修复
     */
    fun showEnvFixDialog(code: Int) {
        val isFullFix = EnvFixDialog.isFullFixRequired(code)
        envFixDialogState = EnvFixDialog.DialogState(
            visible = true,
            state = if (isFullFix) EnvFixDialog.State.FULL_FIX else EnvFixDialog.State.NORMAL_FIX,
            code = code
        )
    }

    /**
     * 关闭 EnvFixDialog
     */
    fun dismissEnvFixDialog() {
        envFixDialogState = EnvFixDialog.DialogState()
    }

    /**
     * 切换到修复中状态
     */
    fun startFixing() {
        envFixDialogState = envFixDialogState.copy(state = EnvFixDialog.State.FIXING)
    }

    /**
     * 导航到安装页面（用于完整修复模式）
     */
    fun navigateToInstall() {
        dismissEnvFixDialog()
        onMagiskPressed()
    }

    private suspend fun ensureEnv() {
        if (magiskState == State.INVALID || checkedEnv) return
        val code = EnvFixDialog.checkEnv()
        if (code != 0) {
            showEnvFixDialog(code)
        }
        checkedEnv = true
    }

    val showTest = false
    fun onTestPressed() = object : UiEvent(), ActivityExecutor {
        override fun invoke(activity: ComponentActivity) {
            /* Entry point to trigger test events within the app */
        }
    }.publish()

    private fun buildManagerVersion(updateInfo: UpdateInfo): String {
        val isDebug = Config.updateChannel == Config.Value.DEBUG_CHANNEL
        return "${updateInfo.version} (${updateInfo.versionCode})" + if (isDebug) " (D)" else ""
    }

    private fun buildManagerReleaseNotes(updateInfo: UpdateInfo): String {
        return updateInfo.note.ifBlank {
            buildManagerVersion(updateInfo).ifBlank {
                AppContext.getString(CoreR.string.app_changelog)
            }
        }
    }
}
