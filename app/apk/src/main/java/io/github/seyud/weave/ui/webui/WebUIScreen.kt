package io.github.seyud.weave.ui.webui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.TextField

@Composable
fun rememberFileLauncher(webUIState: WebUIState): ActivityResultLauncher<Intent> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                data.clipData?.let { clipData ->
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } ?: data.data?.let { arrayOf(it) }
            }
        } else null
        webUIState.onFileChooserResult(uris)
    }
}

@Composable
fun WebUIScreen(webUIState: WebUIState) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowInsets = WindowInsets.safeDrawing
    val innerPadding = if (webUIState.isInsetsEnabled) PaddingValues(0.dp) else windowInsets.asPaddingValues()
    val fileLauncher = rememberFileLauncher(webUIState)

    LaunchedEffect(density, layoutDirection, windowInsets, webUIState.isInsetsEnabled) {
        if (!webUIState.isInsetsEnabled) return@LaunchedEffect
        snapshotFlow {
            val top = (windowInsets.getTop(density) / density.density).toInt()
            val bottom = (windowInsets.getBottom(density) / density.density).toInt()
            val left = (windowInsets.getLeft(density, layoutDirection) / density.density).toInt()
            val right = (windowInsets.getRight(density, layoutDirection) / density.density).toInt()
            Insets(top, bottom, left, right)
        }.collect { newInsets ->
            if (webUIState.currentInsets != newInsets) {
                webUIState.currentInsets = newInsets
                webUIState.webView?.evaluateJavascript(newInsets.js, null)
            }
        }
    }

    BackHandler(enabled = webUIState.webCanGoBack) {
        webUIState.webView?.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        webUIState.webView?.let { webView ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { _ ->
                    webView.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            )
        }
    }

    HandleWebUIEvent(webUIState, fileLauncher)
    HandleWebViewLifecycle(webUIState)
    HandleConfigurationChanges(webUIState)
}

@Composable
private fun HandleWebUIEvent(
    webUIState: WebUIState,
    fileLauncher: ActivityResultLauncher<Intent>
) {
    when (val event = webUIState.uiEvent) {
        is WebUIEvent.ShowAlert -> {
            val showDialog = remember { mutableStateOf(true) }
            if (showDialog.value) {
                OverlayDialog(
                    show = showDialog.value,
                    title = "",
                    onDismissRequest = {
                        showDialog.value = false
                        webUIState.onAlertResult()
                    }
                ) {
                    Text(text = event.message)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                showDialog.value = false
                                webUIState.onAlertResult()
                            }
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        is WebUIEvent.ShowConfirm -> {
            val showDialog = remember { mutableStateOf(true) }
            if (showDialog.value) {
                OverlayDialog(
                    show = showDialog.value,
                    title = "",
                    onDismissRequest = {
                        showDialog.value = false
                        webUIState.onConfirmResult(false)
                    }
                ) {
                    Text(text = event.message)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "Cancel",
                            onClick = {
                                showDialog.value = false
                                webUIState.onConfirmResult(false)
                            }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                showDialog.value = false
                                webUIState.onConfirmResult(true)
                            }
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        is WebUIEvent.ShowPrompt -> {
            val showDialog = remember { mutableStateOf(true) }
            var inputValue by remember { mutableStateOf(event.defaultValue) }
            if (showDialog.value) {
                OverlayDialog(
                    show = showDialog.value,
                    title = "",
                    onDismissRequest = {
                        showDialog.value = false
                        webUIState.onPromptResult(null)
                    }
                ) {
                    Text(text = event.message)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "Cancel",
                            onClick = {
                                showDialog.value = false
                                webUIState.onPromptResult(null)
                            }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                showDialog.value = false
                                webUIState.onPromptResult(inputValue)
                            }
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        is WebUIEvent.ShowFileChooser -> {
            LaunchedEffect(event) {
                fileLauncher.launch(event.intent)
            }
        }

        else -> {}
    }
}

@Composable
private fun HandleWebViewLifecycle(webUIState: WebUIState) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, webUIState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webUIState.webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webUIState.webView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun HandleConfigurationChanges(webUIState: WebUIState) {
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.fontScale, webUIState.webView) {
        webUIState.webView?.settings?.textZoom = (configuration.fontScale * 100).toInt()
    }
}
