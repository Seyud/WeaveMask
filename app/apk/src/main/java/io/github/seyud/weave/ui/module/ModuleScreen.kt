package io.github.seyud.weave.ui.module

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.ui.component.SearchStatus
import io.github.seyud.weave.ui.module.components.ModuleScreenContent
import io.github.seyud.weave.ui.module.components.ModuleScreenTopBar
import io.github.seyud.weave.ui.module.components.ModuleSearchResultsHost
import io.github.seyud.weave.ui.module.dialogs.ModuleScreenDialogs
import io.github.seyud.weave.ui.module.state.rememberLocalModulePicker
import io.github.seyud.weave.ui.module.state.rememberModuleScreenLocalState
import io.github.seyud.weave.ui.module.state.rememberModuleSortPreferences
import io.github.seyud.weave.ui.module.state.rememberShortcutIconPicker
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ModuleScreen(
    viewModel: ModuleViewModel,
    contentBottomPadding: Dp,
    onInstallModuleFromLocal: (List<Uri>) -> Unit,
    onOpenRepo: () -> Unit,
    onRunAction: (String, String) -> Unit,
    onOpenWebUi: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiActivity = context as? MainActivity
    val uiState = viewModel.uiState
    val searchModulesLabel = stringResource(CoreR.string.search_modules_label)
    val localState = rememberModuleScreenLocalState(searchModulesLabel)
    val sortPreferences = rememberModuleSortPreferences(context)
    val shortcutState = rememberModuleShortcutState(context)
    val launchLocalModulePicker = rememberLocalModulePicker(
        onModulePicked = viewModel::requestInstallLocalModule,
    )
    val launchShortcutIconPicker = rememberShortcutIconPicker(
        onIconPicked = shortcutState::updateIconUri,
    )
    val enableBlur = LocalEnableBlur.current
    val blurBackdrop = rememberBarBlurBackdrop(enableBlur, MiuixTheme.colorScheme.surface)
    val scrollBehavior = MiuixScrollBehavior()
    val uiSearchStatus = localState.searchStatus.copy(
        resultStatus = when {
            uiState.isLoading -> SearchStatus.ResultStatus.LOAD
            uiState.modules.isEmpty() -> SearchStatus.ResultStatus.EMPTY
            else -> SearchStatus.ResultStatus.SHOW
        }
    )

    fun onModuleAddShortcut(module: ModuleInfo) {
        shortcutState.bindModule(module)
        when (shortcutState.availableTypes.size) {
            0 -> Unit
            1 -> {
                shortcutState.selectType(shortcutState.availableTypes.first())
                localState.showShortcutDialog = true
            }

            else -> {
                localState.showShortcutTypeDialog = true
            }
        }
    }

    DisposableEffect(onRunAction) {
        viewModel.onRunAction = onRunAction
        onDispose {
            viewModel.onRunAction = null
        }
    }

    LaunchedEffect(localState.hasStartedLoading) {
        if (!localState.hasStartedLoading) {
            localState.hasStartedLoading = true
            sortPreferences.restore(viewModel)
            viewModel.startLoading()
        }
    }

    LaunchedEffect(localState.searchStatus.searchText) {
        if (uiState.query != localState.searchStatus.searchText) {
            viewModel.setQuery(localState.searchStatus.searchText)
        }
    }

    LaunchedEffect(
        uiState.sortEnabledFirst,
        uiState.sortUpdateFirst,
        uiState.sortExecutableFirst,
    ) {
        sortPreferences.persist(uiState)
    }

    MiuixTheme {
        ModuleScreenDialogs(
            uiActivity = uiActivity,
            onlineInstallDialogState = viewModel.onlineInstallDialogState,
            localInstallDialogState = viewModel.localInstallDialogState,
            shortcutState = shortcutState,
            showShortcutDialog = localState.showShortcutDialog,
            showShortcutTypeDialog = localState.showShortcutTypeDialog,
            onDismissOnlineInstallDialog = viewModel::dismissOnlineInstallDialog,
            onDismissLocalInstallDialog = viewModel::dismissLocalInstallDialog,
            onConfirmLocalInstall = onInstallModuleFromLocal,
            onDismissShortcutTypeDialog = { localState.showShortcutTypeDialog = false },
            onSelectShortcutType = { type ->
                localState.showShortcutTypeDialog = false
                shortcutState.selectType(type)
                localState.showShortcutDialog = true
            },
            onDismissShortcutDialog = { localState.showShortcutDialog = false },
            onPickShortcutIcon = launchShortcutIconPicker,
        )

        Scaffold(
            modifier = modifier,
            popupHost = {
                ModuleSearchResultsHost(
                    uiSearchStatus = uiSearchStatus,
                    modules = uiState.modules,
                    contentBottomPadding = contentBottomPadding,
                    onSearchStatusChange = { localState.searchStatus = it },
                    onToggleModule = { module, enabled -> viewModel.toggleModule(module.id, enabled) },
                    onRunAction = { module -> viewModel.runAction(module.id, module.name) },
                    onOpenWebUi = { module -> onOpenWebUi(module.id, module.name) },
                    onAddShortcut = ::onModuleAddShortcut,
                    onDownloadUpdate = { module -> viewModel.downloadPressed(module.updateInfo) },
                    onToggleModuleRemove = { module -> viewModel.toggleModuleRemove(module.id) },
                )
            },
            contentWindowInsets = WindowInsets.systemBars
                .add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal),
            topBar = {
                ModuleScreenTopBar(
                    uiSearchStatus = uiSearchStatus,
                    uiState = uiState,
                    blurBackdrop = blurBackdrop,
                    scrollBehavior = scrollBehavior,
                    onSearchStatusChange = { localState.searchStatus = it },
                    showTopPopup = localState.showTopPopup,
                    onShowTopPopupChange = { localState.showTopPopup = it },
                    onOpenRepo = onOpenRepo,
                    onToggleSortEnabledFirst = {
                        viewModel.setSortEnabledFirst(!uiState.sortEnabledFirst)
                    },
                    onToggleSortUpdateFirst = {
                        viewModel.setSortUpdateFirst(!uiState.sortUpdateFirst)
                    },
                    onToggleSortExecutableFirst = {
                        viewModel.setSortExecutableFirst(!uiState.sortExecutableFirst)
                    },
                )
            },
            content = { innerPadding ->
                ModuleScreenContent(
                    innerPadding = innerPadding,
                    uiState = uiState,
                    uiSearchStatus = uiSearchStatus,
                    contentBottomPadding = contentBottomPadding,
                    blurBackdrop = blurBackdrop,
                    scrollBehavior = scrollBehavior,
                    onSearchStatusChange = { localState.searchStatus = it },
                    onRefresh = viewModel::refresh,
                    onInstallPressed = launchLocalModulePicker,
                    onToggleModule = { module, enabled -> viewModel.toggleModule(module.id, enabled) },
                    onRunAction = { module -> viewModel.runAction(module.id, module.name) },
                    onOpenWebUi = { module -> onOpenWebUi(module.id, module.name) },
                    onAddShortcut = ::onModuleAddShortcut,
                    onDownloadUpdate = { module -> viewModel.downloadPressed(module.updateInfo) },
                    onToggleModuleRemove = { module -> viewModel.toggleModuleRemove(module.id) },
                )
            },
        )
    }
}
