package io.github.seyud.weave.ui.flash

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.R as CoreR
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import io.github.seyud.weave.arch.BaseViewModel.BaseEvent
import io.github.seyud.weave.arch.UiEvent
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.ui.terminal.TerminalScreen
import io.github.seyud.weave.ui.component.ObserveAsEvents
import io.github.seyud.weave.ui.component.showSnackbarEvent
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun FlashScreen(
    viewModel: FlashViewModel,
    action: String,
    additionalData: List<Uri>,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    /** 刷写期间禁止边缘横移返回，避免误滑中断写盘 */
    onBackGestureEnabledChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf(viewModel.state.value ?: FlashViewModel.State.FLASHING) }
    val isFlashing = state == FlashViewModel.State.FLASHING

    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = Observer<FlashViewModel.State> {
            state = it
        }
        viewModel.state.observe(lifecycleOwner, observer)
        onDispose {
            viewModel.state.removeObserver(observer)
        }
    }

    // FlashViewModel 现在是路由条目作用域，Activity 不再统一观察它；把 uiEvents 转给
    // Activity 的 dispatcher，权限申请等 UiEvent 仍由 Activity 执行。
    val mainActivity = context as? MainActivity
    DisposableEffect(viewModel, lifecycleOwner, mainActivity) {
        val uiObserver = Observer<UiEvent> { event -> mainActivity?.onUiEventDispatched(event) }
        viewModel.uiEvents.observe(lifecycleOwner, uiObserver)
        onDispose {
            viewModel.uiEvents.removeObserver(uiObserver)
        }
    }

    BackHandler(enabled = isFlashing) {}
    // 返回键之外，miuix-nav 的边缘横移手势也要一起关掉
    LaunchedEffect(isFlashing) {
        onBackGestureEnabledChange(!isFlashing)
    }

    LaunchedEffect(Unit) {
        viewModel.prepareForCompose(action = action, uris = additionalData)
        viewModel.startFlashing()
    }

    val scope = rememberCoroutineScope()
    ObserveAsEvents(viewModel.baseEvent) { event ->
        when (event) {
            is BaseEvent.ShowSnackbar -> scope.launch {
                snackbarHostState.showSnackbarEvent(
                    message = event.message.getText(resources),
                    duration = event.duration,
                )
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = stringResource(CoreR.string.flash_screen_title),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isFlashing) onNavigateBack()
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveLogForCompose(context) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlashStateCard(state = state)
                    Spacer(modifier = Modifier.height(8.dp))
                    // 模块安装时先出示 WeaveMask 字符画：按屏幕宽度等比缩放，整幅都能露出来
                    if (action == Const.Value.FLASH_ZIP) {
                        ModuleInstallBanner()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // 终端视图：安装器（PTY）与 MagiskInstaller（逐行）都写进同一个终端
                    TerminalScreen(
                        emulator = viewModel.emulator,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onResized = viewModel::onTerminalResized,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (state == FlashViewModel.State.SUCCESS && viewModel.showReboot) {
                    Button(
                        onClick = { viewModel.restartPressed() },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(CoreR.string.reboot),
                            color = MiuixTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun FlashStateCard(state: FlashViewModel.State) {
    val text = when (state) {
        FlashViewModel.State.FLASHING -> stringResource(CoreR.string.flashing)
        FlashViewModel.State.SUCCESS -> stringResource(CoreR.string.done)
        FlashViewModel.State.FAILED -> stringResource(CoreR.string.failure)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state == FlashViewModel.State.FLASHING) {
                CircularProgressIndicator(
                    size = 18.dp,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = when (state) {
                    FlashViewModel.State.SUCCESS -> MiuixTheme.colorScheme.primary
                    FlashViewModel.State.FAILED -> MiuixTheme.colorScheme.error
                    FlashViewModel.State.FLASHING -> Color.Unspecified
                }
            )
        }
    }
}
