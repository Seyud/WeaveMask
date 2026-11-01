package io.github.seyud.weave.core.script

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.registry.DownloadVerificationException
import io.github.seyud.weave.core.registry.ExpectedFile
import io.github.seyud.weave.core.registry.RegistryDownloadStore
import io.github.seyud.weave.core.registry.copyForInstall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/** A validated script, extracted and ready to run. */
data class PreparedScript(
    val manifest: ScriptManifest,
    /** Extraction directory: the script's working directory and `$MODPATH`. */
    val dir: File,
    val sha256: String,
    val params: ScriptParams,
    val actionSource: String,
    val actionSourceTruncated: Boolean,
)

/** Exit code and classification of one run. */
data class ScriptRunResult(val exitCode: Int, val outcome: ScriptOutcome)

/** The page released the extraction while it was being prepared; nobody waits for the result. */
class ReleasedException : IOException("Extraction released during preparation")

/** The registry ZIP is not (or no longer) a verified download of the store. */
class UnverifiedScriptException : IOException("Registry download is not verified")

object ScriptRepository {
    private const val TAG = "ScriptRepository"
    const val DIR_NAME = "oneshot"
    private const val PREFS = "oneshot_scripts"
    private const val MAX_SOURCE_CHARS = 256 * 1024

    private fun root(): File = File(AppContext.filesDir, DIR_NAME)

    /** Root of the app-private store registry ZIPs are downloaded into. */
    private fun storeRoot(): File = File(AppContext.filesDir, RegistryDownloadStore.DIR_NAME)

    /**
     * Extraction held by each owner (one preparation of a run page); everything else under
     * [root] is stale. Guarded by itself together with [released].
     */
    private val extractions = HashMap<String, File>()

    /** Owners released before their preparation registered an extraction. */
    private val released = HashSet<String>()

    /** Deletes released extractions off the caller's (often the main) thread. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serialises preparations, so cleanup never sees another preparation's half-done files. */
    private val prepareLock = Mutex()

    val history: ScriptRunHistory by lazy {
        val prefs = AppContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ScriptRunHistory(object : ScriptRunStorage {
            override fun read(key: String): String? = prefs.getString(key, null)
            override fun write(key: String, value: String) {
                prefs.edit { putString(key, value) }
            }
        })
    }

    /**
     * Host information exported to the script. WeaveMask's version comes from `magisk -v` /
     * `magisk -V` (see [Info.init]), which is exactly what `MAGISK_VER` / `MAGISK_VER_CODE` mean
     * in Magisk's own scripts — upstream read the KernelSU manager's BuildConfig instead.
     */
    fun hostInfo(): ScriptHostInfo = ScriptHostInfo(
        magiskVersion = Info.env.versionString,
        magiskVersionCode = Info.env.versionCode,
    )

    /** module.prop of [uri] if it is a one-shot script ZIP, otherwise (or when unreadable) null. */
    suspend fun peekOneshot(uri: Uri): ScriptManifest? = withContext(Dispatchers.IO) {
        runCatching {
            AppContext.contentResolver.openInputStream(uri)?.use { ScriptPackage.readOneshotManifest(it) }
        }.getOrNull()
    }

    /**
     * Copies, verifies, validates and extracts the ZIP at [uri]. With [registryId] the ZIP must
     * be a verified registry download (re-hashed here like a module install) whose module.prop
     * id equals [registryId]; [expected] is the size and SHA-256 of that release, used when
     * [uri] is not a file of `RegistryDownloadStore` (a MediaStore download). The extraction
     * belongs to [owner], a token unique to this
     * preparation, until [release]; releasing it earlier (even before this returns) drops it.
     */
    suspend fun prepare(
        uri: Uri,
        registryId: String?,
        owner: String,
        expected: ExpectedFile? = null,
    ): PreparedScript = withContext(Dispatchers.IO) {
        try {
            prepareLock.withLock { prepareLocked(uri, registryId, owner, expected) }
        } finally {
            // The token is never reused: forget an early release that found nothing to drop.
            synchronized(extractions) { released.remove(owner) }
        }
    }

    private fun prepareLocked(
        uri: Uri,
        registryId: String?,
        owner: String,
        callerExpected: ExpectedFile? = null,
    ): PreparedScript {
        val store = storeRoot()
        // A file inside the download store carries its own expectation; otherwise the store
        // screen passes the size and SHA-256 of the release it downloaded, and those are
        // checked against the bytes copied here before anything is validated or extracted.
        val storeExpected = if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { RegistryDownloadStore.expectedFor(store, File(it)) }
        } else {
            null
        }
        val expected = storeExpected ?: callerExpected
        if (registryId != null && expected == null) throw UnverifiedScriptException()

