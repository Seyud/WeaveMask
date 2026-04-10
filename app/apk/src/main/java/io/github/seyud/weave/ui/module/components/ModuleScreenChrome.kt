package io.github.seyud.weave.ui.module.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.component.SearchPager
import io.github.seyud.weave.ui.component.SearchBarFake
import io.github.seyud.weave.ui.component.SearchStatus
import io.github.seyud.weave.ui.module.ModuleInfo
import io.github.seyud.weave.ui.module.ModuleUiState
import io.github.seyud.weave.ui.module.RebootListPopup
import io.github.seyud.weave.ui.util.barBlurContainerColor
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ModuleSearchResultsHost(
    uiSearchStatus: SearchStatus,
    modules: List<ModuleInfo>,
    contentBottomPadding: Dp,
    onSearchStatusChange: (SearchStatus) -> Unit,
    onToggleModule: (ModuleInfo, Boolean) -> Unit,
    onRunAction: (ModuleInfo) -> Unit,
    onOpenWebUi: (ModuleInfo) -> Unit,
    onAddShortcut: (ModuleInfo) -> Unit,
    onDownloadUpdate: (ModuleInfo) -> Unit,
    onToggleModuleRemove: (ModuleInfo) -> Unit,
) {
    uiSearchStatus.SearchPager(
        onSearchStatusChange = onSearchStatusChange,
        defaultResult = {},
        resultModifier = Modifier.padding(horizontal = 16.dp),
        resultContentPadding = PaddingValues(top = 8.dp, bottom = contentBottomPadding),
    ) {
        moduleItems(
            modules = modules,
            onToggleModule = onToggleModule,
            onRunAction = onRunAction,
            onOpenWebUi = onOpenWebUi,
            onAddShortcut = onAddShortcut,
            onDownloadUpdate = onDownloadUpdate,
            onToggleModuleRemove = onToggleModuleRemove,
        )
    }
}

@Composable
internal fun ModuleScreenTopBar(
    uiSearchStatus: SearchStatus,
    uiState: ModuleUiState,
    blurBackdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    onSearchStatusChange: (SearchStatus) -> Unit,
    showTopPopup: Boolean,
    onShowTopPopupChange: (Boolean) -> Unit,
    onOpenRepo: () -> Unit,
    onToggleSortEnabledFirst: () -> Unit,
    onToggleSortUpdateFirst: () -> Unit,
    onToggleSortExecutableFirst: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    uiSearchStatus.TopAppBarAnim(
        blurBackdrop = blurBackdrop,
    ) {
        TopAppBar(
            color = barBlurContainerColor(blurBackdrop, MiuixTheme.colorScheme.surface),
            title = context.getString(CoreR.string.modules),
            titleColor = MiuixTheme.colorScheme.onBackground,
            largeTitleColor = MiuixTheme.colorScheme.onBackground,
            navigationIcon = {
                IconButton(
                    onClick = onOpenRepo,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storefront,
                        contentDescription = stringResource(CoreR.string.module_repo_open),
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            bottomContent = {
                Box(
                    modifier = Modifier
                        .alpha(if (uiSearchStatus.isCollapsed()) 1f else 0f)
                        .onGloballyPositioned { coordinates ->
                            with(density) {
                                val newOffsetY = coordinates.positionInWindow().y.toDp()
                                if (uiSearchStatus.offsetY != newOffsetY) {
                                    onSearchStatusChange(uiSearchStatus.copy(offsetY = newOffsetY))
                                }
                            }
                        }
                        .then(
                            if (uiSearchStatus.isCollapsed()) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures {
                                        onSearchStatusChange(
                                            uiSearchStatus.copy(current = SearchStatus.Status.EXPANDING)
                                        )
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    SearchBarFake(uiSearchStatus.label, dynamicTopPadding)
                }
            },
            actions = {
                ModuleTopBarActions(
                    uiState = uiState,
                    showTopPopup = showTopPopup,
                    onShowTopPopupChange = onShowTopPopupChange,
                    onToggleSortEnabledFirst = onToggleSortEnabledFirst,
                    onToggleSortUpdateFirst = onToggleSortUpdateFirst,
                    onToggleSortExecutableFirst = onToggleSortExecutableFirst,
                )
            },
        )
    }
}

@Composable
private fun ModuleTopBarActions(
    uiState: ModuleUiState,
    showTopPopup: Boolean,
    onShowTopPopupChange: (Boolean) -> Unit,
    onToggleSortEnabledFirst: () -> Unit,
    onToggleSortUpdateFirst: () -> Unit,
    onToggleSortExecutableFirst: () -> Unit,
) {
    OverlayListPopup(
        show = showTopPopup,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = { onShowTopPopupChange(false) },
    ) {
        ListPopupColumn {
            DropdownImpl(
                text = stringResource(CoreR.string.module_sort_enabled_first_label),
                isSelected = uiState.sortEnabledFirst,
                optionSize = 3,
                onSelectedIndexChange = { onToggleSortEnabledFirst() },
                index = 0,
            )
            DropdownImpl(
                text = stringResource(CoreR.string.module_sort_update_first_label),
                isSelected = uiState.sortUpdateFirst,
                optionSize = 3,
                onSelectedIndexChange = { onToggleSortUpdateFirst() },
                index = 1,
            )
            DropdownImpl(
                text = stringResource(CoreR.string.module_sort_executable_first_label),
                isSelected = uiState.sortExecutableFirst,
                optionSize = 3,
                onSelectedIndexChange = { onToggleSortExecutableFirst() },
                index = 2,
            )
        }
    }

    IconButton(
        modifier = Modifier.padding(end = 8.dp),
        onClick = { onShowTopPopupChange(true) },
        holdDownState = showTopPopup,
    ) {
        Icon(
            imageVector = MiuixIcons.MoreCircle,
            contentDescription = stringResource(CoreR.string.more_options_description),
        )
    }

    RebootListPopup(
        alignment = PopupPositionProvider.Align.TopEnd,
    )
}
