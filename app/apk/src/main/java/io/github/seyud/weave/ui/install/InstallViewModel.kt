package io.github.seyud.weave.ui.install

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.Spanned
import android.text.SpannedString
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.seyud.weave.arch.BaseViewModel
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.BuildConfig.APP_VERSION_CODE
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.repository.NetworkService
import io.github.seyud.weave.dialog.SecondSlotWarningDialog
import io.github.seyud.weave.ui.flash.FlashRequest
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.io.File
import java.io.IOException
import io.github.seyud.weave.core.R as CoreR

class InstallViewModel(svc: NetworkService, markwon: Markwon) : BaseViewModel() {

    companion object {
        private const val INSTALL_STATE_KEY = "install_state"
        private val uri = MutableLiveData<Uri?>()
        private val deferredPatch = MutableLiveData<Pair<Uri, String>?>()

        // 方法ID常量（替代已删除的R.id引用）
        const val METHOD_PATCH = 1
        const val METHOD_DIRECT = 2
        const val METHOD_INACTIVE_SLOT = 3
        const val METHOD_DOWNLOAD = 4
    }

    val isRooted get() = Info.isRooted
    val skipOptions = Info.isEmulator || (Info.isSAR && !Info.isFDE && Info.ramdisk)
    val noSecondSlot = !isRooted || !Info.isAB || Info.isEmulator

    private var stepState by mutableIntStateOf(if (skipOptions) 1 else 0)
    var step: Int
        get() = stepState
        set(value) {
            stepState = value
        }

    private var methodId by mutableIntStateOf(-1)

    var method
        get() = methodId
        set(value) {
            setMethod(value, showWarning = true)
        }

    private fun setMethod(value: Int, showWarning: Boolean) {
        if (methodId == value) return
        methodId = value
        if (showWarning && value == METHOD_INACTIVE_SLOT) {
            SecondSlotWarningDialog().show()
        }
    }

    val data: LiveData<Uri?> get() = uri
    val pendingPatch: LiveData<Pair<Uri, String>?> get() = deferredPatch

    fun setDeferredPatch(contentUri: Uri, fileName: String) {
        deferredPatch.value = Pair(contentUri, fileName)
    }

    suspend fun copyDeferredPatch(context: Context): Uri {
        val (contentUri, fileName) = deferredPatch.value
            ?: throw IllegalStateException("No deferred patch URI")
        return withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "patch_boot").apply {
                deleteRecursively()
                mkdirs()
            }
            val target = File(cacheDir, fileName)
            val input = context.contentResolver.openInputStream(contentUri)
                ?: throw IOException("Cannot read selected file")
            input.use { source ->
                target.outputStream().use { sink ->
                    source.copyTo(sink)
                }
            }
            target.toUri()
        }.also { localUri ->
            setPatchFile(localUri)
        }
    }

    fun setPatchFile(localUri: Uri) {
        deferredPatch.value = null
        uri.value = localUri
    }

    var downloadUrl by mutableStateOf("")
        internal set

    private fun isValidUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        val uri = url.toUri()
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrEmpty() &&
            !uri.path.isNullOrEmpty()
    }

    var notes by mutableStateOf<Spanned>(SpannedString(""))
        private set

    var notesText: String = ""
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val noteFile = File(AppContext.cacheDir, "${APP_VERSION_CODE}.md")
                val noteText = when {
                    noteFile.exists() -> noteFile.readText()
                    else -> {
                        val note = svc.fetchUpdate(APP_VERSION_CODE)?.note.orEmpty()
                        if (note.isEmpty()) return@launch
                        noteFile.writeText(note)
                        note
                    }
                }
                notesText = noteText
                val spanned = markwon.toMarkdown(noteText)
                withContext(Dispatchers.Main) {
                    notes = spanned
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }
    }

    fun composeFlashRequest(): ComposeFlashRequest? {
        return when (method) {
            METHOD_PATCH -> data.value?.let {
                ComposeFlashRequest(
                    request = FlashRequest.patch(it),
                )
            }

            METHOD_DIRECT -> ComposeFlashRequest(
                request = FlashRequest.flash(isSecondSlot = false),
            )

            METHOD_INACTIVE_SLOT -> ComposeFlashRequest(
                request = FlashRequest.flash(isSecondSlot = true),
            )

            METHOD_DOWNLOAD -> {
                if (isValidUrl(downloadUrl)) {
                    ComposeFlashRequest(
                        request = FlashRequest.download(downloadUrl),
                    )
                } else null
            }

            else -> null
        }
    }

    override fun onSaveState(state: Bundle) {
        state.putParcelable(
            INSTALL_STATE_KEY, InstallState(
                methodId,
                step,
                Config.keepVerity,
                Config.keepEnc,
                Config.recovery
            )
        )
    }

    override fun onRestoreState(state: Bundle) {
        BundleCompat.getParcelable(state, INSTALL_STATE_KEY, InstallState::class.java)?.let {
            setMethod(it.method, showWarning = false)
            step = it.step
            Config.keepVerity = it.keepVerity
            Config.keepEnc = it.keepEnc
            Config.recovery = it.recovery
        }
    }

    @Parcelize
    class InstallState(
        val method: Int,
        val step: Int,
        val keepVerity: Boolean,
        val keepEnc: Boolean,
        val recovery: Boolean,
    ) : Parcelable

    data class ComposeFlashRequest(
        val request: FlashRequest,
    )

}
