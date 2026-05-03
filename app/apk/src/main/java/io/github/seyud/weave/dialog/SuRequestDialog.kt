package io.github.seyud.weave.dialog

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import io.github.seyud.weave.core.R as CoreR
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 超级用户请求弹窗状态管理
 */
object SuRequestDialog {

    /**
     * 弹窗状态数据类
     *
     * @param visible 弹窗是否可见
     * @param appIcon 应用图标
     * @param appName 应用名称
     * @param packageName 包名
     * @param isSharedUid 是否为共享 UID
     * @param selectedTimeoutIndex 选中的超时时间索引
     * @param grantEnabled 允许按钮是否可用（倒计时结束后启用）
     * @param remainingSeconds 倒计时剩余秒数
     * @param isAuthenticating 是否正在进行生物识别认证
     */
    data class DialogState(
        val visible: Boolean = false,
        val appIcon: Drawable? = null,
        val appName: String = "",
        val packageName: String = "",
        val isSharedUid: Boolean = false,
        val selectedTimeoutIndex: Int = 0,
        val grantEnabled: Boolean = false,
        val remainingSeconds: Int = 0,
        val isAuthenticating: Boolean = false
    )
}

/**
 * 超级用户请求弹窗 Compose 组件
 * 使用原生 Dialog + Miuix 样式实现透明背景
 *
 * @param state 弹窗状态
 * @param timeoutOptions 超时选项数组
 * @param onTimeoutSelected 超时选择回调
 * @param onGrant 允许回调
 * @param onDeny 拒绝回调
 * @param onDismiss 关闭回调
 */
@Composable
fun SuRequestDialog(
    state: SuRequestDialog.DialogState,
    timeoutOptions: Array<String>,
    onTimeoutSelected: (Int) -> Unit,
    onGrant: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.visible) return

    val context = LocalContext.current

    // 超时选择器状态
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember(state.selectedTimeoutIndex) {
        mutableIntStateOf(state.selectedTimeoutIndex)
    }

    // 使用原生 Dialog，禁用 dim 效果，实现透明背景
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        // 全屏透明背景容器
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            // 弹窗内容卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MiuixTheme.colorScheme.background)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = context.getString(CoreR.string.su_request_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 应用信息卡片
                SuRequestAppInfo(
                    icon = state.appIcon,
                    appName = state.appName,
                    packageName = state.packageName,
                    isSharedUid = state.isSharedUid
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 超时选择器
                SuRequestTimeoutSelector(
                    options = timeoutOptions,
                    selectedIndex = selectedIndex,
                    onSelect = { index ->
                        selectedIndex = index
                        onTimeoutSelected(index)
                    },
                    enabled = state.grantEnabled
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 警告文本
                Text(
                    text = context.getString(CoreR.string.su_warning),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮区域
                SuRequestButtons(
                    remainingSeconds = state.remainingSeconds,
                    grantEnabled = state.grantEnabled,
                    onGrant = onGrant,
                    onDeny = onDeny
                )
            }
        }
    }
}

/**
 * 应用信息展示组件
 *
 * @param icon 应用图标
 * @param appName 应用名称
 * @param packageName 包名
 * @param isSharedUid 是否为共享 UID
 */
@Composable
private fun SuRequestAppInfo(
    icon: Drawable?,
    appName: String,
    packageName: String,
    isSharedUid: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // 应用图标
        if (icon != null) {
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 应用名称和包名
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            val displayName = if (isSharedUid) "[${stringResource(CoreR.string.shared_uid)}] $appName" else appName
            Text(
                text = displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = packageName,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 超时选择器组件
 * 使用简单的文本按钮触发下拉选择，支持展开/收起动画
 *
 * @param options 选项数组
 * @param selectedIndex 当前选中索引
 * @param onSelect 选择回调
 * @param enabled 是否可用
 */
@Composable
private fun SuRequestTimeoutSelector(
    options: Array<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean
) {
    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
    var showSelector by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 未展开时显示当前选择按钮
        if (!showSelector) {
            TextButton(
                text = options.getOrNull(currentIndex) ?: "",
                onClick = { if (enabled) showSelector = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 带动画的选项列表
        AnimatedVisibility(
            visible = showSelector,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == currentIndex
                    TextButton(
                        text = option,
                        onClick = {
                            currentIndex = index
                            onSelect(index)
                            showSelector = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (isSelected) {
                            ButtonDefaults.textButtonColorsPrimary()
                        } else {
                            ButtonDefaults.textButtonColors()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 请求按钮区域组件
 *
 * @param remainingSeconds 倒计时剩余秒数
 * @param grantEnabled 允许按钮是否可用
 * @param onGrant 允许回调
 * @param onDeny 拒绝回调
 */
@Composable
private fun SuRequestButtons(
    remainingSeconds: Int,
    grantEnabled: Boolean,
    onGrant: () -> Unit,
    onDeny: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 拒绝按钮（带倒计时）
        val denyText = if (remainingSeconds > 0) {
            "${context.getString(CoreR.string.deny)} ($remainingSeconds)"
        } else {
            context.getString(CoreR.string.deny)
        }

        TextButton(
            text = denyText,
            onClick = onDeny,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColors()
        )

        Spacer(modifier = Modifier.width(20.dp))

        // 允许按钮
        TextButton(
            text = context.getString(CoreR.string.grant),
            onClick = onGrant,
            enabled = grantEnabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}

/**
 * 倒计时副作用
 * 在 ViewModel 中管理倒计时，这里仅用于预览或独立使用
 *
 * @param totalSeconds 总秒数
 * @param onTick 每秒回调
 * @param onFinish 结束回调
 */
@Composable
fun CountdownEffect(
    totalSeconds: Int,
    onTick: (Int) -> Unit,
    onFinish: () -> Unit
) {
    var remaining by remember { mutableIntStateOf(totalSeconds) }

    LaunchedEffect(totalSeconds) {
        remaining = totalSeconds
        while (remaining > 0) {
            delay(1000)
            remaining--
            onTick(remaining)
        }
        onFinish()
    }
}
