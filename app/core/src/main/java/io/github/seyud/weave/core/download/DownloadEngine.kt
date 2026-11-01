package io.github.seyud.weave.core.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.collection.SparseArrayCompat
import androidx.collection.isNotEmpty
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.JobService
import io.github.seyud.weave.core.R
import io.github.seyud.weave.core.base.IActivityExtension
import io.github.seyud.weave.core.cmp
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.intent
import io.github.seyud.weave.core.ktx.cachedFile
import io.github.seyud.weave.core.ktx.set
import io.github.seyud.weave.core.registry.DownloadVerificationException
import io.github.seyud.weave.core.registry.DownloadVerifier
import io.github.seyud.weave.core.registry.ExpectedFile
import io.github.seyud.weave.core.utils.ProgressInputStream
import io.github.seyud.weave.core.integration.AppNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import timber.log.Timber
import java.io.FileOutputStream
import java.io.InputStream

/**
 * This class drives the execution of file downloads and notification management.
 *
 * Each download engine instance has to be paired with a "session" that is managed by the operating
 * system. A session is an Android component that allows executing long lasting operations and
 * have its state tied to a notification to show progress.
 *
 * A session can only have one single notification representing its state, and the operating system
 * also uses the notification to manage the lifecycle of a session. One goal of this class is
 * to support concurrent download tasks using only one single session, so internally it manages
 * all active tasks and notifications and properly re-assign notifications to be attached to
 * the session to make sure all download operations can be completed without the operating system
 * killing the session.
 *
 * For API 23 - 33, we use a foreground service as a session.
 * For API 34 and higher, we use user-initiated job services as a session.
 */
class DownloadEngine(session: DownloadSession) : DownloadSession by session, DownloadNotifier {

    companion object {
        const val ACTION = "io.github.seyud.weave.DOWNLOAD"
        const val SUBJECT_KEY = "subject"
        private const val REQUEST_CODE = 1

        private val progressBroadcast = MutableLiveData<Pair<Float, Subject>?>()

        /**
         * Failures carry the subject, the verification reason (null for anything else) and the
         * throwable, because the progress channel only has the -2f "failed" marker and the UI has
         * to tell a corrupted ZIP from a plain network error.
         */
        private val failureBroadcast =
            MutableLiveData<Triple<Subject, DownloadVerificationException.Reason?, Throwable>?>()

        private fun broadcast(progress: Float, subject: Subject) {
            progressBroadcast.postValue(progress to subject)
        }

        private fun broadcastFailure(
            subject: Subject,
            reason: DownloadVerificationException.Reason?,
            error: Throwable
        ) {
            failureBroadcast.postValue(Triple(subject, reason, error))
        }

        fun observeProgress(owner: LifecycleOwner, callback: (Float, Subject) -> Unit) {
            progressBroadcast.value = null
            progressBroadcast.observe(owner) {
                val (progress, subject) = it ?: return@observe
                callback(progress, subject)
            }
        }

        /**
         * Observes failed downloads. A non-null [DownloadVerificationException.Reason] means the
         * file was rejected by integrity verification (URL not allowed, size or SHA-256 mismatch)
         * and deleted, so it never reached MediaStore; null means any other failure.
         *
         * The failure is broadcast for every download in progress, so the caller compares
         * `subject.notifyId` with the subject it started, exactly like [observeProgress].
         */
        fun observeFailure(
            owner: LifecycleOwner,
            callback: (Subject, DownloadVerificationException.Reason?, Throwable) -> Unit
        ) {
            failureBroadcast.value = null
            failureBroadcast.observe(owner) {
                val (subject, reason, error) = it ?: return@observe
                callback(subject, reason, error)
            }
        }

        private fun createBroadcastIntent(context: Context, subject: Subject) =
            context.intent<io.github.seyud.weave.core.Receiver>()
                .setAction(ACTION)
                .putExtra(SUBJECT_KEY, subject)

        private fun createServiceIntent(context: Context, subject: Subject) =
            context.intent<io.github.seyud.weave.core.Service>()
                .setAction(ACTION)
                .putExtra(SUBJECT_KEY, subject)

        @SuppressLint("InlinedApi")
        fun getPendingIntent(context: Context, subject: Subject): PendingIntent {
            val flag = PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_ONE_SHOT
            return if (Build.VERSION.SDK_INT >= 34) {
                // On API 34+, download tasks are handled with a user-initiated job.
                // However, there is no way to schedule a new job directly with a pending intent.
                // As a workaround, we send the subject to a broadcast receiver and have it
                // schedule the job for us.
                val intent = createBroadcastIntent(context, subject)
                PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flag)
            } else {
                val intent = createServiceIntent(context, subject)
                if (Build.VERSION.SDK_INT >= 26) {
                    PendingIntent.getForegroundService(context, REQUEST_CODE, intent, flag)
                } else {
                    PendingIntent.getService(context, REQUEST_CODE, intent, flag)
                }
            }
        }

