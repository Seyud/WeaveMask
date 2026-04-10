package io.github.seyud.weave.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.collection.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * 应用图标异步加载和 LRU 缓存工具类。
 *
 * 使用内存 LRU 缓存避免重复加载，专用后台线程池保持主线程流畅。
 */
object AppIconCache : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    private class IconLruCache(maxSize: Int) :
        LruCache<String, Bitmap>(maxSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private val lruCache: LruCache<String, Bitmap>
    private val dispatcher: CoroutineDispatcher

    init {
        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val availableCacheSize = (maxMemory / 4).toInt()
        lruCache = IconLruCache(availableCacheSize)

        val threadCount = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        val loadIconExecutor = Executors.newFixedThreadPool(threadCount)
        dispatcher = loadIconExecutor.asCoroutineDispatcher()
    }

    private fun buildKey(info: ApplicationInfo, size: Int): String {
        return "${info.packageName}:${info.uid}:${info.sourceDir}:$size"
    }

    fun getCachedBitmap(info: ApplicationInfo, size: Int): Bitmap? =
        lruCache[buildKey(info, size)]

    private fun put(info: ApplicationInfo, size: Int, bitmap: Bitmap) {
        val key = buildKey(info, size)
        if (lruCache[key] == null) {
            lruCache.put(key, bitmap)
        }
    }

    private fun ApplicationInfo.withCurrentUserUid(): ApplicationInfo {
        val myUserId = Process.myUid() / 100000
        val appId = uid % 100000
        val targetUid = myUserId * 100000 + appId
        if (uid == targetUid) return this
        return ApplicationInfo(this).apply { uid = targetUid }
    }

    private fun getOrLoadBitmap(
        context: Context,
        info: ApplicationInfo,
        size: Int
    ): Bitmap {
        getCachedBitmap(info, size)?.let { return it }

        val drawable = info.withCurrentUserUid().loadIcon(context.packageManager)
        val bitmap = drawable.toBitmap(size, size).also { it.prepareToDraw() }
        put(info, size, bitmap)
        return bitmap
    }

    /**
     * 异步加载应用图标 Drawable。
     *
     * @param context Context
     * @param info ApplicationInfo
     * @param size 图标尺寸（像素）
     * @return Drawable 或 null（加载失败时）
     */
    suspend fun loadIconDrawable(context: Context, info: ApplicationInfo, size: Int): Drawable? {
        return try {
            val bitmap = withContext(dispatcher) {
                getOrLoadBitmap(context, info, size)
            }
            bitmap.toDrawable(context.resources)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步加载应用图标 Bitmap。
     *
     * @param context Context
     * @param info ApplicationInfo
     * @param size 图标尺寸（像素）
     * @return Bitmap 或 null（加载失败时）
     */
    suspend fun loadIconBitmap(context: Context, info: ApplicationInfo, size: Int): Bitmap? {
        return try {
            withContext(dispatcher) {
                getOrLoadBitmap(context, info, size)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
