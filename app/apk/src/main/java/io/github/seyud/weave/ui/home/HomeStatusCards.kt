package io.github.seyud.weave.ui.home

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.R
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.theme.LocalIsMonetTheme
import androidx.compose.ui.res.painterResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.TiltFeedback
import top.yukonga.miuix.kmp.utils.pressable

@Composable
internal fun NoticeCard(
    onHide: () -> Unit,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .pressable(
                interactionSource = null,
                indication = TiltFeedback(),
                delay = null
            ),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primary,
            contentColor = MiuixTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(CoreR.string.home_notice_content),
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onPrimary,
                style = MiuixTheme.textStyles.body1
            )
            TextButton(
                text = context.getString(CoreR.string.hide),
                onClick = onHide,
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.onPrimary,
                    color = Color.Transparent
                )
            )
        }
    }
}

@Composable
internal fun InlineCardActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accentColor: Color,
    onPressed: () -> Unit,
) {
    Surface(
        onClick = onPressed,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = accentColor
            )
        }
    }
}

@Composable
internal fun InstallActionButton(
    appState: HomeViewModel.State,
    matchUninstallMetrics: Boolean = false,
    usePrimaryBlockStyleWhenNonMonet: Boolean = false,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val isMonetTheme = LocalIsMonetTheme.current
    val weaveAccentColor = if (isMonetTheme) {
        MiuixTheme.colorScheme.primary
    } else {
        colorResource(id = CoreR.color.weave_brand_main)
    }
    val icon = if (appState == HomeViewModel.State.OUTDATED) MiuixIcons.Update else MiuixIcons.Download
    val text = if (appState == HomeViewModel.State.OUTDATED) {
        context.getString(CoreR.string.update)
    } else {
        context.getString(CoreR.string.install)
    }
    val buttonColors = if (appState == HomeViewModel.State.OUTDATED) {
        ButtonDefaults.buttonColorsPrimary()
    } else {
        ButtonDefaults.buttonColors()
    }
    val iconTint = if (appState == HomeViewModel.State.OUTDATED) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSecondaryVariant
    }

    if (matchUninstallMetrics) {
        val isWeaveBrandAccent = !isMonetTheme
        val usePrimaryBlockStyle = usePrimaryBlockStyleWhenNonMonet && isWeaveBrandAccent
        val containerColor = if (usePrimaryBlockStyle) {
            MiuixTheme.colorScheme.primary
        } else if (appState == HomeViewModel.State.OUTDATED) {
            if (isWeaveBrandAccent) weaveAccentColor else MiuixTheme.colorScheme.primary
        } else {
            if (isWeaveBrandAccent) weaveAccentColor.copy(alpha = 0.14f) else MiuixTheme.colorScheme.secondaryContainer
        }
        val buttonIconTint = if (usePrimaryBlockStyle) {
            MiuixTheme.colorScheme.onPrimary
        } else if (isWeaveBrandAccent) {
            if (appState == HomeViewModel.State.OUTDATED) Color.White else weaveAccentColor
        } else {
            iconTint
        }
        val border = if (usePrimaryBlockStyle) {
            BorderStroke(1.dp, MiuixTheme.colorScheme.onPrimary)
        } else {
            null
        }

        Surface(
            onClick = onClick,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
            color = containerColor,
            border = border,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = buttonIconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = text,
                    style = MiuixTheme.textStyles.body2,
                    color = buttonIconTint
                )
            }
        }
        return
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        colors = buttonColors
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = iconTint
        )
    }
}

