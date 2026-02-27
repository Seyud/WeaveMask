package com.topjohnwu.magisk.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.databinding.Bindable
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.ActivityExecutor
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.arch.ContextExecutor
import com.topjohnwu.magisk.arch.ViewEvent
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.download.Subject
import com.topjohnwu.magisk.core.download.Subject.App
import com.topjohnwu.magisk.core.ktx.await
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.repository.NetworkService
import com.topjohnwu.magisk.databinding.bindExtra
import com.topjohnwu.magisk.databinding.set
import com.topjohnwu.magisk.dialog.EnvFixDialog
import com.topjohnwu.magisk.dialog.ManagerInstallDialog
import com.topjohnwu.magisk.dialog.UninstallDialog
import com.topjohnwu.magisk.events.SnackbarEvent
import com.topjohnwu.magisk.utils.TextHolder
import com.topjohnwu.magisk.utils.asText
import com.topjohnwu.superuser.Shell
import kotlin.math.roundToInt
import com.topjohnwu.magisk.core.R as CoreR

class HomeViewModel(
    private val svc: NetworkService
) : AsyncLoadViewModel() {

    enum class State {
        LOADING, INVALID, OUTDATED, UP_TO_DATE
    }



    val magiskTitleBarrierIds =
        intArrayOf(R.id.home_magisk_icon, R.id.home_magisk_title, R.id.home_magisk_button)
    val appTitleBarrierIds =
        intArrayOf(R.id.home_manager_icon, R.id.home_manager_title, R.id.home_manager_button)

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
        get() = BuildConfig.APP_PACKAGE_NAME

    @get:Bindable
    var stateManagerProgress = 0
        set(value) = set(value, field, { field = it }, BR.stateManagerProgress)

    val extraBindings = bindExtra {
        it.put(BR.viewModel, this)
    }

    companion object {
        private var checkedEnv = false
    }

    override suspend fun doLoadWork() {
        appState = State.LOADING
        Info.fetchUpdate(svc)?.apply {
            appState = when {
                BuildConfig.APP_VERSION_CODE < versionCode -> State.OUTDATED
                else -> State.UP_TO_DATE
            }

            val isDebug = Config.updateChannel == Config.Value.DEBUG_CHANNEL
            managerRemoteVersion =
                ("$version (${versionCode})" + if (isDebug) " (D)" else "").asText()
        } ?: run {
            appState = State.INVALID
            managerRemoteVersion = CoreR.string.not_available.asText()
        }
        ensureEnv()
    }

    override fun onNetworkChanged(network: Boolean) = startLoading()

    fun onProgressUpdate(progress: Float, subject: Subject) {
        if (subject is App)
            stateManagerProgress = progress.times(100f).roundToInt()
    }

    fun onLinkPressed(link: String) = object : ViewEvent(), ContextExecutor {
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
        State.LOADING -> SnackbarEvent(CoreR.string.loading).publish()
        State.INVALID -> SnackbarEvent(CoreR.string.no_connection).publish()
        else -> withExternalRW {
            withInstallPermission {
                ManagerInstallDialog().show()
            }
        }
    }

    fun onMagiskPressed() = withExternalRW {
        HomeFragmentDirections.actionHomeFragmentToInstallFragment().navigate()
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
    fun onTestPressed() = object : ViewEvent(), ActivityExecutor {
        override fun invoke(activity: AppCompatActivity) {
            /* Entry point to trigger test events within the app */
        }
    }.publish()
}
