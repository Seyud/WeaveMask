package io.github.seyud.weave.ui.surequest

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.R
import io.github.seyud.weave.core.base.ActivityExtension
import io.github.seyud.weave.core.base.IActivityExtension
import io.github.seyud.weave.core.base.UntrackedActivity
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.su.SuCallbackHandler
import io.github.seyud.weave.core.su.SuCallbackHandler.REQUEST
import io.github.seyud.weave.dialog.SuRequestDialog
import io.github.seyud.weave.ui.theme.WeaveMagiskTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 超级用户请求 Activity
 * 使用 Compose + Miuix OverlayDialog 实现 su 请求弹窗
 *
 * 安全特性：
 * 1. 点击劫持防护 - 在 dispatchTouchEvent 中检测 FLAG_WINDOW_IS_OBSCURED
 * 2. 无障碍服务防护 - 使用 EmptyAccessibilityDelegate 对辅助服务隐藏 UI
 * 3. 生物识别认证 - 支持 suAuth 配置
 * 4. 倒计时自动拒绝 - 默认 10 秒超时
 */
class SuRequestActivity : ComponentActivity(), UntrackedActivity, IActivityExtension {

    /**
     * Activity 扩展实例
     * 用于支持权限请求、身份验证等功能
     * 必须在属性初始化时创建，以确保 registerForActivityResult 在正确的时机调用
     */
    override val extension = ActivityExtension(this)

    /**
     * ViewModel 实例
     */
    private val viewModel: SuRequestViewModel by viewModels {
        SuRequestViewModel.Factory(
            ServiceLocator.policyDB,
            AppContext.getSharedPreferences("su_request", MODE_PRIVATE)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 配置窗口特性（必须在 super.onCreate 之前）
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        // 初始化 ActivityExtension（必须在 super.onCreate 之前）
        extension.onCreate(savedInstanceState)

        super.onCreate(savedInstanceState)

        // 设置无障碍服务委托（点击劫持防护启用时）
        if (Config.suTapjack) {
            window.decorView.accessibilityDelegate = SuRequestViewModel.EmptyAccessibilityDelegate
        }

        // 设置 Compose 内容
        setContent {
            val colorMode = Config.colorMode
            val keyColorInt = Config.keyColor
            val keyColor = if (keyColorInt == 0) null else Color(keyColorInt)

            WeaveMagiskTheme(colorMode = colorMode, keyColor = keyColor) {
                val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()

                SuRequestDialog(
                    state = dialogState,
                    timeoutOptions = resources.getStringArray(R.array.allow_timeout),
                    onTimeoutSelected = viewModel::onTimeoutSelected,
                    onGrant = {
                        viewModel.cancelTimer()
                        if (Config.suAuth) {
                            withAuthentication { authenticated ->
                                if (authenticated) {
                                    viewModel.respondGranted()
                                }
                            }
                        } else {
                            viewModel.respondGranted()
                        }
                    },
                    onDeny = viewModel::denyPressed,
                    onDismiss = viewModel::denyPressed
                )
            }
        }

        // 处理 Intent
        handleIntent(intent)

        // 观察 shouldFinish 状态
        lifecycleScope.launch {
            viewModel.shouldFinish.collect { shouldFinish ->
                if (shouldFinish) {
                    finishAndRemoveTask()
                }
            }
        }

        // 生命周期观察
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                viewModel.onDestroy()
            }
        })
    }

    /**
     * 处理 Intent
     * 根据 action 类型处理 su 请求或回调
     *
     * @param intent 传入的 Intent
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val action = intent.getStringExtra("action")
            if (action == REQUEST) {
                viewModel.handleRequest(intent)
            } else {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        SuCallbackHandler.run(this@SuRequestActivity, action, intent.extras)
                    }
                    finishAndRemoveTask()
                }
            }
        } else {
            finishAndRemoveTask()
        }
    }

    /**
     * 触摸事件分发
     * 实现点击劫持防护：检测窗口是否被其他应用覆盖
     *
     * @param event 触摸事件
     * @return 是否消费事件
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 点击劫持防护
        if (event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0
            || event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0) {
            if (Config.suTapjack) {
                // 窗口被覆盖，消费事件并提示用户
                if (event.action == MotionEvent.ACTION_UP) {
                    android.widget.Toast.makeText(this, R.string.touch_filtered_warning, android.widget.Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * 返回键处理
     * 触发拒绝操作
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        viewModel.denyPressed()
    }

    /**
     * 新 Intent 处理
     * 用于处理新的 su 请求
     *
     * @param intent 新的 Intent
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * 保存实例状态
     * 保存 ActivityExtension 的状态以便恢复
     *
     * @param outState 状态 Bundle
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        extension.onSaveInstanceState(outState)
    }
}
