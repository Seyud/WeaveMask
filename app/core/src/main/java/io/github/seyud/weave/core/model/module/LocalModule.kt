package io.github.seyud.weave.core.model.module

import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.utils.RootUtils
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import java.util.Locale

data class LocalModule(
    val base: ExtendedFile,
) : Module() {
    private val svc get() = ServiceLocator.networkService

    override var id: String = ""
    override var name: String = ""
    override var version: String = ""
    override var versionCode: Int = -1
    var author: String = ""
    var description: String = ""
    var updateInfo: OnlineModule? = null
    var outdated = false
    var actionIconPath: String? = null
    var webUiIconPath: String? = null
    var banner: String = ""
    private var updateUrl: String = ""

    private val removeFile = base.getChildFile("remove")
    private val disableFile = base.getChildFile("disable")
    private val updateFile = base.getChildFile("update")
    val zygiskFolder = base.getChildFile("zygisk")

    val updated get() = updateFile.exists()
    val isRiru = (id == "riru-core") || base.getChildFile("riru").exists()
    val isZygisk = zygiskFolder.exists()
    val zygiskUnloaded = zygiskFolder.getChildFile("unloaded").exists()
    val hasAction = base.getChildFile("action.sh").exists()
    val hasWebUi = base.getChildFile("webroot").exists()

    var enable: Boolean
        get() = !disableFile.exists()
        set(enable) {
            if (enable) {
                disableFile.delete()
                Shell.cmd("copy_preinit_files").submit()
            } else {
                !disableFile.createNewFile()
                Shell.cmd("copy_preinit_files").submit()
            }
        }

    var remove: Boolean
        get() = removeFile.exists()
        set(remove) {
            if (remove) {
                if (updateFile.exists()) return
                removeFile.createNewFile()
                Shell.cmd("copy_preinit_files").submit()
            } else {
                removeFile.delete()
                Shell.cmd("copy_preinit_files").submit()
            }
        }

    private fun resolveModuleFilePath(path: String): String? {
        val normalized = path.trim()
        if (normalized.isBlank()) {
            return null
        }
        if (normalized.startsWith("/")) {
            return normalized
        }
        return base.getChildFile(normalized).path
    }

    @Throws(NumberFormatException::class)
    private fun parseProps(props: List<String>) {
        for (line in props) {
            val prop = line.split("=".toRegex(), 2).map { it.trim() }
            if (prop.size != 2)
                continue

            val key = prop[0]
            val value = prop[1]
            if (key.isEmpty() || key[0] == '#')
                continue

            when (key) {
                "id" -> id = value
                "name" -> name = value
                "version" -> version = value
                "versionCode" -> versionCode = value.toInt()
                "author" -> author = value
                "description" -> description = value
                "updateJson" -> updateUrl = value
                "actionIcon" -> actionIconPath = resolveModuleFilePath(value)
                "webuiIcon" -> webUiIconPath = resolveModuleFilePath(value)
                "banner" -> banner = if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
                    value
                } else {
                    resolveModuleFilePath(value) ?: ""
                }
            }
        }
    }

    init {
        runCatching {
            parseProps(Shell.cmd("dos2unix < $base/module.prop").exec().out)
        }

        if (id.isEmpty()) {
            id = base.name
        }

        if (name.isEmpty()) {
            name = id
        }
    }

    suspend fun fetch(): Boolean {
        if (updateUrl.isEmpty())
            return false

        try {
            val json = svc.fetchModuleJson(updateUrl)
            updateInfo = OnlineModule(this, json)
            outdated = json.versionCode > versionCode
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The URL and the JSON come from the module, so anything can be wrong with them,
            // e.g. a malformed URL makes Retrofit throw IllegalArgumentException
            Timber.w(e)
        }

        return false
    }

    companion object {

        fun loaded() = RootUtils.fs.getFile(Const.MODULE_PATH).exists()

        suspend fun installed() = withContext(Dispatchers.IO) {
            RootUtils.fs.getFile(Const.MODULE_PATH)
                .listFiles()
                .orEmpty()
                .filter { !it.isFile && !it.isHidden }
                .map { LocalModule(it) }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }
}
