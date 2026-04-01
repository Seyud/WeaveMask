package io.github.seyud.weave.ui.navigation3

import android.net.Uri
import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 类型安全的导航路由定义（Navigation3）
 * 每个目的地是一个 NavKey（data object/data class），可保存/恢复到 backStack
 */
sealed interface Route : NavKey, Parcelable {
    /** 主页面（包含底部导航栏 + HorizontalPager） */
    @Parcelize
    @Serializable
    data object Main : Route

    /** 安装页面 */
    @Parcelize
    @Serializable
    data object Install : Route

    /** 刷写页面 */
    @Parcelize
    @Serializable
    data class Flash(val action: String, val uriStrings: List<String> = emptyList()) : Route

    /** 日志页面 */
    @Parcelize
    @Serializable
    data object Log : Route

    /** 应用语言设置 */
    @Parcelize
    @Serializable
    data object AppLanguage : Route

    /** 拒绝列表 */
    @Parcelize
    @Serializable
    data object Deny : Route

    /** 模块操作页面 */
    @Parcelize
    @Serializable
    data class Action(
        val moduleId: String,
        val moduleName: String = "",
        val fromShortcut: Boolean = false,
    ) : Route
}
