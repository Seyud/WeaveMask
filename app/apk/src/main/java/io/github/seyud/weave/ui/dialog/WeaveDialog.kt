package io.github.seyud.weave.ui.dialog

import android.app.Activity
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

typealias DialogButtonClickListener = (DialogInterface) -> Unit

interface WeaveDialogHost {
    fun showWeaveDialog(dialog: WeaveDialog)
    fun dismissWeaveDialog(dialog: WeaveDialog)
}

class WeaveDialog(
    private val activity: Activity,
    @Suppress("UNUSED_PARAMETER") theme: Int = 0,
) : DialogInterface {

    private val host = activity as? WeaveDialogHost

    private var iconDrawable: Drawable? = null
    private var title: CharSequence = ""
    private var message: CharSequence = ""
    private var contentView: View? = null
    private var cancelable: Boolean = true

    private val positiveButton = ButtonViewModel()
    private val neutralButton = ButtonViewModel()
    private val negativeButton = ButtonViewModel()

    enum class ButtonType {
        POSITIVE,
        NEUTRAL,
        NEGATIVE,
    }

    interface Button {
        var icon: Int
        var text: Any
        var isEnabled: Boolean
        var doNotDismiss: Boolean

        fun onClick(listener: DialogButtonClickListener)
    }

    inner class ButtonViewModel : Button {
        override var icon: Int = 0
        override var text: Any = ""
        override var isEnabled: Boolean = true
        override var doNotDismiss: Boolean = false

        private var listener: DialogButtonClickListener = {}

        val visible: Boolean
            get() = icon != 0 || resolvedText.isNotEmpty()

        val resolvedText: CharSequence
            get() = when (val value = text) {
                is Int -> activity.getText(value)
                is CharSequence -> value
                else -> value.toString()
            }

        override fun onClick(listener: DialogButtonClickListener) {
            this.listener = listener
        }

        fun invoke() {
            listener(this@WeaveDialog)
            if (!doNotDismiss) {
                dismiss()
            }
        }
    }

    @Immutable
    private data class ButtonRenderState(
        val iconRes: Int,
        val text: CharSequence,
        val isEnabled: Boolean,
        val isPrimary: Boolean,
        val onClick: () -> Unit,
    )

    @StringRes
    fun setTitle(titleId: Int) {
        title = activity.getString(titleId)
    }

    fun setTitle(title: CharSequence?) {
        this.title = title ?: ""
    }

    fun setMessage(@StringRes msgId: Int, vararg args: Any) {
        message = activity.getString(msgId, *args)
    }

    fun setMessage(message: CharSequence) {
        this.message = message
    }

    fun setIcon(@DrawableRes drawableRes: Int) {
        iconDrawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(activity, drawableRes)
    }

    fun setIcon(drawable: Drawable) {
        iconDrawable = drawable
    }

    fun setButton(buttonType: ButtonType, builder: Button.() -> Unit) {
        val button = when (buttonType) {
            ButtonType.POSITIVE -> positiveButton
            ButtonType.NEUTRAL -> neutralButton
            ButtonType.NEGATIVE -> negativeButton
        }
        button.apply(builder)
    }

    fun setView(view: View) {
        contentView = view
    }

    fun setCancelable(flag: Boolean) {
        cancelable = flag
    }

    fun show() {
        checkNotNull(host) { "WeaveDialog host is only available in activities implementing WeaveDialogHost" }
        host.showWeaveDialog(this)
    }

    override fun cancel() {
        dismiss()
    }

    override fun dismiss() {
        host?.dismissWeaveDialog(this)
    }

    @Composable
    internal fun Render() {
        val dismissRequest = if (cancelable) {
            { dismiss() }
        } else {
            {}
        }
        val buttons = remember(this) {
            listOf(
                neutralButton.toRenderState(isPrimary = false),
                negativeButton.toRenderState(isPrimary = false),
                positiveButton.toRenderState(isPrimary = true),
            ).filterNotNull()
        }

        WindowDialog(
            show = true,
            title = title.takeIf { it.isNotEmpty() }?.toString(),
            summary = message.takeIf { it.isNotEmpty() }?.toString(),
            onDismissRequest = dismissRequest,
        ) {
            Column {
                iconDrawable?.let { drawable ->
                    val bitmap = remember(drawable) {
                        runCatching { drawable.toBitmap() }.getOrNull()
                    }
                    if (bitmap != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (message.isEmpty()) {
                    contentView?.let { view ->
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { context ->
                                (view.parent as? ViewGroup)?.removeView(view)
                                view
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (buttons.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        buttons.forEachIndexed { index, button ->
                            Button(
                                onClick = button.onClick,
                                modifier = Modifier.weight(1f),
                                enabled = button.isEnabled,
                                colors = if (button.isPrimary) {
                                    ButtonDefaults.buttonColorsPrimary()
                                } else {
                                    ButtonDefaults.buttonColors(
                                        color = MiuixTheme.colorScheme.secondaryVariant,
                                        disabledColor = MiuixTheme.colorScheme.disabledSecondaryVariant,
                                        contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                                        disabledContentColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                                    )
                                },
                            ) {
                                if (button.iconRes != 0) {
                                    Icon(
                                        painter = painterResource(button.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (button.isPrimary) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.onBackground
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(text = button.text.toString())
                            }
                            if (index != buttons.lastIndex) {
                                Spacer(modifier = Modifier.width(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ButtonViewModel.toRenderState(isPrimary: Boolean): ButtonRenderState? {
        if (!visible) return null
        return ButtonRenderState(
            iconRes = icon,
            text = resolvedText,
            isEnabled = isEnabled,
            isPrimary = isPrimary,
            onClick = { invoke() },
        )
    }
}

@Composable
fun WeaveDialogHostContent(
    dialog: WeaveDialog?,
) {
    dialog?.Render()
}
