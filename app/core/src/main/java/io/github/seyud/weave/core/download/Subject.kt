package io.github.seyud.weave.core.download

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.model.UpdateInfo
import io.github.seyud.weave.core.model.module.OnlineModule
import io.github.seyud.weave.core.registry.ExpectedFile
import io.github.seyud.weave.core.utils.MediaStoreUtils
import io.github.seyud.weave.core.integration.AppNotifications
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.UUID

abstract class Subject : Parcelable {

    abstract val url: String
    abstract val file: Uri
    abstract val title: String
    abstract val notifyId: Int
    open val autoLaunch: Boolean get() = true

    /** Expected size in bytes when this download is integrity verified; non-positive means legacy. */
    open val expectedSize: Long get() = 0L

    /** Expected SHA-256 (hex) when this download is integrity verified; null means legacy. */
    open val expectedSha256: String? get() = null

    /**
     * Verification data for this download, or null for the legacy unchecked path. Both halves are
     * required: a hash without a usable size cannot be checked, as the verifier rejects a
     * non-positive expected size, so such data is ignored instead of failing every download.
     */
    val expected: ExpectedFile?
        get() {
            val sha256 = expectedSha256 ?: return null
            if (expectedSize < 1) return null
            return ExpectedFile(expectedSize, sha256)
        }

    open fun pendingIntent(context: Context): PendingIntent? = null

    abstract class Module : Subject() {
        abstract val module: OnlineModule

        /**
         * Whether the downloaded ZIP should be turned into a Magisk module installer
         * (`META-INF/com/google/android/update-binary` added). A one-shot script is executed
         * from its own directory instead, and is re-verified against the release size and
         * SHA-256, so its bytes must stay exactly as published.
         */
        open val asModuleInstaller: Boolean get() = true

        final override val url: String get() = module.zipUrl
        final override val title: String get() = module.downloadFilename
        final override val expectedSize: Long get() = module.zipSize
        final override val expectedSha256: String? get() = module.zipSha256
        final override val file by lazy {
            MediaStoreUtils.getFile(title).uri
        }
    }

    @Parcelize
    class App(
        private val json: UpdateInfo = Info.update,
        override val notifyId: Int = AppNotifications.nextId()
    ) : Subject() {
        override val title: String get() = "WeaveMask-${json.version}(${json.versionCode})"
        override val url: String get() = json.link

        @IgnoredOnParcel
        override val file by lazy {
            MediaStoreUtils.getFile("${title}.apk").uri
        }

        @IgnoredOnParcel
        var intent: Intent? = null

        override fun pendingIntent(context: Context): PendingIntent? {
            return (intent ?: createInstallIntent()).toPending(context)
        }

        private fun createInstallIntent(): Intent {
            return Intent(Intent.ACTION_VIEW)
                .setDataAndType(file, "application/vnd.android.package-archive")
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
        }
    }

    @Parcelize
    class Test(
        override val notifyId: Int = AppNotifications.nextId(),
        override val title: String = UUID.randomUUID().toString().substring(0, 6)
    ) : Subject() {
        override val url get() = "https://link.testfile.org/250MB"
        override val file get() = File("/dev/null").toUri()
        override val autoLaunch get() = false
    }

    @SuppressLint("InlinedApi")
    protected fun Intent.toPending(context: Context): PendingIntent {
        return PendingIntent.getActivity(context, notifyId, this,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
    }
}
