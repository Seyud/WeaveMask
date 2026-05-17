package io.github.seyud.weave.ui.colorpalette

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.App as CoreApp
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.ui.settings.ScaleDialog
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.theme.MonetPresetPalette
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import io.github.seyud.weave.core.R as CoreR

@Composable
fun ColorPaletteScreen(
    onNavigateBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val surfaceColor = colorScheme.surface
    val blurBackdrop = rememberBarBlurBackdrop(enableBlur, surfaceColor)

    var themeMode by rememberSaveable { mutableIntStateOf(Config.colorMode) }
    var enableBlurState by rememberSaveable { mutableStateOf(Config.enableBlur) }
    var enableFloatingBottomBar by rememberSaveable { mutableStateOf(Config.enableFloatingBottomBar) }
    var enableFloatingBottomBarBlur by rememberSaveable { mutableStateOf(Config.enableFloatingBottomBarBlur) }
    var sliderValue by rememberSaveable { mutableFloatStateOf(Config.pageScale) }
    val showScaleDialog = rememberSaveable { mutableStateOf(false) }

    val isMonet = themeMode in 3..5

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBarBlur(blurBackdrop, surfaceColor),
                color = barBlurContainerColor(blurBackdrop, surfaceColor),
                title = stringResource(CoreR.string.settings_theme),
                navigationIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground
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
                .fillMaxHeight()
                .attachBarBlurBackdrop(blurBackdrop)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Theme dropdown
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val themeItems = listOf(
                            stringResource(id = CoreR.string.settings_theme_mode_system),
                            stringResource(id = CoreR.string.settings_theme_mode_light),
                            stringResource(id = CoreR.string.settings_theme_mode_dark),
                            stringResource(id = CoreR.string.settings_theme_mode_monet_system),
                            stringResource(id = CoreR.string.settings_theme_mode_monet_light),
                            stringResource(id = CoreR.string.settings_theme_mode_monet_dark),
                        )
                        OverlayDropdownPreference(
                            title = stringResource(id = CoreR.string.settings_theme),
                            summary = stringResource(id = CoreR.string.settings_theme_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = CoreR.string.settings_theme),
                                    tint = colorScheme.onBackground
                                )
                            },
                            items = themeItems,
                            selectedIndex = themeMode.coerceIn(0, 5),
                            onSelectedIndexChange = { index ->
                                Config.colorMode = index
                                themeMode = index
                            }
                        )

                        AnimatedVisibility(visible = isMonet) {
                            val keyColorDefault = stringResource(CoreR.string.settings_key_color_default)
                            val colorRed = stringResource(CoreR.string.color_red)
                            val colorPink = stringResource(CoreR.string.color_pink)
                            val colorPurple = stringResource(CoreR.string.color_purple)
                            val colorDeepPurple = stringResource(CoreR.string.color_deep_purple)
                            val colorIndigo = stringResource(CoreR.string.color_indigo)
                            val colorBlue = stringResource(CoreR.string.color_blue)
                            val colorCyan = stringResource(CoreR.string.color_cyan)
                            val colorTeal = stringResource(CoreR.string.color_teal)
                            val colorGreen = stringResource(CoreR.string.color_green)
                            val colorYellow = stringResource(CoreR.string.color_yellow)
                            val colorAmber = stringResource(CoreR.string.color_amber)
                            val colorOrange = stringResource(CoreR.string.color_orange)
                            val colorBrown = stringResource(CoreR.string.color_brown)
                            val colorBlueGrey = stringResource(CoreR.string.color_blue_grey)
                            val colorSakura = stringResource(CoreR.string.color_sakura)

                            val colorItems = remember(
                                keyColorDefault,
                                colorRed,
                                colorPink,
                                colorPurple,
                                colorDeepPurple,
                                colorIndigo,
                                colorBlue,
                                colorCyan,
                                colorTeal,
                                colorGreen,
                                colorYellow,
                                colorAmber,
                                colorOrange,
                                colorBrown,
                                colorBlueGrey,
                                colorSakura,
                            ) {
                                listOf(
                                    keyColorDefault,
                                    colorRed,
                                    colorPink,
                                    colorPurple,
                                    colorDeepPurple,
                                    colorIndigo,
                                    colorBlue,
                                    colorCyan,
                                    colorTeal,
                                    colorGreen,
                                    colorYellow,
                                    colorAmber,
                                    colorOrange,
                                    colorBrown,
                                    colorBlueGrey,
                                    colorSakura,
                                )
                            }
                            val colorValues = remember {
                                listOf(0) + MonetPresetPalette.presetKeyColors
                            }
                            var keyColorIndex by rememberSaveable {
                                mutableIntStateOf(
                                    colorValues.indexOf(Config.keyColor).takeIf { it >= 0 } ?: 0,
                                )
                            }

                            OverlayDropdownPreference(
                                title = stringResource(id = CoreR.string.settings_key_color),
                                summary = stringResource(id = CoreR.string.settings_key_color_summary),
                                items = colorItems,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Colorize,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = stringResource(id = CoreR.string.settings_key_color),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                selectedIndex = keyColorIndex,
                                onSelectedIndexChange = { index ->
                                    Config.keyColor = colorValues[index]
                                    keyColorIndex = index
                                }
                            )
                        }
                    }

                    // Blur / Floating bar / Glass section
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SwitchPreference(
                                title = stringResource(id = CoreR.string.settings_enable_blur),
                                summary = stringResource(id = CoreR.string.settings_enable_blur_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.WaterDrop,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = stringResource(id = CoreR.string.settings_enable_blur),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = enableBlurState,
                                onCheckedChange = {
                                    Config.enableBlur = it
                                    enableBlurState = Config.enableBlur
                                    enableFloatingBottomBarBlur = Config.enableFloatingBottomBarBlur
                                }
                            )
                        }

                        SwitchPreference(
                            title = stringResource(id = CoreR.string.settings_floating_bottom_bar),
                            summary = stringResource(id = CoreR.string.settings_floating_bottom_bar_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.CallToAction,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = CoreR.string.settings_floating_bottom_bar),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = enableFloatingBottomBar,
                            onCheckedChange = {
                                Config.enableFloatingBottomBar = it
                                enableFloatingBottomBar = it
                            }
                        )

                        AnimatedVisibility(
                            visible = enableFloatingBottomBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                        ) {
                            SwitchPreference(
                                title = stringResource(id = CoreR.string.settings_enable_glass),
                                summary = stringResource(id = CoreR.string.settings_enable_glass_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.BlurOn,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = stringResource(id = CoreR.string.settings_enable_glass),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = enableFloatingBottomBarBlur,
                                onCheckedChange = {
                                    Config.enableFloatingBottomBarBlur = it
                                    enableFloatingBottomBarBlur = Config.enableFloatingBottomBarBlur
                                    enableBlurState = Config.enableBlur
                                }
                            )
                        }
                    }

                    // Predictive back / Page scale section
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val context = LocalContext.current
                            val activity = context as Activity
                            var enablePredictiveBack by rememberSaveable {
                                mutableStateOf(Config.enablePredictiveBack)
                            }
                            SwitchPreference(
                                title = stringResource(id = CoreR.string.settings_enable_predictive_back),
                                summary = stringResource(id = CoreR.string.settings_enable_predictive_back_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Palette,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = stringResource(id = CoreR.string.settings_enable_predictive_back),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = enablePredictiveBack,
                                onCheckedChange = {
                                    Config.enablePredictiveBack = it
                                    enablePredictiveBack = it
                                    CoreApp.setEnableOnBackInvokedCallback(
                                        context.applicationInfo,
                                        it
                                    )
                                    activity.recreate()
                                }
                            )
                        }

                        ArrowPreference(
                            title = stringResource(id = CoreR.string.settings_page_scale),
                            summary = stringResource(id = CoreR.string.settings_page_scale_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.AspectRatio,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = CoreR.string.settings_page_scale),
                                    tint = colorScheme.onBackground
                                )
                            },
                            endActions = {
                                Text(
                                    text = "${(sliderValue * 100).toInt()}%",
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = { showScaleDialog.value = !showScaleDialog.value },
                            holdDownState = showScaleDialog.value,
                            bottomAction = {
                                Slider(
                                    value = sliderValue,
                                    onValueChange = { sliderValue = it },
                                    onValueChangeFinished = {
                                        Config.pageScale = sliderValue
                                    },
                                    valueRange = 0.8f..1.1f,
                                    showKeyPoints = true,
                                    keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f),
                                    magnetThreshold = 0.01f,
                                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                )
                            },
                        )
                        ScaleDialog(
                            showDialog = showScaleDialog,
                            scaleState = { Config.pageScale },
                            onScaleChange = {
                                Config.pageScale = it
                                sliderValue = it
                            },
                        )
                    }
                }
                item {
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() +
                                    12.dp
                        )
                    )
                }
            }
        }
    }
}
