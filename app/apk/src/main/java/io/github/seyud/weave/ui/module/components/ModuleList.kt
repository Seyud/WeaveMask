package io.github.seyud.weave.ui.module.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.module.ModuleInfo
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@OptIn(ExperimentalScrollBarApi::class)
internal fun ModuleList(
    modules: List<ModuleInfo>,
    blurBackdrop: LayerBackdrop?,
    onInstallPressed: () -> Unit,
    onToggleModule: (ModuleInfo, Boolean) -> Unit,
    onRunAction: (ModuleInfo) -> Unit,
    onOpenWebUi: (ModuleInfo) -> Unit,
    onAddShortcut: (ModuleInfo) -> Unit,
    onDownloadUpdate: (ModuleInfo) -> Unit,
    onToggleModuleRemove: (ModuleInfo) -> Unit,
    topContentPadding: Dp,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = topContentPadding, bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null,
        ) {
            item {
                InstallModuleEntryButton(onClick = onInstallPressed)
            }

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

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = PaddingValues(top = topContentPadding, bottom = contentBottomPadding),
        )
    }
}

internal fun LazyListScope.moduleItems(
    modules: List<ModuleInfo>,
    onToggleModule: (ModuleInfo, Boolean) -> Unit,
    onRunAction: (ModuleInfo) -> Unit,
    onOpenWebUi: (ModuleInfo) -> Unit,
    onAddShortcut: (ModuleInfo) -> Unit,
    onDownloadUpdate: (ModuleInfo) -> Unit,
    onToggleModuleRemove: (ModuleInfo) -> Unit,
) {
    items(
        items = modules,
        key = { it.id },
        contentType = { "module" },
    ) { module ->
        ModuleItem(
            module = module,
            onToggleEnabled = { enabled -> onToggleModule(module, enabled) },
            onRunAction = { onRunAction(module) },
            onOpenWebUi = { onOpenWebUi(module) },
            onAddShortcut = { onAddShortcut(module) },
            onDownloadUpdate = { onDownloadUpdate(module) },
            onToggleRemoved = { onToggleModuleRemove(module) },
        )
    }
}
