package io.github.seyud.weave.ui.install

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.component.MarkdownText
import io.github.seyud.weave.ui.component.MiuixTextInputDialog
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 安装方法枚举
 * 定义不同的安装方式
 */
enum class InstallMethod {
    PATCH,
    DIRECT,
    INACTIVE_SLOT,
    DOWNLOAD
}

/**
 * 安装页面屏幕
 * 显示安装选项和方法选择
 */
@Composable
fun InstallScreen(
    viewModel: InstallViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToFlash: (String, Uri?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val step = viewModel.step
    var keepVerity by remember { mutableStateOf(Config.keepVerity) }
    var keepEnc by remember { mutableStateOf(Config.keepEnc) }
    var recovery by remember { mutableStateOf(Config.recovery) }

    val skipOptions = viewModel.skipOptions
    val isRooted = viewModel.isRooted
    val noSecondSlot = viewModel.noSecondSlot

    val notes = viewModel.notes
    val hasNotes = notes.isNotEmpty()

    var selectedMethod by remember(viewModel.method) {
        mutableStateOf(
            when (viewModel.method) {
                InstallViewModel.METHOD_PATCH -> InstallMethod.PATCH
                InstallViewModel.METHOD_DIRECT -> InstallMethod.DIRECT
                InstallViewModel.METHOD_INACTIVE_SLOT -> InstallMethod.INACTIVE_SLOT
                InstallViewModel.METHOD_DOWNLOAD -> InstallMethod.DOWNLOAD
                else -> null
            }
        )
    }

    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }
    var downloadInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(viewModel.downloadUrl))
    }

    val openDownloadDialog = {
        downloadInput = TextFieldValue(viewModel.downloadUrl)
        showDownloadDialog = true
    }

    // 观察 viewModel.data (uri) 的变化
    var dataUri by remember { mutableStateOf(viewModel.data.value) }
    var deferredPatch by remember { mutableStateOf(viewModel.pendingPatch.value) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel) {
        val uriObserver = Observer<Uri?> { dataUri = it }
        val patchObserver = Observer<Pair<Uri, String>?> { deferredPatch = it }
        viewModel.data.observe(lifecycleOwner, uriObserver)
        viewModel.pendingPatch.observe(lifecycleOwner, patchObserver)
        onDispose {
            viewModel.data.removeObserver(uriObserver)
            viewModel.pendingPatch.removeObserver(patchObserver)
        }
    }

    // 文件选择器 - 选择后立即记录 URI，延迟复制到点击"开始"时
    val patchFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = context.contentResolver.query(
                it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    )
                } else null
            } ?: "boot.img"
            viewModel.setDeferredPatch(it, fileName)
        }
    }

    val trimmed = downloadInput.text.trim()
    val isValid = isValidDownloadUrl(trimmed)
    MiuixTextInputDialog(
        show = showDownloadDialog,
        title = stringResource(CoreR.string.download_dialog_title),
        value = downloadInput,
        onValueChange = { downloadInput = it },
        label = stringResource(CoreR.string.download_dialog_msg),
        helperText = if (downloadInput.text.isNotBlank() && !isValid) {
            stringResource(CoreR.string.module_repo_source_invalid)
        } else null,
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = { showDownloadDialog = false },
        onConfirm = {
            if (isValid) {
                viewModel.downloadUrl = trimmed
                showDownloadDialog = false
            }
        },
        confirmEnabled = isValid,
        useOverlay = true,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = context.getString(CoreR.string.install),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                if (!skipOptions) {
                    OptionsCard(
                        step = step,
                        keepVerity = keepVerity,
                        keepEnc = keepEnc,
                        recovery = recovery,
                        isSAR = Info.isSAR,
                        isFDE = Info.isFDE,
                        hasRamdisk = Info.ramdisk,
                        onKeepVerityChange = {
                            keepVerity = !keepVerity
                            Config.keepVerity = keepVerity
                        },
                        onKeepEncChange = {
                            keepEnc = !keepEnc
                            Config.keepEnc = keepEnc
                        },
                        onRecoveryChange = {
                            recovery = !recovery
                            Config.recovery = recovery
                        },
                        onNextClick = { viewModel.step = 1 }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                MethodCard(
                    step = step,
                    selectedMethod = selectedMethod,
                    isRooted = isRooted,
                    noSecondSlot = noSecondSlot,
                    dataUri = dataUri,
                    deferredPatch = deferredPatch,
                    downloadUrl = viewModel.downloadUrl,
                    isDownloadUrlValid = isValidDownloadUrl(viewModel.downloadUrl),
                    onRequestDownloadUrl = openDownloadDialog,
                    onMethodChange = { newMethod ->
                        selectedMethod = newMethod
                        viewModel.method = when (newMethod) {
                            InstallMethod.PATCH -> InstallViewModel.METHOD_PATCH
                            InstallMethod.DIRECT -> InstallViewModel.METHOD_DIRECT
                            InstallMethod.INACTIVE_SLOT -> InstallViewModel.METHOD_INACTIVE_SLOT
                            InstallMethod.DOWNLOAD -> InstallViewModel.METHOD_DOWNLOAD
                            null -> -1
                        }
                        // 如果选择了修补文件方法，立即触发文件选择器
                        if (newMethod == InstallMethod.PATCH) {
                            patchFilePicker.launch(arrayOf("*/*"))
                        }
                        if (newMethod == InstallMethod.DOWNLOAD) {
                            openDownloadDialog()
                        }
                    },
                    onInstallClick = {
                        scope.launch {
                            if (deferredPatch != null && dataUri == null) {
                                runCatching { viewModel.copyDeferredPatch(context) }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            error.message ?: context.getString(CoreR.string.failure),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }
                            }
                            viewModel.composeFlashRequest()?.let {
                                onNavigateToFlash(it.request.action, it.request.dataUri)
                            }
                        }
                    }
                )

                if (hasNotes) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NotesCard(notes = viewModel.notesText)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    )
}

