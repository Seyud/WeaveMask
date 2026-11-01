package io.github.seyud.weave.ui.scriptrun

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seyud.weave.core.script.ParamError
import io.github.seyud.weave.core.script.PreparedScript
import io.github.seyud.weave.core.script.ScriptOutcome
import io.github.seyud.weave.core.script.ScriptPackageException.Reason
import io.github.seyud.weave.core.script.ScriptParam
import io.github.seyud.weave.core.script.ScriptRepository
import io.github.seyud.weave.core.script.ScriptRunRecord
import io.github.seyud.weave.ui.component.AutoScrollToBottom
import io.github.seyud.weave.ui.component.MiuixConfirmDialog
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.DateFormat
import java.util.Date
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.core.registry.ExpectedFile

/**
 * Run page of a registry one-shot script (`kind = script`): verify and extract the download,
 * collect its parameters, show action.sh, then run it once and stream the output.
 *
 * Everything stateful lives in [ScriptRunViewModel]; this file only renders [ScriptRunUiState]
 * and owns the confirmation dialog, which is pure view state.
 */
@Composable
fun ScriptRunScreen(
    zipUri: Uri,
    registryId: String,
    displayName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Verified size + SHA-256 of the registry release, re-checked before the script is unpacked. */
    expectedSize: Long = 0L,
    expectedSha256: String? = null,
) {
    val viewModel: ScriptRunViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The ViewModel survives recomposition and rotation, so this only prepares on first entry.
    LaunchedEffect(zipUri, registryId, expectedSha256) {
        val expected = expectedSha256?.takeIf { expectedSize > 0 }?.let { ExpectedFile(expectedSize, it) }
        viewModel.load(zipUri, registryId, displayName, expected)
    }

    val running = state.phase == ScriptRunPhase.RUNNING
    // The script cannot be interrupted: leaving mid-run would orphan its root shell.
    BackHandler(enabled = running) { }

    var showConfirm by rememberSaveable { mutableStateOf(false) }
    MiuixConfirmDialog(
        show = showConfirm,
        title = stringResource(CoreR.string.script_run_title),
        summary = stringResource(
            CoreR.string.script_run_confirm,
            state.prepared?.manifest?.name ?: displayName,
        ),
        confirmText = stringResource(CoreR.string.script_run),
        dismissText = stringResource(android.R.string.cancel),
        onDismissRequest = { showConfirm = false },
        onConfirm = {
            showConfirm = false
            viewModel.run()
        },
    )

    val scrollBehavior = MiuixScrollBehavior()
    val surfaceColor = colorScheme.surface
    val blurBackdrop = rememberBarBlurBackdrop(LocalEnableBlur.current, surfaceColor)
    // The registry entry name until module.prop is read, so the page identifies itself from the
    // first frame on.
    val title = state.prepared?.manifest?.name?.takeIf { it.isNotBlank() }
        ?: state.displayName.ifBlank { stringResource(CoreR.string.script_run_title) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBarBlur(blurBackdrop, surfaceColor),
                color = barBlurContainerColor(blurBackdrop, surfaceColor),
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !running) {
                        val layoutDirection = LocalLayoutDirection.current
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            imageVector = MiuixIcons.Back,
                            tint = colorScheme.onSurface,
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .attachBarBlurBackdrop(blurBackdrop)
        ) {
            if (state.prepared == null && state.failure == null) {
                PreparingContent()
                return@Box
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .imePadding()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item { Spacer(modifier = Modifier.height(12.dp)) }
                val failure = state.failure
                val prepared = state.prepared
                if (failure != null || prepared == null) {
                    item { FailureCard(failure ?: ScriptRunFailure.Other(null)) }
                } else {
                    item { InfoCard(prepared) }
                    item { NoticeCard(prepared.manifest.id, modifier = Modifier.padding(top = 12.dp)) }
                    if (prepared.params.params.isNotEmpty()) {
                        item {
                            ParamsSection(
                                prepared = prepared,
                                state = state,
                                onValueChange = viewModel::setValue,
                            )
                        }
                    }
                    item { SourceSection(prepared, modifier = Modifier.padding(top = 12.dp)) }
                    item {
                        Button(
                            onClick = { showConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            enabled = state.canRun,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(text = stringResource(CoreR.string.script_run))
                        }
                    }
                    if (state.console.isNotEmpty() || state.result != null) {
                        item { ConsoleSection(state, modifier = Modifier.padding(top = 12.dp)) }
                    }
                }
                item {
                    Spacer(
                        modifier = Modifier.height(
                            24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PreparingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InfiniteProgressIndicator()
            Text(
                text = stringResource(CoreR.string.script_preparing),
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FailureCard(failure: ScriptRunFailure) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(CoreR.string.script_error_title),
            summary = failureText(failure),
        )
    }
}

@Composable
private fun InfoCard(prepared: PreparedScript) {
    val manifest = prepared.manifest
    val authorLabel = stringResource(CoreR.string.module_author)
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = manifest.name,
            summary = buildString {
                append(scriptSubtitle(manifest.id, manifest.version, manifest.versionCode))
                if (manifest.author.isNotBlank()) {
                    append('\n').append(authorLabel).append(": ").append(manifest.author)
                }
                if (manifest.description.isNotBlank()) {
                    append('\n').append('\n').append(manifest.description)
                }
            },
        )
    }
}

@Composable
private fun NoticeCard(manifestId: String?, modifier: Modifier = Modifier) {
    // The last run lives in shared preferences, not in the script package: re-read it whenever a
    // run is recorded (runCount) instead of trusting a value captured at prepare time.
    val runCount by ScriptRepository.runCount.collectAsStateWithLifecycle()
    val lastRun by produceState<ScriptRunRecord?>(initialValue = null, manifestId, runCount) {
        value = manifestId?.let { withContext(Dispatchers.IO) { ScriptRepository.history.last(it) } }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        BasicComponent(summary = stringResource(CoreR.string.script_notice))
        BasicComponent(summary = lastRunText(lastRun))
    }
}

@Composable
private fun ParamsSection(
    prepared: PreparedScript,
    state: ScriptRunUiState,
    onValueChange: (String, String) -> Unit,
) {
    SmallTitle(
        text = stringResource(CoreR.string.script_params),
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        prepared.params.params.forEach { param ->
            val value = state.values[param.key] ?: param.defaultValue
            val error = state.errors[param.key]
            when (param) {
                is ScriptParam.Bool -> SwitchPreference(
                    title = param.label,
                    summary = error?.let { parameterErrorText(param, it) } ?: param.description,
                    checked = value == "true",
                    onCheckedChange = { onValueChange(param.key, it.toString()) },
                )

                is ScriptParam.Choice -> OverlayDropdownPreference(
                    title = param.label,
                    summary = error?.let { parameterErrorText(param, it) } ?: param.description,
                    items = param.options.map { it.label ?: it.value },
                    selectedIndex = param.options.indexOfFirst { it.value == value }.coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        param.options.getOrNull(index)?.let { onValueChange(param.key, it.value) }
                    },
                )

                is ScriptParam.Text, is ScriptParam.Integer -> TextParamRow(
                    param = param,
                    value = value,
                    error = error,
                    onValueChange = { onValueChange(param.key, it) },
                )
            }
        }
    }
}

/**
 * Text and number parameters need an editable field, which Miuix has no list-row form of: the
 * label sits above a full-width field. The selection is kept in a local [TextFieldValue], because
 * the ViewModel only stores the plain string (the cursor must not jump while typing).
 */
@Composable
private fun TextParamRow(
    param: ScriptParam,
    value: String,
    error: ParamError?,
    onValueChange: (String) -> Unit,
) {
    val field = remember(param.key) { mutableStateOf(TextFieldValue(value)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = param.label, fontSize = 16.sp, color = colorScheme.onSurface)
        TextField(
            value = field.value,
            onValueChange = {
                field.value = it
                onValueChange(it.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = param.label,
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = if (param is ScriptParam.Integer) {
                KeyboardOptions(keyboardType = integerKeyboardType(param))
            } else {
                KeyboardOptions.Default
            },
        )
        val hint = error?.let { parameterErrorText(param, it) } ?: param.description
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 12.sp,
                color = if (error != null) colorScheme.error else colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SourceSection(prepared: PreparedScript, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(CoreR.string.script_source),
            summary = if (prepared.actionSourceTruncated) {
                stringResource(CoreR.string.script_source_truncated)
            } else {
                null
            },
            onClick = { expanded = !expanded },
            endActions = {
                Icon(
                    imageVector = if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.onSurfaceVariantActions,
                    contentDescription = null,
                )
            },
        )
        if (expanded) {
            val source = rememberShellHighlight(
                prepared.actionSource,
                ShellHighlightColors.forBackground(colorScheme.surfaceContainer),
            )
            // Long lines scroll sideways instead of wrapping: shell source is read line by line.
            SelectionContainer {
                Text(
                    text = source,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ConsoleSection(state: ScriptRunUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.phase == ScriptRunPhase.RUNNING) {
                    InfiniteProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                }
                val result = state.result
                if (result != null) {
                    val (message, color) = when (result.outcome) {
                        ScriptOutcome.SUCCESS ->
                            stringResource(CoreR.string.script_result_success) to colorScheme.primary

                        ScriptOutcome.NOT_APPLICABLE ->
                            stringResource(CoreR.string.script_result_not_applicable) to
                                    colorScheme.onSurfaceVariantSummary

                        ScriptOutcome.FAILURE ->
                            stringResource(CoreR.string.script_result_failed, result.exitCode) to
                                    colorScheme.error
                    }
                    Text(text = message, color = color)
                }
            }
            if (state.console.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CONSOLE_HEIGHT)
                        .padding(top = 8.dp),
                ) {
                    items(state.console) { line ->
                        Text(
                            text = line.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            // stderr in the error colour: a failing script usually only explains
                            // itself there.
                            color = if (line.isError) colorScheme.error else colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
                // The console has its own scroll: follow the newest line unless the user scrolled up.
                AutoScrollToBottom(listState = listState, itemCount = state.console.size)
            }
        }
    }
}

@Composable
private fun failureText(failure: ScriptRunFailure): String = when (failure) {
    is ScriptRunFailure.InvalidPackage -> when (failure.reason) {
        Reason.CORRUPT -> stringResource(CoreR.string.script_error_corrupt)
        Reason.UNSAFE_ENTRY -> stringResource(CoreR.string.script_error_unsafe)
        Reason.TOO_LARGE -> stringResource(CoreR.string.script_error_too_large)
        Reason.MISSING_MODULE_PROP -> stringResource(CoreR.string.script_error_missing_prop)
        Reason.NOT_ONESHOT -> stringResource(CoreR.string.script_error_not_oneshot)
        Reason.INVALID_ID -> stringResource(CoreR.string.script_error_invalid_id)
        Reason.ID_MISMATCH -> stringResource(CoreR.string.script_error_id_mismatch)
        Reason.MISSING_ACTION -> stringResource(CoreR.string.script_error_missing_action)
        Reason.INVALID_PARAMS ->
            stringResource(CoreR.string.script_error_params, failure.detail.orEmpty())
    }

    ScriptRunFailure.Unverified -> stringResource(CoreR.string.script_error_unverified)
    ScriptRunFailure.VerificationFailed -> stringResource(CoreR.string.script_error_verify_failed)
    is ScriptRunFailure.Other -> stringResource(CoreR.string.script_error_generic, failure.message.orEmpty())
}

@Composable
private fun parameterErrorText(param: ScriptParam, error: ParamError): String = when (error) {
    ParamError.REQUIRED -> stringResource(CoreR.string.script_param_required)
    ParamError.TOO_LONG ->
        stringResource(CoreR.string.script_param_too_long, (param as? ScriptParam.Text)?.maxLength ?: 0)

    ParamError.INVALID_CHARACTERS -> stringResource(CoreR.string.script_param_invalid_characters)
    ParamError.NOT_AN_INTEGER -> stringResource(CoreR.string.script_param_not_integer)
    ParamError.BELOW_MIN ->
        stringResource(CoreR.string.script_param_below_min, (param as? ScriptParam.Integer)?.min ?: 0)

    ParamError.ABOVE_MAX ->
        stringResource(CoreR.string.script_param_above_max, (param as? ScriptParam.Integer)?.max ?: 0)

    ParamError.INVALID_OPTION, ParamError.INVALID_BOOLEAN ->
        stringResource(CoreR.string.script_param_invalid)
}

@Composable
private fun lastRunText(record: ScriptRunRecord?): String {
    // Formatted once per record: a date format instantiation per recomposition would be wasteful.
    val time = remember(record?.time) {
        record?.time?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
        }
    }
    // Read once: a public property of another module cannot be smart cast after a null check.
    val versionCode = record?.versionCode
    return when {
        record == null || time == null -> stringResource(CoreR.string.script_never_run)
        versionCode != null ->
            stringResource(CoreR.string.script_last_run_version, time, versionCode, record.exitCode)

        else -> stringResource(CoreR.string.script_last_run, time, record.exitCode)
    }
}

/** "ID: foo · 1.0 (100)" — the identity of the script, above the free-text fields. */
private fun scriptSubtitle(id: String, version: String, versionCode: Int?): String = buildString {
    append("ID: ").append(id)
    if (version.isNotBlank()) append(" · ").append(version)
    if (versionCode != null) append(" (").append(versionCode).append(')')
}

/**
 * The plain number keypad of most IMEs has no "-": use a text keyboard when negatives are allowed.
 */
private fun integerKeyboardType(param: ScriptParam.Integer): KeyboardType =
    if ((param.min ?: Int.MIN_VALUE) < 0) KeyboardType.Ascii else KeyboardType.Number

private val CONSOLE_HEIGHT = 220.dp
