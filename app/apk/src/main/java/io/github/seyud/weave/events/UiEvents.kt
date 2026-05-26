package io.github.seyud.weave.events

import android.content.Context
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import io.github.seyud.weave.arch.ActivityExecutor
import io.github.seyud.weave.arch.ContextExecutor
import io.github.seyud.weave.arch.UiEvent
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.core.base.ContentResultCallback
import io.github.seyud.weave.core.base.IActivityExtension
import io.github.seyud.weave.core.base.relaunch
import io.github.seyud.weave.core.integration.AppShortcuts
import io.github.seyud.weave.ui.dialog.WeaveDialog
import io.github.seyud.weave.utils.TextHolder
import io.github.seyud.weave.utils.asText
import top.yukonga.miuix.kmp.basic.SnackbarDuration

class PermissionEvent(
    private val permission: String,
    private val callback: (Boolean) -> Unit
) : UiEvent(), ActivityExecutor {

    override fun invoke(activity: ComponentActivity) =
        (activity as? IActivityExtension)?.withPermission(permission, callback) ?: callback(false)
}

class BackPressEvent : UiEvent(), ActivityExecutor {
    override fun invoke(activity: ComponentActivity) {
        activity.onBackPressedDispatcher.onBackPressed()
    }
}

class DieEvent : UiEvent(), ActivityExecutor {
    override fun invoke(activity: ComponentActivity) {
        activity.finish()
    }
}

class RecreateEvent : UiEvent(), ActivityExecutor {
    override fun invoke(activity: ComponentActivity) {
        activity.relaunch()
    }
}

class AuthEvent(
    private val callback: () -> Unit
) : UiEvent(), ActivityExecutor {

    override fun invoke(activity: ComponentActivity) {
        (activity as? IActivityExtension)?.withAuthentication { if (it) callback() }
    }
}

class GetContentEvent(
    private val type: String,
    private val callback: ContentResultCallback
) : UiEvent(), ActivityExecutor {
    override fun invoke(activity: ComponentActivity) {
        (activity as? IActivityExtension)?.getContent(type, callback)
    }
}

class AddHomeIconEvent : UiEvent(), ContextExecutor {
    override fun invoke(context: Context) {
        AppShortcuts.addHomeIcon(context)
    }
}

class SnackbarEvent(
    private val msg: TextHolder,
    private val duration: SnackbarDuration = SnackbarDuration.Short,
) : UiEvent(), ActivityExecutor {

    constructor(
        @StringRes res: Int,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) : this(res.asText(), duration)

    constructor(
        msg: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) : this(msg.asText(), duration)

    fun resolveMessage(activity: ComponentActivity): String =
        msg.getText(activity.resources)

    fun resolveDuration(): SnackbarDuration = duration

    override fun invoke(activity: ComponentActivity) {
        (activity as? MainActivity)?.showSnackbar(this)
    }
}

class DialogEvent(
    private val builder: DialogBuilder
) : UiEvent(), ActivityExecutor {
    override fun invoke(activity: ComponentActivity) {
        WeaveDialog(activity).apply(builder::build).show()
    }
}

interface DialogBuilder {
    fun build(dialog: WeaveDialog)
}
