package io.github.seyud.weave.ui.module

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import io.github.seyud.weave.core.cmp
import io.github.seyud.weave.core.integration.AppIconManager
import io.github.seyud.weave.core.ktx.toast
import io.github.seyud.weave.core.utils.RootUtils
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.ui.webui.WebUIActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import io.github.seyud.weave.core.R as CoreR

object ModuleShortcutContract {
    const val EXTRA_SHORTCUT_TYPE = "shortcut_type"
    const val EXTRA_MODULE_ID = "module_id"
    const val EXTRA_MODULE_NAME = "module_name"

    const val TYPE_ACTION = "module_action"
    const val TYPE_WEB_UI = "module_webui"
}

private const val ROOT_ICON_SCHEME = "root"

internal fun buildRootIconUri(path: String): String {
    return Uri.Builder()
        .scheme(ROOT_ICON_SCHEME)
        .path(path)
        .build()
        .toString()
}

suspend fun createModuleShortcut(
    context: Context,
    moduleId: String,
    name: String,
    iconUri: String?,
    type: ShortcutType,
): Boolean {
    val (shortcutId, shortcutIntent) = when (type) {
        ShortcutType.Action -> {
            "module_action_$moduleId" to Intent().apply {
                component = MainActivity::class.java.cmp(context.packageName)
                action = Intent.ACTION_VIEW
                putExtra(ModuleShortcutContract.EXTRA_SHORTCUT_TYPE, ModuleShortcutContract.TYPE_ACTION)
                putExtra(ModuleShortcutContract.EXTRA_MODULE_ID, moduleId)
                putExtra(ModuleShortcutContract.EXTRA_MODULE_NAME, name)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }

        ShortcutType.WebUI -> {
            "module_webui_$moduleId" to Intent().apply {
                component = WebUIActivity::class.java.cmp(context.packageName)
                action = Intent.ACTION_VIEW
                putExtra("id", moduleId)
                putExtra("name", name)
                putExtra("from_webui_shortcut", true)
                putExtra(ModuleShortcutContract.EXTRA_SHORTCUT_TYPE, ModuleShortcutContract.TYPE_WEB_UI)
                putExtra(ModuleShortcutContract.EXTRA_MODULE_ID, moduleId)
                putExtra(ModuleShortcutContract.EXTRA_MODULE_NAME, name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    val hasPinned = withContext(Dispatchers.IO) {
        hasPinnedShortcut(context, shortcutId)
    }
    val iconCompat = withContext(Dispatchers.IO) {
        createShortcutIcon(context, iconUri)
    }
    val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
        .setShortLabel(name)
        .setIntent(shortcutIntent)
        .setIcon(iconCompat ?: defaultShortcutIcon(context))
        .build()

    return withContext(Dispatchers.Main) {
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }.onFailure {
            Timber.w(it, "Failed to push dynamic module shortcut: %s", shortcutId)
        }

        if (hasPinned) {
            context.toast(context.getString(CoreR.string.module_shortcut_updated), Toast.LENGTH_SHORT)
            return@withContext true
        }

        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            context.toast(context.getString(CoreR.string.module_shortcut_not_supported), Toast.LENGTH_LONG)
            return@withContext false
        }

        val pinned = runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.onFailure {
            Timber.w(it, "Failed to request pinned module shortcut: %s", shortcutId)
        }.getOrDefault(false)

        if (pinned) {
            context.toast(context.getString(CoreR.string.module_shortcut_created), Toast.LENGTH_SHORT)
        } else {
            context.toast(CoreR.string.failure, Toast.LENGTH_SHORT)
        }
        pinned
    }
}

fun deleteModuleShortcut(
    context: Context,
    moduleId: String,
    type: ShortcutType,
) {
    val shortcutId = when (type) {
        ShortcutType.Action -> "module_action_$moduleId"
        ShortcutType.WebUI -> "module_webui_$moduleId"
    }

    runCatching {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
    }.onFailure {
        Timber.w(it, "Failed to remove dynamic module shortcut: %s", shortcutId)
    }
    runCatching {
        ShortcutManagerCompat.disableShortcuts(context, listOf(shortcutId), "")
    }.onFailure {
        Timber.w(it, "Failed to disable module shortcut: %s", shortcutId)
    }
}

fun hasModuleShortcut(
    context: Context,
    moduleId: String,
    type: ShortcutType,
): Boolean {
    val shortcutId = when (type) {
        ShortcutType.Action -> "module_action_$moduleId"
        ShortcutType.WebUI -> "module_webui_$moduleId"
    }
    return hasPinnedShortcut(context, shortcutId)
}

fun loadShortcutBitmap(
    context: Context,
    iconUri: String?,
): Bitmap? {
    if (iconUri.isNullOrBlank()) {
        return null
    }

    return runCatching {
        val uri = iconUri.toUri()
        val rawBitmap = when {
            uri.scheme.equals(ROOT_ICON_SCHEME, ignoreCase = true) -> {
                val path = uri.path.orEmpty()
                if (path.isBlank()) {
                    null
                } else {
                    if (!RootUtils.ensureServiceConnected()) {
                        return@runCatching null
                    }
                    RootUtils.fs.getFile(path).newInputStream().use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
            }

            uri.scheme.isNullOrBlank() && iconUri.startsWith("/") -> {
                if (!RootUtils.ensureServiceConnected()) {
                    return@runCatching null
                }
                RootUtils.fs.getFile(iconUri).newInputStream().use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }

            else -> {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        } ?: return@runCatching null

        normalizeShortcutBitmap(rawBitmap)
    }.onFailure {
        Timber.w(it, "Failed to load module shortcut bitmap from %s", iconUri)
    }.getOrNull()
}

private fun hasPinnedShortcut(context: Context, shortcutId: String): Boolean {
    return runCatching {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .any { it.id == shortcutId && it.isEnabled }
    }.onFailure {
        Timber.w(it, "Failed to query pinned module shortcut: %s", shortcutId)
    }.getOrDefault(false)
}

private fun createShortcutIcon(
    context: Context,
    iconUri: String?,
): IconCompat? {
    val bitmap = loadShortcutBitmap(context, iconUri) ?: return null
    return IconCompat.createWithBitmap(bitmap)
}

private fun defaultShortcutIcon(context: Context): IconCompat {
    return IconCompat.createWithResource(context, AppIconManager.currentShortcutIconResId(context))
}

private fun normalizeShortcutBitmap(rawBitmap: Bitmap): Bitmap {
    val width = rawBitmap.width
    val height = rawBitmap.height
    val side = minOf(width, height)
    val offsetX = (width - side) / 2
    val offsetY = (height - side) / 2

    val square = runCatching {
        Bitmap.createBitmap(rawBitmap, offsetX, offsetY, side, side)
    }.getOrElse { rawBitmap }

    if (square !== rawBitmap && !rawBitmap.isRecycled) {
        rawBitmap.recycle()
    }

    if (side <= 512) {
        return square
    }

    return runCatching {
        square.scale(512, 512)
    }.getOrElse {
        square
    }.also { scaled ->
        if (scaled !== square && !square.isRecycled) {
            square.recycle()
        }
    }
}
