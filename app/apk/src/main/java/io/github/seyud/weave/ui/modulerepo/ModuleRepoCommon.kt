package io.github.seyud.weave.ui.modulerepo

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seyud.weave.core.registry.RegistryAssets
import io.github.seyud.weave.core.registry.RegistryUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Compact star count for list rows: "999", "1.2k", "12.3k", "123k", "1.2M". Integer arithmetic
 * (rounded down) keeps it independent of the device locale, like GitHub's own counters.
 */
fun formatStarCount(count: Int): String {
    fun compact(value: Int, unit: Int, suffix: String): String {
        if (value >= unit * 100) return "${value / unit}$suffix"
        val tenths = value / (unit / 10)
        return if (tenths % 10 == 0) "${tenths / 10}$suffix" else "${tenths / 10}.${tenths % 10}$suffix"
    }
    return when {
        count < 1_000 -> count.toString()
        count < 1_000_000 -> compact(count, 1_000, "k")
        else -> compact(count, 1_000_000, "M")
    }
}

/** GitHub stargazers page of an "owner/name" repository (validated by the registry parser). */
fun starsUrl(starsRepository: String?): String? =
    starsRepository?.let { "https://github.com/$it/stargazers" }

fun formatFileSize(size: Long): String = when {
    size >= 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.1f GB", size / (1024f * 1024f * 1024f))
    size >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", size / (1024f * 1024f))
    size >= 1024L -> String.format(Locale.ROOT, "%.0f KB", size / 1024f)
    else -> "$size B"
}

/** "abcdef12…34567890" for compact display; the full hash is still selectable in the info text. */
fun shortHash(hash: String, head: Int = 12, tail: Int = 8): String =
    if (hash.length <= head + tail + 1) hash else hash.take(head) + "…" + hash.takeLast(tail)

private const val MAX_ICON_BYTES = 512L * 1024
private const val ICON_DECODE_PX = 192
private val iconCache = LruCache<String, ImageBitmap>(128)

/** Decodes registry icon bytes to a bitmap, downsampling while decoding so memory stays bounded. */
private fun decodeIcon(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    // Bound the larger side so very wide or tall images are downsampled too.
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ICON_DECODE_PX) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}

/**
 * Module icon from the registry (raster formats only); renders nothing until/unless it loads.
 * [origin] is the repository site root: only images served from it are accepted.
 */
@Composable
fun RepoModuleIcon(url: String?, origin: String, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    if (url == null) return
    val bitmap by produceState(iconCache.get(url), url, origin) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                // Icons are content-hashed registry assets: same origin only, size capped.
                iconCache.get(url) ?: RegistryAssets.fetch(url, origin, MAX_ICON_BYTES)?.let(::decodeIcon)
                    ?.also { iconCache.put(url, it) }
            }
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp)),
        )
    }
}
