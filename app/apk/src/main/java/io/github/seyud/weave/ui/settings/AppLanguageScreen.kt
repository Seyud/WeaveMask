package io.github.seyud.weave.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.utils.LocaleSetting
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.barBlurContainerColor
import io.github.seyud.weave.ui.util.defaultBarBlur
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.seyud.weave.core.R as CoreR

private data class LanguageOption(
    val index: Int,
    val tag: String,
    val name: String,
)

@Composable
fun AppLanguageScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBarBlurBackdrop(enableBlur, surfaceColor)
    val systemDefaultLabel = stringResource(CoreR.string.system_default)
    val tags = remember { LocaleSetting.available.tags.toList() }
    val localeList = remember(tags, systemDefaultLabel) {
        tags.mapIndexed { index, tag ->
            val name = if (index == 0) {
                systemDefaultLabel
            } else {
                val locale = Locale.forLanguageTag(tag)
                locale.getDisplayName(locale)
            }
            LanguageOption(index = index, tag = tag, name = name)
        }
    }

    var selectedIndex by remember {
        mutableIntStateOf(tags.indexOf(Config.locale).takeIf { it >= 0 } ?: 0)
    }
    var switchingLanguage by remember { mutableStateOf(false) }
    val languageApplyDelay = remember {
        if (LocaleSetting.useLocaleManager) 0L else 520L
    }

    val currentSystemLanguageTag = remember { getSystemLocale().toLanguageTag() }
    val currentLocale = LocaleSetting.instance.currentLocale
    val nameCollator = remember(currentLocale) { Collator.getInstance(currentLocale) }

    val suggestedIndexes = remember(localeList, tags, currentSystemLanguageTag) {
        buildList {
            add(0)
            val localeIndex = tags.indexOf(currentSystemLanguageTag)
            if (localeIndex > 0) {
                add(localeIndex)
            }
        }.distinct()
    }

    val suggestedLanguages = remember(localeList, suggestedIndexes) {
        localeList.filter { it.index in suggestedIndexes }
    }

    val allLanguages = remember(localeList, suggestedIndexes, nameCollator) {
        localeList
            .filterNot { it.index in suggestedIndexes }
            .sortedWith(languageOptionComparator(nameCollator))
    }

    val onSelectLanguage: (Int) -> Unit = { index ->
        if (!switchingLanguage && selectedIndex != index) {
            switchingLanguage = true
            selectedIndex = index
            onNavigateBack()
            scope.launch {
                if (languageApplyDelay > 0) {
                    delay(languageApplyDelay)
                }
                context.findActivity()?.intent?.putExtra(MainActivity.EXTRA_START_MAIN_TAB, 3)
                Config.locale = tags[index]
            }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBarBlur(blurBackdrop, surfaceColor),
                color = barBlurContainerColor(blurBackdrop, surfaceColor),
                title = stringResource(CoreR.string.app_language),
                largeTitle = stringResource(CoreR.string.app_language),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    // Keep action width symmetric with navigation icon so collapsed title stays centered.
                    IconButton(
                        onClick = {},
                        enabled = false,
                    ) {}
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .attachBarBlurBackdrop(blurBackdrop),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    SmallTitle(text = stringResource(CoreR.string.settings_language_suggested))
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        suggestedLanguages.forEach { option ->
                            CheckboxPreference(
                                title = option.name,
                                checked = selectedIndex == option.index,
                                onCheckedChange = { checked ->
                                    if (checked && !switchingLanguage) {
                                        onSelectLanguage(option.index)
                                    }
                                },
                                enabled = !switchingLanguage,
                            )
                        }
                    }
                }

                item {
                    SmallTitle(text = stringResource(CoreR.string.settings_language_all))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        allLanguages.forEach { option ->
                            CheckboxPreference(
                                title = option.name,
                                checked = selectedIndex == option.index,
                                onCheckedChange = { checked ->
                                    if (checked && !switchingLanguage) {
                                        onSelectLanguage(option.index)
                                    }
                                },
                                enabled = !switchingLanguage,
                            )
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun languageOptionComparator(
    nameCollator: Collator,
): Comparator<LanguageOption> = Comparator { left, right ->
    val leftRank = chineseSortRank(left.tag)
    val rightRank = chineseSortRank(right.tag)
    when {
        leftRank != rightRank -> leftRank - rightRank
        else -> {
            val nameCompare = nameCollator.compare(left.name, right.name)
            if (nameCompare != 0) {
                nameCompare
            } else {
                left.tag.compareTo(right.tag)
            }
        }
    }
}

private fun chineseSortRank(tag: String): Int = when (tag) {
    "" -> 3
    else -> {
        val locale = Locale.forLanguageTag(tag)
        if (!locale.language.equals("zh", ignoreCase = true)) {
            3
        } else {
            val script = locale.script
            val region = locale.country
            when {
                tag.equals("zh-CN", ignoreCase = true) ||
                    script.equals("Hans", ignoreCase = true) ||
                    region.equals("CN", ignoreCase = true) ||
                    region.equals("SG", ignoreCase = true) -> 0
                tag.equals("zh-TW", ignoreCase = true) ||
                    script.equals("Hant", ignoreCase = true) ||
                    region.equals("TW", ignoreCase = true) ||
                    region.equals("HK", ignoreCase = true) ||
                    region.equals("MO", ignoreCase = true) -> 1
                else -> 2
            }
        }
    }
}

private fun getSystemLocale(): Locale {
    // minSdk 24：Configuration#getLocales 一定可用，无需再判断 SDK_INT
    return Resources.getSystem().configuration.locales[0]
}
