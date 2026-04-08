package io.github.seyud.weave.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixConfirmDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    confirmText: String,
    dismissText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmTextColor: Color? = null,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = dismissText,
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(20.dp))
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = confirmTextColor?.let {
                    ButtonDefaults.textButtonColors(
                        textColor = it,
                        disabledTextColor = it.copy(alpha = 0.38f),
                    )
                } ?: ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
fun MiuixTextInputDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    helperText: String? = null,
    counterText: String? = null,
    confirmText: String,
    dismissText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = label,
                singleLine = true,
                useLabelAsPlaceholder = true,
            )
            if (helperText != null || counterText != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    helperText?.let {
                        Text(
                            text = it,
                            modifier = Modifier.weight(1f),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f))

                    counterText?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(start = 12.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = dismissText,
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = confirmEnabled,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
fun MiuixLoadingDialog(
    show: Boolean,
    title: String,
) {
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = {},
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                size = 32.dp,
                strokeWidth = 3.dp,
            )
        }
    }
}
