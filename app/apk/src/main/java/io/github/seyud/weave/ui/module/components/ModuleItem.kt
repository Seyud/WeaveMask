package io.github.seyud.weave.ui.module.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.ui.module.ModuleInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
internal fun ModuleItem(
    module: ModuleInfo,
    onToggleEnabled: (Boolean) -> Unit,
    onRunAction: () -> Unit,
    onOpenWebUi: () -> Unit,
    onAddShortcut: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onToggleRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isSwitchEnabled = !module.removed &&
        !module.updated &&
        (!module.showNotice || (!Info.isZygiskEnabled && module.isZygisk))
    val hasDescription = module.description.isNotEmpty()
    val visibleActionButtonCount = listOf(
        module.showAction && module.enabled && !module.removed,
        module.showWebUi,
        module.showShortcutButton,
        module.showUpdate,
    ).count { it }
    val compactActionButtons = visibleActionButtonCount >= 3
    var expanded by rememberSaveable(module.id) { mutableStateOf(false) }

    val textDecoration = if (module.removed) TextDecoration.LineThrough else null
    val cardAlpha = if (module.enabled && !module.removed) 1f else 0.5f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = cardAlpha },
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(16.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = {
            if (module.showWebUi) {
                onOpenWebUi()
            } else if (hasDescription) {
                expanded = !expanded
            }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            ) {
                Text(
                    text = module.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurface,
                    textDecoration = textDecoration,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${module.version} by ${module.author}",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textDecoration = textDecoration,
                )
            }
            Switch(
                checked = module.enabled,
                enabled = isSwitchEnabled,
                onCheckedChange = onToggleEnabled,
            )
        }

        if (hasDescription) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = 250,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
            ) {
                Text(
                    text = module.description,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    textDecoration = textDecoration,
                )
            }
        }

        if (module.showNotice) {
            Text(
                text = module.noticeText.getText(context.resources),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
                color = MiuixTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (module.showAction && module.enabled && !module.removed) {
                val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                val actionIconTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                IconButton(
                    minHeight = 32.dp,
                    minWidth = 32.dp,
                    onClick = onRunAction,
                    enabled = module.enabled,
                    backgroundColor = secondaryContainer,
                ) {
                    if (compactActionButtons) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = MiuixIcons.Play,
                            tint = actionIconTint,
                            contentDescription = context.getString(CoreR.string.module_action),
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = MiuixIcons.Play,
                                tint = actionIconTint,
                                contentDescription = context.getString(CoreR.string.module_action),
                            )
                            Text(
                                text = context.getString(CoreR.string.module_action),
                                color = actionIconTint,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            if (module.showWebUi) {
                val webUiBg = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                val webUiTint = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                IconButton(
                    minHeight = 32.dp,
                    minWidth = 32.dp,
                    onClick = onOpenWebUi,
                    backgroundColor = webUiBg,
                ) {
                    if (compactActionButtons) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = Icons.Rounded.Code,
                            tint = webUiTint,
                            contentDescription = context.getString(CoreR.string.module_webui),
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = Icons.Rounded.Code,
                                tint = webUiTint,
                                contentDescription = context.getString(CoreR.string.module_webui),
                            )
                            Text(
                                text = context.getString(CoreR.string.module_webui),
                                color = webUiTint,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            if (module.showShortcutButton) {
                val shortcutBg = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                val shortcutTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                IconButton(
                    minHeight = 32.dp,
                    minWidth = 32.dp,
                    onClick = onAddShortcut,
                    backgroundColor = shortcutBg,
                ) {
                    if (compactActionButtons) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = Icons.Rounded.Add,
                            tint = shortcutTint,
                            contentDescription = context.getString(CoreR.string.module_shortcut_button),
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = Icons.Rounded.Add,
                                tint = shortcutTint,
                                contentDescription = context.getString(CoreR.string.module_shortcut_button),
                            )
                            Text(
                                text = context.getString(CoreR.string.module_shortcut_button),
                                color = shortcutTint,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (module.showUpdate) {
                val updateBg = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                val updateTint = MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                IconButton(
                    backgroundColor = updateBg,
                    enabled = module.updateReady,
                    minHeight = 32.dp,
                    minWidth = 32.dp,
                    onClick = onDownloadUpdate,
                ) {
                    if (compactActionButtons) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = MiuixIcons.UploadCloud,
                            tint = updateTint,
                            contentDescription = context.getString(CoreR.string.update),
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = MiuixIcons.UploadCloud,
                                tint = updateTint,
                                contentDescription = context.getString(CoreR.string.update),
                            )
                            Text(
                                text = context.getString(CoreR.string.update),
                                color = updateTint,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
            val actionIconTint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            val removeButtonText = if (module.removed) {
                context.getString(CoreR.string.module_state_restore)
            } else {
                context.getString(CoreR.string.module_state_remove)
            }
            IconButton(
                minHeight = 32.dp,
                minWidth = 32.dp,
                onClick = onToggleRemoved,
                enabled = !module.updated,
                backgroundColor = if (module.removed) {
                    secondaryContainer.copy(alpha = 0.8f)
                } else {
                    secondaryContainer
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = if (module.removed) {
                            MiuixIcons.Undo
                        } else {
                            MiuixIcons.Delete
                        },
                        tint = actionIconTint,
                        contentDescription = removeButtonText,
                    )
                    Text(
                        text = removeButtonText,
                        color = actionIconTint,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
