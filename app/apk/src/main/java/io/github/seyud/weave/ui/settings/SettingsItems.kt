package io.github.seyud.weave.ui.settings

import android.content.res.Resources
import io.github.seyud.weave.core.Config

/**
 * 设置项工具对象
 * 提供设置项的辅助方法和对话框显示
 */

/**
 * 检查自定义更新通道是否启用
 */
object UpdateChannelUrl {
    fun isEnabled(): Boolean = Config.updateChannel == Config.Value.CUSTOM_CHANNEL

    fun getDescription(res: Resources): String? {
        return Config.customChannelUrl.takeIf { it.isNotEmpty() }
    }
}

