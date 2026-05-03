package io.github.seyud.weave.ui.surequest

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.CountDownTimer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.R
import io.github.seyud.weave.core.data.magiskdb.PolicyDao
import io.github.seyud.weave.core.ktx.getLabel
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.model.su.SuPolicy.Companion.ALLOW
import io.github.seyud.weave.core.model.su.SuPolicy.Companion.DENY
import io.github.seyud.weave.core.su.SuRequestHandler
import io.github.seyud.weave.dialog.SuRequestDialog
import io.github.seyud.weave.arch.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 超级用户请求 ViewModel
 * 管理 su 请求弹窗的状态和业务逻辑
 *
 * @param policyDB 权限策略数据库访问对象
 * @param timeoutPrefs 超时时间 SharedPreferences
 */
class SuRequestViewModel(
    policyDB: PolicyDao,
    private val timeoutPrefs: SharedPreferences
) : BaseViewModel() {

    /**
     * ViewModel 工厂类
     * 用于创建带参数的 ViewModel 实例
     */
    class Factory(
        private val policyDB: PolicyDao,
        private val timeoutPrefs: SharedPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SuRequestViewModel::class.java)) {
                return SuRequestViewModel(policyDB, timeoutPrefs) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    /**
     * 弹窗状态流
     * 使用 StateFlow 驱动授权弹窗状态
     */
    private val _dialogState = MutableStateFlow(SuRequestDialog.DialogState())
    val dialogState: StateFlow<SuRequestDialog.DialogState> = _dialogState.asStateFlow()

    /**
     * 是否应该结束 Activity
     */
    private val _shouldFinish = MutableStateFlow(false)
    val shouldFinish: StateFlow<Boolean> = _shouldFinish.asStateFlow()

    /**
     * 应用图标
     */
    lateinit var icon: Drawable
        private set

    /**
     * 应用标题（名称）
     */
    lateinit var title: String
        private set

    /**
     * 包名
     */
    lateinit var packageName: String
        private set

    /**
     * 请求处理器
     */
    private val handler = SuRequestHandler(AppContext.packageManager, policyDB)

    /**
     * 倒计时总毫秒数
     */
    private val millis = TimeUnit.SECONDS.toMillis(Config.suDefaultTimeout.toLong())

    /**
     * 倒计时器
     */
    private var timer: CountDownTimer? = null

    /**
     * 是否已初始化完成
     */
    private var initialized = false

    /**
     * 点击劫持防护触摸监听器
     * 检测窗口是否被其他应用覆盖
     */
    val grantTouchListener = View.OnTouchListener { _: View, event: MotionEvent ->
        // 过滤被覆盖的触摸事件
        if (event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0
            || event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0) {
            if (event.action == MotionEvent.ACTION_UP) {
                AppContext.toast(R.string.touch_filtered_warning, Toast.LENGTH_SHORT)
            }
            return@OnTouchListener Config.suTapjack
        }
        false
    }

    /**
     * 取消倒计时
     * 在用户点击允许按钮时调用
     */
    fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    /**
     * 响应允许请求
     * 在身份验证成功后或无需验证时调用
     */
    fun respondGranted() {
        respond(ALLOW)
    }

    /**
     * 拒绝按钮点击处理
     */
    fun denyPressed() {
        respond(DENY)
    }

    /**
     * 超时选择器触摸处理
     * 取消倒计时防止自动拒绝
     */
    fun spinnerTouched(): Boolean {
        cancelTimer()
        return false
    }

    /**
     * 超时选择变化处理
     *
     * @param index 选中的超时选项索引
     */
    fun onTimeoutSelected(index: Int) {
        _dialogState.value = _dialogState.value.copy(selectedTimeoutIndex = index)
    }

    /**
     * 处理 su 请求
     * 在后台线程初始化请求，需要用户交互时显示弹窗
     *
     * @param intent 包含请求参数的 Intent
     */
    fun handleRequest(intent: Intent) {
        viewModelScope.launch(Dispatchers.Default) {
            if (handler.start(intent)) {
                withContext(Dispatchers.Main) {
                    showDialog()
                }
            } else {
                _shouldFinish.value = true
            }
        }
    }

    /**
     * 显示弹窗并初始化 UI 数据
     */
    private suspend fun showDialog() {
        val pm = handler.pm
        val info = handler.pkgInfo
        val app = info.applicationInfo

        // 在后台线程加载应用信息
        val appIcon: Drawable
        val appName: String
        val pkgName: String
        val isSharedUid: Boolean

        if (app == null) {
            // 请求不是来自应用进程，且 UID 是共享 UID
            // 无法确定请求来源
            appIcon = pm.defaultActivityIcon
            appName = "[${AppContext.getString(R.string.shared_uid)}] ${info.sharedUserId}"
            pkgName = info.sharedUserId.toString()
            isSharedUid = true
        } else {
            val prefix = if (info.sharedUserId == null) "" else "[${AppContext.getString(R.string.shared_uid)}] "
            appIcon = app.loadIcon(pm)
            appName = "$prefix${app.getLabel(pm)}"
            pkgName = info.packageName
            isSharedUid = info.sharedUserId != null
        }

        val savedIndex = timeoutPrefs.getInt(pkgName, 0)

        // 保存到成员变量
        icon = appIcon
        title = appName
        packageName = pkgName

        // 在主线程更新状态
        withContext(Dispatchers.Main) {
            _dialogState.value = SuRequestDialog.DialogState(
                visible = true,
                appIcon = appIcon,
                appName = appName,
                packageName = pkgName,
                isSharedUid = isSharedUid,
                selectedTimeoutIndex = savedIndex,
                grantEnabled = false,
                remainingSeconds = (millis / 1000).toInt()
            )

            // 启动倒计时
            startTimer()

            initialized = true
        }
    }

    /**
     * 响应请求
     * 将用户选择写入 FIFO 管道并更新数据库
     *
     * @param action 响应动作（ALLOW 或 DENY）
     */
    private fun respond(action: Int) {
        if (!initialized) {
            // 忽略弹窗完成前的响应
            return
        }

        cancelTimer()

        val pos = _dialogState.value.selectedTimeoutIndex
        timeoutPrefs.edit().putInt(packageName, pos).apply()

        viewModelScope.launch {
            handler.respond(action, Config.Value.TIMEOUT_LIST[pos])
            // 响应后结束 Activity
            _shouldFinish.value = true
        }
    }

    /**
     * 启动倒计时器
     * 默认 10 秒后自动拒绝
     */
    private fun startTimer() {
        timer = object : CountDownTimer(millis, 1000) {
            override fun onTick(remains: Long) {
                val remainingSecs = (remains / 1000).toInt() + 1
                val shouldEnableGrant = remains <= millis - 1000

                _dialogState.value = _dialogState.value.copy(
                    remainingSeconds = remainingSecs,
                    grantEnabled = shouldEnableGrant || _dialogState.value.grantEnabled
                )
            }

            override fun onFinish() {
                _dialogState.value = _dialogState.value.copy(remainingSeconds = 0)
                respond(DENY)
            }
        }.start()
    }

    /**
     * 生命周期清理
     * Activity 销毁时取消倒计时
     */
    fun onDestroy() {
        cancelTimer()
    }

    /**
     * 无障碍服务空委托
     * 对辅助服务隐藏 UI 内容，防止恶意辅助服务点击
     */
    object EmptyAccessibilityDelegate : View.AccessibilityDelegate() {
        override fun sendAccessibilityEvent(host: View, eventType: Int) {}
        override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?) = true
        override fun sendAccessibilityEventUnchecked(host: View, event: AccessibilityEvent) {}
        override fun dispatchPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) = true
        override fun onPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) {}
        override fun onInitializeAccessibilityEvent(host: View, event: AccessibilityEvent) {}
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {}
        override fun addExtraDataToAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo, extraDataKey: String, arguments: android.os.Bundle?) {}
        override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean = false
        override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProvider? = null
    }
}
