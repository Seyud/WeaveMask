package io.github.seyud.weave.ui.component

import android.content.pm.ApplicationInfo
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.utils.AppIconCache
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class IconKey(
    val packageName: String,
    val uid: Int,
    val sourceDir: String,
)

@Composable
fun AppIconImage(
    applicationInfo: ApplicationInfo,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetSizePx = with(density) { 48.dp.roundToPx() }
    val iconKey = remember(applicationInfo) {
        IconKey(
            packageName = applicationInfo.packageName,
            uid = applicationInfo.uid,
            sourceDir = applicationInfo.sourceDir.orEmpty(),
        )
    }
    val cachedBitmap = remember(iconKey, targetSizePx) {
        AppIconCache.getCachedBitmap(applicationInfo, targetSizePx)
    }

    var appBitmap by remember(iconKey, targetSizePx) { mutableStateOf(cachedBitmap) }

    LaunchedEffect(iconKey, targetSizePx) {
        if (appBitmap == null) {
            appBitmap = AppIconCache.loadIconBitmap(context, applicationInfo, targetSizePx)
        }
    }

    Box(modifier = modifier) {
        if (cachedBitmap != null) {
            Image(
                bitmap = remember(cachedBitmap) { cachedBitmap.asImageBitmap() },
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Crossfade(
                targetState = appBitmap,
                animationSpec = tween(durationMillis = 150),
                label = "AppIconFade",
            ) { icon ->
                if (icon == null) {
                    AppIconPlaceholder()
                } else {
                    Image(
                        bitmap = remember(icon) { icon.asImageBitmap() },
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconPlaceholder() {
    val containerColor = if (LocalEnableBlur.current) {
        MiuixTheme.colorScheme.secondaryContainer
    } else {
        MiuixTheme.colorScheme.secondaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor),
    )
}