        val zip = File.createTempFile("oneshot-", ".zip", AppContext.cacheDir)
        try {
            try {
                val input = AppContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
                // A local source has no verified size and may never end.
                input.use { copyForInstall(if (expected == null) ScriptPackage.bounded(it, ScriptPackage.MAX_ZIP_BYTES) else it, zip, expected) }
            } catch (e: DownloadVerificationException) {
                // Corrupted or tampered after download: never keep it around for a retry.
                if (storeExpected != null) uri.path?.let { RegistryDownloadStore.delete(store, File(it)) }
                throw e
            }
            val manifest = ScriptPackage.validate(zip, registryId)
            val sha256 = sha256(zip)
            // One directory per preparation: the same ZIP may be open on two run pages at once.
            val dir = File(root(), "$sha256-${UUID.randomUUID()}")
            synchronized(extractions) {
                if (released.remove(owner)) throw ReleasedException()
                extractions[owner] = dir
            }
            cleanup()
            try {
                ScriptPackage.extract(zip, dir)
                val prepared = readPrepared(manifest, dir, sha256)
                // Released while extracting: release() may have run before the directory existed.
                if (synchronized(extractions) { extractions[owner] != dir }) throw ReleasedException()
                return prepared
            } catch (e: Throwable) {
                release(owner)
                delete(dir)
                throw e
            }
        } finally {
            zip.delete()
        }
    }

    private fun readPrepared(manifest: ScriptManifest, dir: File, sha256: String): PreparedScript {
        val paramsFile = File(dir, ScriptPackage.PARAMS)
        val params = if (paramsFile.isFile) {
            ScriptPackage.parseParams(paramsFile.readText())
        } else {
            ScriptParams.EMPTY
        }
        // Read at most one char past the limit: action.sh itself may be up to the ZIP size cap.
        val source = File(dir, ScriptPackage.ACTION).bufferedReader().use { reader ->
            val buf = CharArray(MAX_SOURCE_CHARS + 1)
            var n = 0
            while (n < buf.size) {
                val read = reader.read(buf, n, buf.size - n)
                if (read == -1) break
                n += read
            }
            String(buf, 0, n)
        }
        return PreparedScript(
            manifest = manifest,
            dir = dir,
            sha256 = sha256,
            params = params,
            actionSource = source.take(MAX_SOURCE_CHARS),
            actionSourceTruncated = source.length > MAX_SOURCE_CHARS,
        )
    }

    /**
     * Drops the extraction of [owner] (page left, or the preparation failed). Not for a running
     * script: the run removes its directory as root, since the script may create root-owned files.
     */
    fun release(owner: String) {
        val dir = synchronized(extractions) {
            extractions.remove(owner) ?: run { released.add(owner); null }
        } ?: return
        ioScope.launch { delete(dir) }
    }

    private val _runCount = MutableStateFlow(0)

    /** Bumped after every recorded run, so screens showing the last run can refresh. */
    val runCount: StateFlow<Int> = _runCount.asStateFlow()

    fun recordRun(prepared: PreparedScript, exitCode: Int) {
        history.record(
            ScriptRunRecord(
                id = prepared.manifest.id,
                versionCode = prepared.manifest.versionCode,
                time = System.currentTimeMillis(),
                exitCode = exitCode,
            )
        )
        _runCount.update { it + 1 }
    }

    /**
     * Runs [prepared] once with the user's [values]: builds the command (see [ScriptCommand]),
     * executes it in a throwaway root shell, records the run in [history], deletes the extraction
     * as root and releases [owner].
     *
     * Throws [IllegalArgumentException] when a value does not pass
     * [ScriptParams.validate] — nothing is run and nothing is released in that case.
     */
    suspend fun run(
        prepared: PreparedScript,
        values: Map<String, String>,
        owner: String,
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): ScriptRunResult {
        val invalid = prepared.params.validate(values)
        require(invalid.isEmpty()) { "Invalid parameters: $invalid" }
        val dir = prepared.dir.absolutePath
        val command = ScriptCommand.build(
            dir,
            ScriptCommand.environment(hostInfo(), prepared.manifest.id, dir, prepared.params, values),
        )
        val exitCode = ScriptRunner.exec(command, onStdout, onStderr)
        recordRun(prepared, exitCode)
        // Nothing stays behind: the script may have created root-owned files in its directory.
        if (!ScriptRunner.removeAsRoot(dir)) Timber.w("$TAG: cannot remove $dir")
        release(owner)
        return ScriptRunResult(exitCode, ScriptOutcome.of(exitCode))
    }

    /** Drops extractions no page holds, e.g. left behind by a killed process. */
    private fun cleanup() {
        val held = synchronized(extractions) { extractions.values.mapTo(HashSet()) { it.name } }
        root().listFiles()?.forEach { f -> if (f.name !in held) delete(f) }
    }

    /**
     * Deletes [dir] without following symlinks (a script may have linked to anywhere), and as
     * root when the app cannot (root-owned files left by a run whose own cleanup failed).
     */
    @OptIn(ExperimentalPathApi::class)
    private fun delete(dir: File) {
        val path = dir.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        try {
            path.deleteRecursively()
        } catch (e: IOException) {
            Timber.w(e, "cannot delete $dir, retrying as root")
            if (!ScriptRunner.removeAsRoot(dir.absolutePath)) Timber.w("cannot remove $dir as root")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
