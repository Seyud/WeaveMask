package io.github.seyud.weave.ui.module

import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.core.ktx.reboot
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 重启列表弹出按钮组件
 * 显示一个关闭图标按钮，点击后弹出重启选项菜单
 *
 * @param modifier Modifier
 * @param alignment 弹出菜单对齐方式
 */
@Composable
fun RebootListPopup(
    modifier: Modifier = Modifier,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.TopEnd
) {
    val showTopPopup = remember { mutableStateOf(false) }
    val context = LocalContext.current

    IconButton(
        modifier = modifier,
        onClick = { showTopPopup.value = true },
        holdDownState = showTopPopup.value
    ) {
        Icon(
            imageVector = MiuixIcons.Close2,
            contentDescription = context.getString(CoreR.string.reboot),
            tint = colorScheme.onBackground
        )
    }

    OverlayListPopup(
        show = showTopPopup.value,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = alignment,
        onDismissRequest = {
            showTopPopup.value = false
        }
    ) {
        val pm = context.getSystemService<PowerManager>()

        @Suppress("DEPRECATION")
        val isRebootingUserspaceSupported =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true

        ListPopupColumn {
            val rebootOptions = mutableListOf(
                Pair(CoreR.string.reboot, ""),
                Pair(CoreR.string.reboot_recovery, "recovery"),
                Pair(CoreR.string.reboot_bootloader, "bootloader"),
                Pair(CoreR.string.reboot_download, "download"),
                Pair(CoreR.string.reboot_edl, "edl")
            )
            if (isRebootingUserspaceSupported) {
                rebootOptions.add(1, Pair(CoreR.string.reboot_userspace, "userspace"))
            }
            rebootOptions.forEachIndexed { idx, (id, reason) ->
                RebootDropdownItem(
                    id = id,
                    reason = reason,
                    showTopPopup = showTopPopup,
                    optionSize = rebootOptions.size,
                    index = idx
                )
            }
        }
    }
}

/**
 * 重启选项下拉项
 *
 * @param id 字符串资源ID
 * @param reason 重启原因参数
 * @param showTopPopup 控制弹出菜单显示状态的State
 * @param optionSize 选项总数
 * @param index 当前选项索引
 */
@Composable
private fun RebootDropdownItem(
    @StringRes id: Int,
    reason: String = "",
    showTopPopup: MutableState<Boolean>,
    optionSize: Int,
    index: Int,
) {
    val context = LocalContext.current
    DropdownImpl(
        text = context.getString(id),
        optionSize = optionSize,
        isSelected = false,
        onSelectedIndexChange = {
            reboot(reason)
            showTopPopup.value = false
        },
        index = index
    )
}
