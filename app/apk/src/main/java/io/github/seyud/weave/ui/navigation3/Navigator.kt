package io.github.seyud.weave.ui.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

/**
 * 导航器：持有一个 miuix-nav 的 backStack，提供 push/pop/replace 方法
 *
 * miuix-nav 要求同一栈内每个 entry 的 contentKey 唯一（重复压入相同的路由值会被 runtime 拒绝），
 * 因此 [push] 是幂等的：已在栈上的路由不再重复压入。需要多个并存实例的路由应携带可区分的值。
 */
class Navigator(
    val backStack: NavBackStack,
) {
    /** 压入新路由（幂等） */
    fun push(key: NavKey) {
        if (key !in backStack) {
            backStack.add(key)
        }
    }

    /** 替换栈顶路由 */
    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    /** 弹出栈顶路由（保留栈底入口：miuix-nav 的 backStack 不允许为空） */
    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    /** 弹出直到 predicate 匹配栈顶（保留栈底入口） */
    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /** 获取当前栈顶路由 */
    fun current(): NavKey? {
        return backStack.lastOrNull()
    }

    /** 获取 backStack 大小 */
    fun backStackSize(): Int {
        return backStack.size
    }
}

@Composable
fun rememberNavigator(startRoute: Route): Navigator {
    val backStack = rememberNavBackStack<Route>(startRoute)
    return remember(backStack) { Navigator(backStack) }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