@Composable
internal fun UninstallButton(
    text: String? = null,
    onPressed: () -> Unit,
) {
    val context = LocalContext.current
    val buttonText = text ?: context.getString(CoreR.string.uninstall_magisk_title)

    Surface(
        onClick = onPressed,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        color = MiuixTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MiuixTheme.colorScheme.error),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = buttonText,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
internal fun ZygiskCard(
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val statusColor = if (isEnabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainer
    val cardModifier = modifier
        .pressable(
            interactionSource = null,
            indication = TiltFeedback(),
            delay = null
        )

    Card(modifier = cardModifier, pressFeedbackType = PressFeedbackType.None) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Pin,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = context.getString(CoreR.string.zygisk),
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = context.getString(
                    if (isEnabled) CoreR.string.home_status_enabled else CoreR.string.home_status_disabled
                ),
                style = MiuixTheme.textStyles.body2,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun RamdiskCard(
    isAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val statusColor = if (isAvailable) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainer
    val cardModifier = modifier
        .pressable(
            interactionSource = null,
            indication = TiltFeedback(),
            delay = null
        )

    Card(modifier = cardModifier, pressFeedbackType = PressFeedbackType.None) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Backup,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ramdisk",
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = context.getString(
                    if (isAvailable) CoreR.string.home_status_supported else CoreR.string.home_status_unsupported
                ),
                style = MiuixTheme.textStyles.body2,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun ManagerCard(
    appState: HomeViewModel.State,
    remoteVersion: String,
    installedVersion: String,
    packageName: String,
    progress: Int,
    expanded: Boolean,
    onCardClick: () -> Unit,
    onInstallClick: () -> Unit,
) {
    val context = LocalContext.current
    val isInteractive = appState == HomeViewModel.State.OUTDATED ||
        appState == HomeViewModel.State.UP_TO_DATE
    val actionLabel = when (appState) {
        HomeViewModel.State.OUTDATED -> context.getString(CoreR.string.update)
        HomeViewModel.State.UP_TO_DATE -> context.getString(CoreR.string.install)
        HomeViewModel.State.INVALID -> context.getString(CoreR.string.no_connection)
        HomeViewModel.State.LOADING -> context.getString(CoreR.string.loading)
    }
    val actionIcon = when (appState) {
        HomeViewModel.State.OUTDATED -> MiuixIcons.Update
        else -> MiuixIcons.Download
    }
    val actionColor = when (appState) {
        HomeViewModel.State.INVALID -> MiuixTheme.colorScheme.error
        HomeViewModel.State.LOADING -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        else -> MiuixTheme.colorScheme.primary
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "managerCardChevronRotation"
    )

    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .then(
                if (isInteractive) {
                    Modifier
                } else {
                    Modifier.pressable(
                        interactionSource = null,
                        indication = TiltFeedback(),
                        delay = null
                    )
                }
            ),
        pressFeedbackType = if (isInteractive) PressFeedbackType.Tilt else PressFeedbackType.None,
        onClick = if (isInteractive) onCardClick else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_manager),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "APP",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (appState == HomeViewModel.State.LOADING) {
                    CircularProgressIndicator(
                        size = 24.dp,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (isInteractive) {
                            InlineCardActionButton(
                                icon = actionIcon,
                                text = actionLabel,
                                accentColor = MiuixTheme.colorScheme.primary,
                                onPressed = onInstallClick
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = MiuixIcons.ChevronForward,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer {
                                        rotationZ = chevronRotation
                                    }
                            )
                        } else {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                tint = actionColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = actionLabel,
                                color = actionColor,
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                HomeItemRow(
                    label = context.getString(CoreR.string.home_latest_version),
                    value = remoteVersion
                )
                HomeItemRow(
                    label = context.getString(CoreR.string.home_installed_version),
                    value = installedVersion
                )
                HomeItemRow(
                    label = context.getString(CoreR.string.home_package),
                    value = packageName
                )
            }

            if (progress > 0 && progress < 100) {
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
private fun HomeItemRow(
    label: String,
    value: String,
    labelColor: Color = MiuixTheme.colorScheme.onSurface,
    valueColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    labelFontSize: androidx.compose.ui.unit.TextUnit = MiuixTheme.textStyles.body2.fontSize,
    valueFontSize: androidx.compose.ui.unit.TextUnit = MiuixTheme.textStyles.body2.fontSize,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = labelFontSize,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            fontSize = valueFontSize,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}
