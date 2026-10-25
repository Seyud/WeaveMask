package io.github.seyud.weave.core.integration

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.R

object AppShortcuts {

    private fun createLaunchIntent(context: Context): Intent? {
        return if (AppIconManager.isSupported(context)) {
            AppIconManager.createMainActivityIntent(context)
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        }
    }

    fun setupDynamic(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val manager = context.getSystemService<ShortcutManager>() ?: return
            manager.dynamicShortcuts = getShortCuts(context)
        }
    }

    fun addHomeIcon(context: Context) {
        val intent = createLaunchIntent(context) ?: return
        val info = ShortcutInfoCompat.Builder(context, Const.Nav.HOME)
            .setShortLabel(context.getString(R.string.magisk))
            .setIntent(intent)
            .setIcon(context.getIconCompat(AppIconManager.currentShortcutIconResId(context)))
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    private fun Context.getIcon(id: Int): Icon {
        return Icon.createWithResource(this, id)
    }

    private fun Context.getIconCompat(id: Int): IconCompat {
        return IconCompat.createWithResource(this, id)
    }

    @RequiresApi(api = 25)
    private fun getShortCuts(context: Context): List<ShortcutInfo> {
        val intent = createLaunchIntent(context) ?: return emptyList()

        val shortCuts = mutableListOf<ShortcutInfo>()

        if (Info.showSuperUser) {
            shortCuts.add(
                ShortcutInfo.Builder(context, Const.Nav.SUPERUSER)
                    .setShortLabel(context.getString(R.string.superuser))
                    .setIntent(
                        Intent(intent).putExtra(Const.Key.OPEN_SECTION, Const.Nav.SUPERUSER)
                    )
                    .setIcon(context.getIcon(R.drawable.sc_superuser))
                    .setRank(0)
                    .build()
            )
        }
        if (Info.env.isActive) {
            shortCuts.add(
                ShortcutInfo.Builder(context, Const.Nav.MODULES)
                    .setShortLabel(context.getString(R.string.modules))
                    .setIntent(
                        Intent(intent).putExtra(Const.Key.OPEN_SECTION, Const.Nav.MODULES)
                    )
                    .setIcon(context.getIcon(R.drawable.sc_extension))
                    .setRank(1)
                    .build()
            )
        }
        return shortCuts
    }
}