        @SuppressLint("InlinedApi")
        fun <T> startWithActivity(
            activity: T,
            subject: Subject
        ) where T : ComponentActivity, T : IActivityExtension {
            activity.withPermission(Manifest.permission.POST_NOTIFICATIONS) {
                // Always download regardless of notification permission status
                start(activity.applicationContext, subject)
            }
        }

        @SuppressLint("MissingPermission")
        fun start(context: Context, subject: Subject) {
            if (Build.VERSION.SDK_INT >= 34) {
                val scheduler = context.getSystemService<JobScheduler>()!!
                val cmp = JobService::class.java.cmp(context.packageName)
                val extras = Bundle()
                extras.putParcelable(SUBJECT_KEY, subject)
                val info = JobInfo.Builder(Const.ID.DOWNLOAD_JOB_ID, cmp)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setUserInitiated(true)
                    .setTransientExtras(extras)
                    .build()
                scheduler.schedule(info)
            } else {
                val intent = createServiceIntent(context, subject)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /**
         * Starts a module ZIP download. A [Subject.Module] derives its expected size + SHA-256 from
         * the [OnlineModule][io.github.seyud.weave.core.model.module.OnlineModule] it was built
         * from, so a registry module is verified automatically while a module without that data
         * keeps the legacy unchecked path.
         */
        fun startModule(context: Context, subject: Subject.Module) = start(context, subject)

        @SuppressLint("InlinedApi")
        fun <T> startModuleWithActivity(
            activity: T,
            subject: Subject.Module
        ) where T : ComponentActivity, T : IActivityExtension =
            startWithActivity(activity, subject)
    }

    private val notifications = SparseArrayCompat<Notification.Builder>()
    private var attachedId = -1
    private val job = Job()
    private val processor = DownloadProcessor(this)
    private val network get() = ServiceLocator.networkService

    fun download(subject: Subject) {
        notifyUpdate(subject.notifyId)
        CoroutineScope(job + Dispatchers.IO).launch {
            try {
                val expected = subject.expected
                if (expected != null) {
                    downloadVerified(subject, expected)
                } else {
                    val stream = network.fetchFile(subject.url).toProgressStream(subject)
                    processor.handle(stream, subject)
                }
                val launchIntent = subject.pendingIntent(context)
                if (subject.autoLaunch && launchIntent != null) {
                    notifyRemove(subject.notifyId)
                    launchIntent.send()
                } else {
                    notifyFinish(subject)
                }
            } catch (e: DownloadVerificationException) {
                Timber.e(e)
                notifyFail(subject, e.reason, e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                notifyFail(subject, null, e)
            }
        }
    }

    /**
     * Verified download path for registry modules: [the subject's URL][Subject.url] and every
     * redirect hop are checked against the allow-list before a byte is fetched, and the body is
     * streamed into an app-private cache file while the size and SHA-256 are verified. Only a file
     * that passed verification is handed to [DownloadProcessor], which is also the first place
     * that touches [Subject.file], so a rejected download never reaches MediaStore.
     */
    private suspend fun downloadVerified(subject: Subject, expected: ExpectedFile) {
        val temp = context.cachedFile("verified-${subject.notifyId}.zip")
        try {
            val verifier = DownloadVerifier(expected)
            VerifiedRequest.open(subject.url).use { response ->
                // The progress emitter needs the size up front; expected.size is the only
                // trustworthy one, the server's Content-Length is not verified yet.
                val source = progressStream(expected.size, subject, response.body.byteStream())
                FileOutputStream(temp).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        // update() throws as soon as more bytes than expected arrive, so an
                        // oversized payload is not written to disk either.
                        verifier.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                    out.fd.sync()
                }
            }
            verifier.finish()
            temp.inputStream().use { processor.handle(it, subject) }
        } finally {
            // Verified or rejected, the temporary file only lives for this download.
            temp.delete()
        }
    }

    @Synchronized
    fun reattach() {
        val builder = notifications[attachedId] ?: return
        attachNotification(attachedId, builder)
    }

    private fun attach(id: Int, notification: Notification.Builder) {
        attachedId = id
        attachNotification(id, notification)
    }

    private fun finalNotify(id: Int, editor: (Notification.Builder) -> Unit): Int {
        val notification = notifyRemove(id)?.also(editor) ?: return -1
        val newId = AppNotifications.nextId()
        AppNotifications.mgr.notify(newId, notification.build())
        return newId
    }

    private fun notifyFail(
        subject: Subject,
        reason: DownloadVerificationException.Reason?,
        error: Throwable
    ) {
        // Broadcast outside of the notification editor: a notification that was already removed
        // must not swallow the failure the UI is waiting for.
        broadcastFailure(subject, reason, error)
        finalNotify(subject.notifyId) {
            broadcast(-2f, subject)
            it.setContentText(failureText(subject, reason))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setOngoing(false)
        }
    }

    /**
     * Verification failures get their own notification text, so a ZIP that failed the integrity
     * check is not mistaken for a network error.
     */
    private fun failureText(
        subject: Subject,
        reason: DownloadVerificationException.Reason?
    ): CharSequence = when (reason) {
        DownloadVerificationException.Reason.URL_NOT_ALLOWED ->
            context.getString(R.string.module_repo_download_url_blocked)

        DownloadVerificationException.Reason.SIZE_MISMATCH,
        DownloadVerificationException.Reason.SHA256_MISMATCH ->
            context.getString(R.string.module_repo_download_verify_failed, subject.title)

        null -> context.getString(R.string.download_file_error)
    }

    private fun notifyFinish(subject: Subject) = finalNotify(subject.notifyId) {
        broadcast(1f, subject)
        it.setContentTitle(subject.title)
            .setContentText(context.getString(R.string.download_complete))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
        subject.pendingIntent(context)?.let { intent -> it.setContentIntent(intent) }
    }

    @Synchronized
    override fun notifyUpdate(id: Int, editor: (Notification.Builder) -> Unit) {
        val notification = (notifications[id] ?: AppNotifications.startProgress("").also {
            notifications[id] = it
        }).apply(editor)

        if (attachedId < 0)
            attach(id, notification)
        else
            AppNotifications.mgr.notify(id, notification.build())
    }

    @Synchronized
    private fun notifyRemove(id: Int): Notification.Builder? {
        val idx = notifications.indexOfKey(id)
        var n: Notification.Builder? = null

        if (idx >= 0) {
            n = notifications.valueAt(idx)
            notifications.removeAt(idx)

            // The cancelled notification is the one attached to the session, need special handling
            if (attachedId == id) {
                if (notifications.isNotEmpty()) {
                    // There are still remaining notifications, pick one and attach to the session
                    val anotherId = notifications.keyAt(0)
                    val notification = notifications.valueAt(0)
                    attach(anotherId, notification)
                } else {
                    // No more notifications left, terminate the session
                    attachedId = -1
                    onDownloadComplete()
                }
            }
        }

        AppNotifications.mgr.cancel(id)
        return n
    }

    private fun ResponseBody.toProgressStream(subject: Subject): InputStream =
        progressStream(contentLength(), subject, byteStream())

    /**
     * Wraps [base] so every progress tick updates the notification and the progress broadcast.
     * [max] is the total number of bytes, or a non-positive value when it is unknown; verified
     * downloads pass their expected size, so they advance exactly like unchecked ones do.
     */
    private fun progressStream(max: Long, subject: Subject, base: InputStream): InputStream {
        val total = max.toFloat() / 1048576
        val id = subject.notifyId

        notifyUpdate(id) { it.setContentTitle(subject.title) }

        return ProgressInputStream(base) { bytesRead ->
            val progress = bytesRead.toFloat() / 1048576
            notifyUpdate(id) { notification ->
                if (max > 0) {
                    broadcast(progress / total, subject)
                    notification
                        .setProgress(max.toInt(), bytesRead.toInt(), false)
                        .setContentText("%.2f / %.2f MB".format(progress, total))
                } else {
                    broadcast(-1f, subject)
                    notification.setContentText("%.2f MB / ??".format(progress))
                }
            }
        }
    }
}
