package io.github.seyud.weave.core.tasks

import android.net.Uri
import androidx.core.net.toFile
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.ktx.shellQuoted
import io.github.seyud.weave.core.utils.MediaStoreUtils.displayName
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

open class FlashZip(
    private val mUri: Uri,
    private val console: MutableList<String>,
    private val logs: MutableList<String>,
    /**
     * 真正执行 update-binary 的执行器。默认走普通 root shell（无 PTY）；
     * UI 侧传 PTY 版本（[io.github.seyud.weave.terminal.runSuCommand]），安装器才能
     * 拿到真实终端宽度、进度条与交互提示，和上游 FlashViewModel 一致。
     */
    private val runner: suspend (command: String) -> Boolean = { command ->
        Shell.cmd(command).to(console, logs).exec().isSuccess
    },
) {

    private val installDir = File(AppContext.cacheDir, "flash")
    private lateinit var zipFile: File

    @Throws(IOException::class)
    private suspend fun flash(): Boolean {
        installDir.deleteRecursively()
        installDir.mkdirs()

        zipFile = if (mUri.scheme == "file") {
            mUri.toFile()
        } else {
            File(installDir, "install.zip").also {
                console.add("- Copying zip to temp directory")
                try {
                    // Use ContentResolver from AppContext to open input stream
                    // The temporary read permission is granted to the app when receiving the Intent
                    AppContext.contentResolver.openInputStream(mUri)?.use { input ->
                        it.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw FileNotFoundException("Cannot open input stream for: $mUri")
                } catch (e: IOException) {
                    when (e) {
                        is FileNotFoundException -> console.add("! Invalid Uri: ${e.message}")
                        else -> console.add("! Cannot copy to cache: ${e.message}")
                    }
                    throw e
                }
            }
        }

        val binary = File(installDir, "update-binary")
        try {
            AppContext.assets.open("module_installer.sh").use { it.copyTo(binary.outputStream()) }
        } catch (e: IOException) {
            console.add("! Unzip error")
            throw e
        }

        console.add("- Installing ${mUri.displayName}")

        // 路径来自外部（URI/缓存目录），按 shell 单词引用，别让 shell 解释
        return runner("sh ${binary.path.shellQuoted()} dummy 1 ${zipFile.path.shellQuoted()}")
    }

    open suspend fun exec() = withContext(Dispatchers.IO) {
        try {
            if (!flash()) {
                console.add("! Installation failed")
                false
            } else {
                true
            }
        } catch (e: Throwable) {
            Timber.e(e)
            val message = e.message
            if (!message.isNullOrBlank()) {
                console.add("! $message")
            }
            false
        } finally {
            Shell.cmd("cd /", "rm -rf $installDir ${Const.TMPDIR}").submit()
            cleanupSourceIfNeeded()
        }
    }

    private fun cleanupSourceIfNeeded() {
        if (mUri.scheme != "file") return

        val sourceFile = runCatching { mUri.toFile().canonicalFile }.getOrNull() ?: return
        val cacheRoots = listOf(
            File(AppContext.cacheDir, "module_install"),
            File(AppContext.cacheDir, "external_module"),
        ).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }

        val sourcePath = sourceFile.path
        val safeRoot = cacheRoots.firstOrNull { root ->
            sourcePath == root.path || sourcePath.startsWith(root.path + File.separator)
        } ?: return

        val parent = sourceFile.parentFile?.canonicalFile ?: return
        if (parent == safeRoot) return
        if (!parent.path.startsWith(safeRoot.path + File.separator)) return

        parent.deleteRecursively()
    }
}
