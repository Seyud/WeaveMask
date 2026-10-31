package io.github.seyud.weave.ui.component

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

/**
 * 控制台类输出列表：新内容追加时自动跟随到最新一行。
 *
 * 行为对齐 KernelSU manager 的刷写控制台（`LaunchedEffect(state.text) { animateScrollTo(maxValue) }`）：
 * 每来一行就平滑滚到末尾；[LaunchedEffect] 以 [itemCount] 为 key，新行到达会取消上一次滚动动画，
 * 所以"逐行打印"的安装脚本会一行一行跟下去，而"瞬间刷屏"也不会堆积动画队列。
 *
 * 与上游唯一的差别：用户主动上滑回看历史输出时暂停跟随，滚回底部后自动恢复，
 * 避免"想翻旧日志却被不断拽回底部"。
 *
 * 注意：暂停判定只认**用户拖动**（[LazyListState.interactionSource] 的 [DragInteraction]），
 * 不能用 `isScrollInProgress`——自动跟随自己发起的滚动同样会置位，逐行输出时会在动画
 * 交接的空隙里把自己误判成"用户离开了底部"，从而停掉跟随。
 *
 * @param listState 控制台列表的滚动状态
 * @param itemCount 当前输出行数
 */
@Composable
fun AutoScrollToBottom(
    listState: LazyListState,
    itemCount: Int,
) {
    // 是否跟随最新输出。用户把列表拖离底部时置 false，拖回底部后恢复
    var followBottom by remember(listState) { mutableStateOf(true) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions
            .filterIsInstance<DragInteraction.Stop>()
            .collect {
                // 拖动结束后可能还有惯性滑动，等它停稳再判断是否回到底部
                snapshotFlow { listState.isScrollInProgress }.first { !it }
                val layoutInfo = listState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?: return@collect
                followBottom = lastVisibleIndex >= layoutInfo.totalItemsCount - 1
            }
    }

    LaunchedEffect(listState, itemCount) {
        if (itemCount <= 0 || !followBottom) return@LaunchedEffect
        val target = itemCount - 1
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (target - lastVisible > FAR_JUMP_ITEMS) {
            // 距离过远（重进页面、日志极长）时直接定位，避免长距离滚动动画
            listState.scrollToItem(target)
        } else {
            listState.animateScrollToItem(target)
        }
    }
}

/** 超过这个行数差就不再平滑追赶，直接定位，避免长距离滚动耗时 */
private const val FAR_JUMP_ITEMS = 20
