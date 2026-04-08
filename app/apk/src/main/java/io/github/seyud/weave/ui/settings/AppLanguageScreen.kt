package io.github.seyud.weave.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.utils.LocaleSetting
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.Collator
import java.util.Locale
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.core.R as CoreR
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class LanguageOption(
    val index: Int,
    val name: String,
)

@Composable
fun AppLanguageScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDefaultLabel = context.getString(CoreR.string.system_default)
    val tags = remember { LocaleSetting.available.tags.toList() }
    val localeList = remember(tags, systemDefaultLabel) {
        tags.mapIndexed { index, tag ->
            val name = if (index == 0) {
                systemDefaultLabel
            } else {
                val locale = Locale.forLanguageTag(tag)
                locale.getDisplayName(locale)
            }
            LanguageOption(index = index, name = name)
        }
    }

    var selectedIndex by remember {
        mutableIntStateOf(tags.indexOf(Config.locale).takeIf { it >= 0 } ?: 0)
    }
    var switchingLanguage by remember { mutableStateOf(false) }
    val languageApplyDelay = remember {
        if (LocaleSetting.useLocaleManager) 0L else 520L
    }

    val currentSystemLanguageTag = LocaleSetting.instance.currentLocale.toLanguageTag()
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

    val allLanguages = remember(localeList, suggestedIndexes) {
        localeList
            .filterNot { it.index in suggestedIndexes }
            .sortedWith { left, right ->
                nameCollator.compare(left.name, right.name)
            }
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
        topBar = {
            TopAppBar(
                title = context.getString(CoreR.string.app_language),
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
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        content = { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
            ) {
                item {
                    SmallTitle(text = context.getString(CoreR.string.settings_language_suggested))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                    SmallTitle(text = context.getString(CoreR.string.settings_language_all))
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
        },
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