/**
 * 安装选项卡片
 * 显示安装前的配置选项
 */
@Composable
private fun OptionsCard(
    step: Int,
    keepVerity: Boolean,
    keepEnc: Boolean,
    recovery: Boolean,
    isSAR: Boolean,
    isFDE: Boolean,
    hasRamdisk: Boolean,
    onKeepVerityChange: () -> Unit,
    onKeepEncChange: () -> Unit,
    onRecoveryChange: () -> Unit,
    onNextClick: () -> Unit
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    tint = if (step > 0) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainer,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = context.getString(CoreR.string.install_options_title),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (step == 0) {
                    TextButton(
                        text = context.getString(CoreR.string.install_next),
                        onClick = onNextClick
                    )
                }
            }

            if (step > 0) {
                Spacer(modifier = Modifier.height(16.dp))

                if (!isSAR) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onKeepVerityChange
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            state = if (keepVerity) ToggleableState.On else ToggleableState.Off,
                            onClick = onKeepVerityChange
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(CoreR.string.keep_dm_verity),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                }

                if (isFDE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onKeepEncChange
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            state = if (keepEnc) ToggleableState.On else ToggleableState.Off,
                            onClick = onKeepEncChange
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(CoreR.string.keep_force_encryption),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                }

                if (!hasRamdisk) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onRecoveryChange
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            state = if (recovery) ToggleableState.On else ToggleableState.Off,
                            onClick = onRecoveryChange
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(CoreR.string.recovery_mode),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 安装方法卡片
 * 显示安装方法选择和开始安装按钮
 */
@Composable
private fun MethodCard(
    step: Int,
    selectedMethod: InstallMethod?,
    isRooted: Boolean,
    noSecondSlot: Boolean,
    dataUri: Uri?,
    deferredPatch: Pair<Uri, String>?,
    downloadUrl: String,
    isDownloadUrlValid: Boolean,
    onRequestDownloadUrl: () -> Unit,
    onMethodChange: (InstallMethod?) -> Unit,
    onInstallClick: () -> Unit
) {
    val context = LocalContext.current

    val isMethodPatch = selectedMethod == InstallMethod.PATCH
    val isMethodDownload = selectedMethod == InstallMethod.DOWNLOAD
    val isMethodSelected = if (isMethodPatch) (dataUri != null || deferredPatch != null)
                           else if (isMethodDownload) isDownloadUrlValid
                           else selectedMethod != null
    val startContentColor = if (isMethodSelected) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.disabledOnPrimaryButton
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    tint = if (step > 1) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainer,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = context.getString(CoreR.string.install_method_title),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (step == 1) {
                    Button(
                        onClick = onInstallClick,
                        enabled = isMethodSelected,
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(
                            text = context.getString(CoreR.string.install_start),
                            color = startContentColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = null,
                            tint = startContentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (step == 1) {
                Spacer(modifier = Modifier.height(16.dp))

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onMethodChange(InstallMethod.PATCH) }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            state = if (selectedMethod == InstallMethod.PATCH) ToggleableState.On else ToggleableState.Off,
                            onClick = { onMethodChange(InstallMethod.PATCH) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(CoreR.string.select_patch_file),
                            style = MiuixTheme.textStyles.body1
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onMethodChange(InstallMethod.DOWNLOAD) }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            state = if (selectedMethod == InstallMethod.DOWNLOAD) ToggleableState.On else ToggleableState.Off,
                            onClick = { onMethodChange(InstallMethod.DOWNLOAD) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(CoreR.string.download_patch_file),
                            style = MiuixTheme.textStyles.body1
                        )
                    }

                    AnimatedVisibility(
                        visible = isMethodDownload && downloadUrl.isNotBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onRequestDownloadUrl
                                )
                                .padding(start = 36.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = downloadUrl,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            if (!isDownloadUrlValid) {
                                Text(
                                    text = stringResource(CoreR.string.module_repo_source_invalid),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (isRooted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onMethodChange(InstallMethod.DIRECT) }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                state = if (selectedMethod == InstallMethod.DIRECT) ToggleableState.On else ToggleableState.Off,
                                onClick = { onMethodChange(InstallMethod.DIRECT) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(CoreR.string.direct_install),
                                style = MiuixTheme.textStyles.body1
                            )
                        }
                    }

                    if (!noSecondSlot) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onMethodChange(InstallMethod.INACTIVE_SLOT) }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                state = if (selectedMethod == InstallMethod.INACTIVE_SLOT) ToggleableState.On else ToggleableState.Off,
                                onClick = { onMethodChange(InstallMethod.INACTIVE_SLOT) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(CoreR.string.install_inactive_slot),
                                style = MiuixTheme.textStyles.body1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 发布说明卡片
 * 显示版本更新说明，使用Markdown渲染
 */
@Composable
private fun NotesCard(notes: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        MarkdownText(
            content = notes,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun isValidDownloadUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val uri = url.trim().toUri()
    return uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrEmpty() &&
        !uri.path.isNullOrEmpty()
}
