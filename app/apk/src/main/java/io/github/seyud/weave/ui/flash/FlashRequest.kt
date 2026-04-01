package io.github.seyud.weave.ui.flash

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.ui.MainActivity
import io.github.seyud.weave.ui.navigation3.Route

data class FlashRequest(
    val action: String,
    val dataUris: List<Uri> = emptyList(),
) {
    val dataUri: Uri? get() = dataUris.singleOrNull()

    fun toRoute(): Route.Flash = Route.Flash(action, dataUris.map(Uri::toString))

    fun toPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FLASH_ACTION, action)
            if (dataUris.isNotEmpty()) {
                putStringArrayListExtra(
                    MainActivity.EXTRA_FLASH_URIS,
                    ArrayList(dataUris.map(Uri::toString))
                )
            }
            dataUri?.let { putExtra(MainActivity.EXTRA_FLASH_URI, it.toString()) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, action.hashCode(), intent, flags)
    }

    companion object {
        private fun flashType(isSecondSlot: Boolean) =
            if (isSecondSlot) Const.Value.FLASH_INACTIVE_SLOT else Const.Value.FLASH_MAGISK

        fun flash(isSecondSlot: Boolean) = FlashRequest(action = flashType(isSecondSlot))

        fun patch(uri: Uri) = FlashRequest(
            action = Const.Value.PATCH_FILE,
            dataUris = listOf(uri),
        )

        fun uninstall() = FlashRequest(action = Const.Value.UNINSTALL)

        fun install(file: Uri) = FlashRequest(
            action = Const.Value.FLASH_ZIP,
            dataUris = listOf(file),
        )

        fun install(files: List<Uri>) = FlashRequest(
            action = Const.Value.FLASH_ZIP,
            dataUris = files,
        )
    }
}
