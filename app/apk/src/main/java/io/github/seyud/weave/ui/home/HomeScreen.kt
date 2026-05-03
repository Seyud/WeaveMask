package io.github.seyud.weave.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.module.RebootListPopup
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.theme.LocalHomeLayoutMode
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentBottomPadding: Dp,
    onNavigateToInstall: () -> Unit,
    onNavigateToUninstall: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val homeLayoutMode = LocalHomeLayoutMode.current
    val isWeavskHome = homeLayoutMode == Config.Value.HOME_LAYOUT_WEAVSK
    var hasStartedLoading by rememberSaveable { mutableStateOf(false) }
    var isMagiskCardExpanded by rememberSaveable { mutableStateOf(false) }
    var isManagerCardExpanded by rememberSaveable { mutableStateOf(false) }
    var isSupportCardExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBarBlurBackdrop(enableBlur, surfaceColor)
    val cardActionEnter = fadeIn(
        animationSpec = tween(durationMillis = 220)
    ) + expandVertically(
        expandFrom = Alignment.Top,
        animationSpec = tween(durationMillis = 260)
    )
    val cardActionExit = fadeOut(
        animationSpec = tween(durationMillis = 180)
    ) + shrinkVertically(
        shrinkTowards = Alignment.Top,
        animationSpec = tween(durationMillis = 220)
    )

    LaunchedEffect(configuration) {
        viewModel.dismissManagerInstallDialog()
    }

    LaunchedEffect(viewModel.appState) {
        if (viewModel.appState == HomeViewModel.State.LOADING ||
            viewModel.appState == HomeViewModel.State.INVALID
        ) {
            isManagerCardExpanded = false
        }
    }

    LaunchedEffect(hasStartedLoading) {
        if (!hasStartedLoading) {
            hasStartedLoading = true
            viewModel.startLoading()
        }
    }

    HomeDialogHost(
        viewModel = viewModel,
        onNavigateToInstall = onNavigateToInstall,
        onNavigateToUninstall = onNavigateToUninstall
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBarBlur(blurBackdrop, surfaceColor),
                color = barBlurContainerColor(blurBackdrop, surfaceColor),
                title = context.getString(CoreR.string.section_home),
                scrollBehavior = scrollBehavior,
                actions = {
                    RebootListPopup(
                        alignment = PopupPositionProvider.Align.TopEnd,
                    )
                }
            )
        },
        popupHost = { }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .attachBarBlurBackdrop(blurBackdrop),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null
            ) {
                item {
                    AnimatedVisibility(
                        visible = viewModel.isNoticeVisible,
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 300)
                        )
                    ) {
                        NoticeCard(
                            onHide = { viewModel.hideNotice() }
                        )
                    }
                }

                if (isWeavskHome) {
                    weavskHomeContent(
                        viewModel = viewModel,
                        magiskInstalledVersion = viewModel.magiskInstalledVersion.getText(context.resources).toString(),
                        isMagiskCardExpanded = isMagiskCardExpanded,
                        onMagiskExpandedChange = { isMagiskCardExpanded = it },
                        isSupportCardExpanded = isSupportCardExpanded,
                        onSupportExpandedChange = { isSupportCardExpanded = it },
                        onNavigateToInstall = onNavigateToInstall,
                        onNavigateToAbout = onNavigateToAbout,
                        cardActionEnter = cardActionEnter,
                        cardActionExit = cardActionExit
                    )
                } else {
                    classicHomeContent(
                        viewModel = viewModel,
                        magiskInstalledVersion = viewModel.magiskInstalledVersion.getText(context.resources).toString(),
                        managerRemoteVersion = viewModel.managerRemoteVersion.getText(context.resources).toString(),
                        isMagiskCardExpanded = isMagiskCardExpanded,
                        onMagiskExpandedChange = { isMagiskCardExpanded = it },
                        isManagerCardExpanded = isManagerCardExpanded,
                        onManagerExpandedChange = { isManagerCardExpanded = it },
                        onNavigateToInstall = onNavigateToInstall,
                        onNavigateToAbout = onNavigateToAbout,
                        cardActionEnter = cardActionEnter,
                        cardActionExit = cardActionExit
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(contentBottomPadding))
                }
            }
        }
    }
}
