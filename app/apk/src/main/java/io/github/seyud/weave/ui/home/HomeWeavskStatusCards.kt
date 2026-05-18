package io.github.seyud.weave.ui.home

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.theme.LocalIsMonetTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.TiltFeedback
import top.yukonga.miuix.kmp.utils.pressable

@Composable
internal fun WeavskMagiskCard(
    magiskState: HomeViewModel.State,
    installedVersion: String,
    expanded: Boolean,
    onCardClick: () -> Unit,
    onInstallClick: () -> Unit,
) {
    val isMonetTheme = LocalIsMonetTheme.current
    val cardState = MagiskCardState.remember(magiskState, expanded)
    val containerColor = when {
        isMonetTheme -> MiuixTheme.colorScheme.secondaryContainer
        else -> MiuixTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when {
        isMonetTheme -> MiuixTheme.colorScheme.onSecondaryContainer
        else -> MiuixTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .then(
                if (isMonetTheme) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = contentColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            ),
        colors = CardDefaults.defaultColors(
            color = containerColor,
            contentColor = contentColor
        ),
        pressFeedbackType = if (cardState.isInteractive) PressFeedbackType.Tilt else PressFeedbackType.None,
        onClick = if (cardState.isInteractive) onCardClick else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeaveCardIcon(
                    isMonetTheme = isMonetTheme,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weave",
                        style = MiuixTheme.textStyles.title3,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = installedVersion,
                        style = MiuixTheme.textStyles.footnote1,
                        color = contentColor.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Medium
                    )
                }

                when (magiskState) {
                    HomeViewModel.State.LOADING -> {
                        CircularProgressIndicator(
                            size = 24.dp,
                            strokeWidth = 2.dp
                        )
                    }

                    else -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            InlineCardActionButton(
                                icon = cardState.actionIcon,
                                text = cardState.actionText,
                                accentColor = contentColor,
                                onPressed = onInstallClick
                            )
                            if (cardState.isInteractive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = MiuixIcons.ChevronForward,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer {
                                            rotationZ = cardState.chevronRotation
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeavskStatusCard(
    appState: HomeViewModel.State,
    installedVersion: String,
    packageName: String,
    progress: Int,
) {
    val context = LocalContext.current
    val systemVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}.0)"

    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .pressable(
                interactionSource = null,
                indication = TiltFeedback(),
                delay = null
            ),
        pressFeedbackType = PressFeedbackType.None
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                WeavskStatusItem(
                    title = context.getString(CoreR.string.home_zygisk_implementation),
                    content = context.getString(
                        if (io.github.seyud.weave.core.Info.isZygiskEnabled) {
                            CoreR.string.home_status_enabled
                        } else {
                            CoreR.string.home_status_disabled
                        }
                    ),
                    bottomPadding = 24.dp
                )
                WeavskStatusItem(
                    title = context.getString(CoreR.string.home_ramdisk_feature),
                    content = context.getString(
                        if (io.github.seyud.weave.core.Info.ramdisk) {
                            CoreR.string.home_status_supported
                        } else {
                            CoreR.string.home_status_unsupported
                        }
                    ),
                    bottomPadding = 24.dp
                )
                WeavskStatusItem(
                    title = context.getString(CoreR.string.home_manager_version),
                    content = installedVersion,
                    bottomPadding = 24.dp
                )
                WeavskStatusItem(
                    title = context.getString(CoreR.string.home_manager_package),
                    content = packageName,
                    bottomPadding = 24.dp
                )
                WeavskStatusItem(
                    title = context.getString(CoreR.string.home_system_version),
                    content = systemVersion,
                    bottomPadding = 0.dp
                )
            }

            if (progress in 1..99) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WeavskStatusItem(
    title: String,
    content: String,
    bottomPadding: Dp,
) {
    Text(
        text = title,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = content,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
