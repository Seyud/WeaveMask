package io.github.seyud.weave.ui.webui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import io.github.seyud.weave.core.ktx.getLabel
import io.github.seyud.weave.core.utils.InstalledItemSource
import io.github.seyud.weave.core.utils.InstalledPackageLoader
import io.github.seyud.weave.core.utils.RootUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

private const val ICON_CACHE_SIZE = 200

private data class WebUiAppInfo(
    val packageInfo: PackageInfo,
    val appLabel: String,
) {
    val packageName: String get() = packageInfo.packageName
}

internal object WebUiPackageRegistry {
    private val appIconCache = LruCache<String, Bitmap>(ICON_CACHE_SIZE)
    /** 每次排序时按当前系统语言取 Collator：静态持有会在运行期切换语言后失效 */
    private fun packageNameComparator(): Collator = Collator.getInstance(Locale.getDefault())

    @Volatile
    private var cachedApps: List<WebUiAppInfo>? = null
    @Volatile
    private var cachedSource: InstalledItemSource? = null

    fun listPackages(context: Context, type: String): String {
        val packageNames = loadApps(context)
            .asSequence()
            .filter { appInfo ->
                val flags = appInfo.packageInfo.applicationInfo?.flags ?: 0
                when (type.lowercase(Locale.ROOT)) {
                    "system" -> (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    "user" -> (flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    else -> true
                }
            }
            .map { it.packageName }
            .sortedWith(packageNameComparator())
            .toList()

        return JSONArray(packageNames).toString()
    }

    fun getPackagesInfo(context: Context, packageNamesJson: String): String {
        val packageNames = JSONArray(packageNamesJson)
        val jsonArray = JSONArray()
        val appMap = loadApps(context).associateBy { it.packageName }

        for (i in 0 until packageNames.length()) {
            val packageName = packageNames.getString(i)
            val appInfo = appMap[packageName]
            val jsonObject = JSONObject()

            if (appInfo == null) {
                jsonObject.put("packageName", packageName)
                jsonObject.put("error", "Package not found or inaccessible")
            } else {
                val packageInfo = appInfo.packageInfo
                val applicationInfo = packageInfo.applicationInfo
                jsonObject.put("packageName", packageInfo.packageName)
                jsonObject.put("versionName", packageInfo.versionName ?: "")
                jsonObject.put("versionCode", PackageInfoCompat.getLongVersionCode(packageInfo))
                jsonObject.put("appLabel", appInfo.appLabel)
                jsonObject.put(
                    "isSystem",
                    if (applicationInfo != null) {
                        (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    } else {
                        JSONObject.NULL
                    }
                )
                jsonObject.put("uid", applicationInfo?.uid ?: JSONObject.NULL)
            }

            jsonArray.put(jsonObject)
        }

        return jsonArray.toString()
    }

    fun loadAppIcon(context: Context, packageName: String, sizePx: Int): Bitmap? {
        appIconCache.get(packageName)?.let { return it }

        val packageInfo = loadApps(context).firstOrNull { it.packageName == packageName }?.packageInfo ?: return null
        val drawable = packageInfo.applicationInfo?.loadIcon(context.packageManager) ?: return null
        val bitmap = drawableToBitmap(drawable, sizePx).scale(sizePx, sizePx)
        appIconCache.put(packageName, bitmap)
        return bitmap
    }

    /**
     * Load installed apps using RootService to bypass package visibility restrictions.
     * Falls back to direct PackageManager call if RootService is unavailable.
     */
    private fun loadApps(context: Context): List<WebUiAppInfo> {
        cachedApps?.let { apps ->
            val source = cachedSource
            if (source == InstalledItemSource.ROOT) {
                return apps
            }
            if (source == InstalledItemSource.FALLBACK && !RootUtils.isServiceConnected()) {
                return apps
            }
        }

        val packageManager = context.packageManager
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES

        val result = InstalledPackageLoader.loadPackages(flags, packageManager)
        val packages = result.items

        val apps = packages
            .map { packageInfo ->
                val appLabel = packageInfo.applicationInfo?.getLabel(packageManager) ?: packageInfo.packageName
                WebUiAppInfo(packageInfo, appLabel)
            }
            .sortedWith(compareBy(packageNameComparator()) { it.packageName })

        cachedApps = apps
        cachedSource = result.source
        return apps
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
