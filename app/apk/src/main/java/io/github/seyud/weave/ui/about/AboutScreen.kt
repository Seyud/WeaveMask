package io.github.seyud.weave.ui.about

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.seyud.weave.core.BuildConfig
import io.github.seyud.weave.core.R as CoreR
import kotlinx.coroutines.flow.onEach
import io.github.seyud.weave.ui.about.blend.ColorBlendToken
import io.github.seyud.weave.ui.about.effect.BgEffectBackground
import io.github.seyud.weave.ui.home.DeveloperItem
import io.github.seyud.weave.ui.home.IconLink
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onLinkPressed: (String) -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AboutScreenBlend(
            onNavigateBack = onNavigateBack,
            onLinkPressed = onLinkPressed,
        )
    } else {
        AboutScreenLegacy(
            onNavigateBack = onNavigateBack,
            onLinkPressed = onLinkPressed,
        )
    }
}

@Composable
private fun AboutScreenLegacy(
    onNavigateBack: () -> Unit,
    onLinkPressed: (String) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val appName = remember { context.applicationInfo.loadLabel(context.packageManager).toString() }
    val versionText = remember { "${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(CoreR.string.about_page_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = 180f }
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            ),
            overscrollEffect = null
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = CoreR.drawable.ic_app_logo),
                        modifier = Modifier.size(80.dp),
                        contentDescription = appName
                    )
                    Text(
                        text = appName,
                        fontSize = MiuixTheme.textStyles.title2.fontSize,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = versionText,
                        fontSize = MiuixTheme.textStyles.subtitle.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SmallTitle(stringResource(CoreR.string.about_section_title)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = stringResource(CoreR.string.about_view_source_code),
                        summary = stringResource(CoreR.string.about_view_source_code_summary),
                        onClick = { onLinkPressed("https://github.com/Seyud/WeaveMask") }
                    )
                    ArrowPreference(
                        title = stringResource(CoreR.string.about_join_telegram),
                        onClick = { onLinkPressed("tg://resolve?domain=WeaveMask") }
                    )
                    ArrowPreference(
                        title = stringResource(CoreR.string.about_join_qq),
                        onClick = { onLinkPressed("https://qun.qq.com/universal-share/share?ac=1&authKey=2ORH6ytwk71mP%2FdaOhEoBuyr04XdfNI45f19OEycADR5%2Bo%2FLPH3kS%2FnHRUZuxzob&busi_data=eyJncm91cENvZGUiOiIxMDkwMDk1NzQ5IiwidG9rZW4iOiJvcWN1WlFadE5DdmRXU0R6bzdiWVpxeTZOUFQxY2ZuT1BuWDcvS0RySEZQYUV0a0t1MVNOcVVuSVdUdTVHQW9IIiwidWluIjoiMTEwNTc4MzAzMyJ9&data=uw11vDVnwRG1RrwKJtLsu55rKfiPn3DgchjhFFMtONvXMcqT1OhO_JuO2Lb73OVCPPIUKAitdy5FH1uRQp0E8Q&svctype=4&tempid=h5_group_info") }
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    DeveloperSection(onLinkPressed = onLinkPressed)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AboutScreenBlend(
    onNavigateBack: () -> Unit,
    onLinkPressed: (String) -> Unit,
) {
    val context = LocalContext.current
    val appName = remember { context.applicationInfo.loadLabel(context.packageManager).toString() }
    val versionText = remember { "${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})" }

    val lazyListState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var logoHeightPx by remember { mutableFloatStateOf(0f) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0f) 0f
            else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(CoreR.string.about_page_title),
                scrollBehavior = topAppBarScrollBehavior,
                color = MiuixTheme.colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = 180f }
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        AboutContentBody(
            padding = innerPadding,
            appName = appName,
            versionText = versionText,
            lazyListState = lazyListState,
            scrollProgress = scrollProgress,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            onLogoHeightChanged = { logoHeightPx = it.toFloat() },
            onLinkPressed = onLinkPressed,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AboutContentBody(
    padding: PaddingValues,
    appName: String,
    versionText: String,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    scrollProgress: Float,
    topAppBarScrollBehavior: ScrollBehavior,
    onLogoHeightChanged: (Int) -> Unit,
    onLinkPressed: (String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val isDark = isSystemInDarkTheme()

    var showTextureSet by remember { mutableStateOf(false) }
    val dynamicBackground = remember { mutableStateOf(isRuntimeShaderSupported()) }
    val effectBackground = remember { mutableStateOf(isRuntimeShaderSupported()) }
    var isOs3Effect by remember { mutableStateOf(true) }

    val backdrop = rememberLayerBackdrop()

    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var iconY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    var iconProgress by remember { mutableFloatStateOf(0f) }
    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    if (iconProgress != 1f) iconProgress = 1f
                    if (projectNameProgress != 1f) projectNameProgress = 1f
                    if (versionCodeProgress != 1f) versionCodeProgress = 1f
                    return@onEach
                }

                if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                    initialLogoAreaY = logoAreaY
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY

                val stage1TotalLength = refLogoAreaY - versionCodeY
                val stage2TotalLength = versionCodeY - projectNameY
                val stage3TotalLength = projectNameY - iconY

                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress = ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay).coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                projectNameProgress = ((offset.toFloat() - stage1TotalLength) / stage2TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                iconProgress = ((offset.toFloat() - stage1TotalLength - stage2TotalLength) / stage3TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
            }
            .collect { }
    }

    val displayCutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()

    val listContentPadding = PaddingValues(
        start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateLeftPadding(layoutDirection),
        top = padding.calculateTopPadding(),
        end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateRightPadding(layoutDirection),
        bottom = padding.calculateBottomPadding()
    )

    val logoPadding = PaddingValues(
        top = padding.calculateTopPadding() + 40.dp,
        start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateLeftPadding(layoutDirection),
        end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateRightPadding(layoutDirection)
    )

    val blurEnable = remember { isRenderEffectSupported() }

    BgEffectBackground(
        dynamicBackground = dynamicBackground.value,
        isOs3Effect = isOs3Effect,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = effectBackground.value,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection)
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (iconY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        iconY = y + size.height
                    },
            ) {
                Image(
                    painter = painterResource(id = CoreR.drawable.ic_app_logo),
                    modifier = Modifier
                        .requiredSize(160.dp)
                        .textureBlur(
                            backdrop = backdrop,
                            shape = SmoothRoundedCornerShape(16.dp),
                            blurRadius = 200f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = logoBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = blurEnable,
                        ),
                    contentDescription = null
                )
            }

            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .onGloballyPositioned { coordinates ->
                        if (projectNameY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        projectNameY = y + size.height
                    }
                    .graphicsLayer {
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .textureBlur(
                        backdrop = backdrop,
                        shape = SmoothRoundedCornerShape(16.dp),
                        blurRadius = 150f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurColors(blendColors = logoBlend),
                        contentBlendMode = ComposeBlendMode.DstIn,
                        enabled = blurEnable,
                    ),
                text = appName,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionCodeY = y + size.height
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    text = versionText,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = listContentPadding,
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() - listContentPadding.calculateTopPadding() + 126.dp,
                        )
                        .onSizeChanged { size ->
                            onLogoHeightChanged(size.height)
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                showTextureSet = true
                            }
                        }
                        .onGloballyPositioned { coordinates ->
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            logoAreaY = y + size.height
                        },
                    contentAlignment = Alignment.TopCenter,
                    content = { }
                )
            }

            item(key = "about_content") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = listContentPadding.calculateBottomPadding()),
                ) {
                    SmallTitle(stringResource(CoreR.string.about_section_title))

                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .textureBlur(
                                backdrop = backdrop,
                                shape = SmoothRoundedCornerShape(16.dp),
                                blurRadius = 60f,
                                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                colors = BlurColors(
                                    blendColors = if (isDark) ColorBlendToken.Overlay_Extra_Thin_Dark else ColorBlendToken.Pured_Regular_Light,
                                ),
                                enabled = blurEnable,
                            ),
                        colors = CardDefaults.defaultColors(
                            if (blurEnable) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        ArrowPreference(
                            title = stringResource(CoreR.string.about_view_source_code),
                            summary = stringResource(CoreR.string.about_view_source_code_summary),
                            onClick = { onLinkPressed("https://github.com/Seyud/WeaveMask") }
                        )
                        ArrowPreference(
                            title = stringResource(CoreR.string.about_join_telegram),
                            onClick = { onLinkPressed("tg://resolve?domain=WeaveMask") }
                        )
                        ArrowPreference(
                            title = stringResource(CoreR.string.about_join_qq),
                            onClick = { onLinkPressed("https://qun.qq.com/universal-share/share?ac=1&authKey=2ORH6ytwk71mP%2FdaOhEoBuyr04XdfNI45f19OEycADR5%2Bo%2FLPH3kS%2FnHRUZuxzob&busi_data=eyJncm91cENvZGUiOiIxMDkwMDk1NzQ5IiwidG9rZW4iOiJvcWN1WlFadE5DdmRXU0R6bzdiWVpxeTZOUFQxY2ZuT1BuWDcvS0RySEZQYUV0a0t1MVNOcVVuSVdUdTVHQW9IIiwidWluIjoiMTEwNTc4MzAzMyJ9&data=uw11vDVnwRG1RrwKJtLsu55rKfiPn3DgchjhFFMtONvXMcqT1OhO_JuO2Lb73OVCPPIUKAitdy5FH1uRQp0E8Q&svctype=4&tempid=h5_group_info") }
                        )
                    }
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .textureBlur(
                                backdrop = backdrop,
                                shape = SmoothRoundedCornerShape(16.dp),
                                blurRadius = 60f,
                                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                colors = BlurColors(
                                    blendColors = if (isDark) ColorBlendToken.Overlay_Extra_Thin_Dark else ColorBlendToken.Pured_Regular_Light,
                                ),
                                enabled = blurEnable,
                            ),
                        colors = CardDefaults.defaultColors(
                            if (blurEnable) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        DeveloperSection(onLinkPressed = onLinkPressed)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    OverlayBottomSheet(
        show = showTextureSet,
        title = "Texture Set",
        onDismissRequest = { showTextureSet = false },
        insideMargin = DpSize(0.dp, 0.dp),
    ) {
        LazyColumn {
            item {
                val effectVariantOptions = listOf("OS2", "OS3")
                val selectedIndex = if (isOs3Effect) 1 else 0
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = SmoothRoundedCornerShape(12.dp),
                    color = MiuixTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Effect Variant: ${effectVariantOptions[selectedIndex]}",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            effectVariantOptions.forEachIndexed { index, label ->
                                Card(
                                    onClick = { isOs3Effect = index == 1 },
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.defaultColors(
                                        if (selectedIndex == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant,
                                        Color.Transparent,
                                    ),
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        textAlign = TextAlign.Center,
                                        color = if (selectedIndex == index) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = SmoothRoundedCornerShape(12.dp),
                    color = MiuixTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Dynamic Background",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Switch(
                                checked = dynamicBackground.value,
                                onCheckedChange = { dynamicBackground.value = it }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Effect Background",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Switch(
                                checked = effectBackground.value,
                                onCheckedChange = { effectBackground.value = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperSection(onLinkPressed: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        DeveloperLinksRow(
            handle = DeveloperItem.John.handle,
            links = DeveloperItem.John.items,
            onLinkPressed = onLinkPressed
        )
        DeveloperLinksRow(
            handle = DeveloperItem.Vvb.handle,
            links = DeveloperItem.Vvb.items,
            onLinkPressed = onLinkPressed
        )
        DeveloperLinksRow(
            handle = DeveloperItem.YU.handle,
            links = DeveloperItem.YU.items,
            onLinkPressed = onLinkPressed
        )
        DeveloperLinksRow(
            handle = DeveloperItem.Seyud.handle,
            links = DeveloperItem.Seyud.items,
            onLinkPressed = onLinkPressed
        )
        DeveloperLinksRow(
            handle = DeveloperItem.Rikka.handle,
            links = DeveloperItem.Rikka.items,
            onLinkPressed = onLinkPressed
        )
        DeveloperLinksRow(
            handle = DeveloperItem.Canyie.handle,
            links = DeveloperItem.Canyie.items,
            onLinkPressed = onLinkPressed
        )
    }
}

@Composable
private fun DeveloperLinksRow(
    handle: String,
    links: List<IconLink>,
    onLinkPressed: (String) -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = handle,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(100.dp)
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            links.forEach { link ->
                val iconRes = when (link) {
                    is IconLink.Patreon -> CoreR.drawable.ic_patreon
                    is IconLink.PayPal -> CoreR.drawable.ic_paypal
                    is IconLink.Twitter -> CoreR.drawable.ic_twitter
                    is IconLink.Github -> CoreR.drawable.ic_github
                    is IconLink.Sponsor -> CoreR.drawable.ic_favorite
                }

                IconButton(
                    onClick = { onLinkPressed(link.link) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = context.getString(link.title),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
