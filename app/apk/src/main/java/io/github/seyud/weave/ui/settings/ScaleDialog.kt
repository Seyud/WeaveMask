package io.github.seyud.weave.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import io.github.seyud.weave.core.R as CoreR

@Composable
fun ScaleDialog(
    showDialog: MutableState<Boolean>,
    scaleState: () -> Float,
    onScaleChange: (Float) -> Unit,
) {
    OverlayDialog(
        title = stringResource(CoreR.string.settings_page_scale),
        summary = "80% - 110%",
        show = showDialog.value,
        onDismissRequest = { showDialog.value = false },
    ) {
        var text by remember(showDialog.value) {
            mutableStateOf((scaleState() * 100).toInt().toString())
        }
        TextField(
            modifier = Modifier.padding(bottom = 16.dp),
            value = text,
            maxLines = 1,
            trailingIcon = {
                Text(
                    text = "%",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = colorScheme.onSurfaceVariantActions,
                )
            },
            onValueChange = { newValue ->
                if (newValue.isEmpty()) {
                    text = ""
                } else if (newValue.all { it.isDigit() }) {
                    text = newValue
                }
            },
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = { showDialog.value = false },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(android.R.string.ok),
                onClick = {
                    val parsed = text.toIntOrNull()
                    val clamped = parsed?.coerceIn(80, 110) ?: (scaleState() * 100).toInt()
                    onScaleChange(clamped / 100f)
                    showDialog.value = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
