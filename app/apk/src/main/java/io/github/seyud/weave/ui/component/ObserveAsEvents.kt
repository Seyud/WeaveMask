package io.github.seyud.weave.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

@Composable
fun <T> ObserveAsEvents(
    events: Flow<T>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEvent: suspend (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(events, lifecycleOwner, minActiveState) {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            events.collect { onEvent(it) }
        }
    }
}

suspend fun SnackbarHostState.showSnackbarEvent(
    message: String,
    duration: SnackbarDuration = SnackbarDuration.Short,
    actionLabel: String? = null,
    onActionPerformed: (() -> Unit)? = null,
) {
    while (true) {
        val current = newestSnackbarData() ?: break
        current.dismiss()
    }
    val result = showSnackbar(
        message = message,
        actionLabel = actionLabel,
        duration = duration,
    )
    if (result == SnackbarResult.ActionPerformed) {
        onActionPerformed?.invoke()
    }
}
