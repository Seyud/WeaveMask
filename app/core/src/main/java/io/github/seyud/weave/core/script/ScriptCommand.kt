package io.github.seyud.weave.core.script

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import io.github.seyud.weave.core.ktx.shellQuoted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * What the manager knows about the running WeaveMask, exported to the script.
 *
 * Upstream (KernelSU) exports `KSU_VER` / `KSU_VER_CODE` / `KSU_KERNEL_VER_CODE` plus the
 * late-load flag; none of those exist here, so this fork exports the Magisk flavour instead:
 * `MAGISK_VER` and `MAGISK_VER_CODE`, both taken from [io.github.seyud.weave.core.Info].
 */
data class ScriptHostInfo(
    val magiskVersion: String,
    val magiskVersionCode: Int,
)

/**
 * Builds the shell command that runs a one-shot script's action.sh. Every variable value is
 * single-quoted; the interpreter paths are left to the shell to expand.
 */
object ScriptCommand {
    const val ENTRY = "action.sh"

    /**
     * Magisk keeps busybox in the tmpfs mount it booted itself into. The path is only known at
     * runtime, so the command carries a shell expansion instead of a literal: `$MAGISKTMP` is
     * exported by [io.github.seyud.weave.core.utils.ShellInit] for the app's root shells, and the
     * `magisk --path` fallback covers a shell built without that initializer (see
     * [ScriptRunner], which builds a throwaway shell per run).
     */
    private const val MAGISKTMP = "\"\${MAGISKTMP:-\$(magisk --path)}\""

    /** Appended to `$PATH`: Magisk's applet symlink farm (`${MAGISKTMP}/.magisk/busybox`). */
    val BIN_DIR: String = "$MAGISKTMP/.magisk/busybox"

    /** The standalone busybox binary that runs action.sh. */
    val BUSYBOX: String = "$MAGISKTMP/.magisk/busybox/busybox"

    /**
     * Name of the variable holding the module id. Upstream calls it `KSU_MODULE`, which this
     * fork does not export (no `KSU_*` variables); `MODULE_ID` is the fork-neutral replacement.
     */
    const val ENV_MODULE_ID = "MODULE_ID"

    /** Name of the one-shot marker (upstream: `KSU_ONESHOT=1`). */
    const val ENV_ONESHOT = "MODULE_ONESHOT"

    /**
     * Prefix the KernelSU manager uses for parameters. Exported as an alias next to
     * [ScriptParam.ENV_PREFIX] because published registry scripts were written against it.
     */
    const val LEGACY_PARAM_PREFIX = "KSU_PARAM_"

    private val ENV_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    /** Quotes [value] for a POSIX shell: `it's` -> `'it'\''s'`. */
    fun quote(value: String): String {
        require('\u0000' !in value) { "NUL cannot be passed through a shell" }
        return "'" + value.replace("'", "'\\''") + "'"
    }

    /**
     * Environment of a run, in order. [values] are the user's inputs per param key (missing
     * keys use the default); throws [IllegalArgumentException] if any of them is invalid.
     *
     * Order (part of the contract, the tests pin it):
     * `ASH_STANDALONE`, `MAGISK_VER`, `MAGISK_VER_CODE`, `MODULE_ID`, `MODULE_ONESHOT`,
     * `MODPATH`, then one `MODULE_PARAM_<KEY>` per parameter in declaration order, each followed
     * by its `KSU_PARAM_<KEY>` alias.
     *
     * The alias exists because the registry's own scripts (and most published ones) were written
     * for the KernelSU manager, which exports `KSU_PARAM_*`; without it their parameter reads
     * silently fall back to the script's own defaults. Only the parameter namespace is aliased —
     * no `KSU` / `KSU_VER` state variables — so a script that branches on `$KSU` still takes its
     * plain-Magisk path instead of looking for a `ksud` that does not exist here.
     */
    fun environment(
        host: ScriptHostInfo,
        moduleId: String,
        dir: String,
        params: ScriptParams,
        values: Map<String, String>,
    ): List<Pair<String, String>> {
        val env = mutableListOf(
            // Magisk's own scripts always run under standalone busybox ash.
            "ASH_STANDALONE" to "1",
            "MAGISK_VER" to host.magiskVersion,
            "MAGISK_VER_CODE" to host.magiskVersionCode.toString(),
            ENV_MODULE_ID to moduleId,
            ENV_ONESHOT to "1",
            "MODPATH" to dir,
        )
        for (param in params.params) {
            val value = values[param.key] ?: param.defaultValue
            param.validate(value)?.let { throw IllegalArgumentException("${param.key}: $it") }
            val canonical = param.canonical(value)
            env += param.envName to canonical
            env += LEGACY_PARAM_PREFIX + param.key to canonical
        }
        return env
    }

    /** `cd <dir> && <env...> PATH=... busybox sh ./action.sh </dev/null` */
    fun build(dir: String, env: List<Pair<String, String>>): String = buildString {
        append("cd ").append(quote(dir)).append(" && ")
        for ((name, value) in env) {
            require(ENV_NAME.matches(name) && name != "PATH") { "Invalid variable name: $name" }
            append(name).append('=').append(quote(value)).append(' ')
        }
        append("PATH=\"\$PATH\":").append(BIN_DIR).append(' ')
        append(BUSYBOX).append(" sh ./").append(ENTRY)
        // libsu feeds commands through the shell's stdin: the script must never read from it.
        append(" </dev/null")
    }
}

/**
 * Executes commands built by [ScriptCommand]. A throwaway root shell per run: the script gets a
 * clean `cd` / export state, and mount changes still land in the global namespace like a module
 * action (upstream KernelSU does the same with `ksud`).
 */
object ScriptRunner {
    /** Exit code reported when no root shell could be opened at all. */
    const val EXIT_NO_SHELL = -1

    /**
     * Runs [command] to completion and returns its exit code. [onStdout] / [onStderr] are called
     * from the shell's reader thread, line by line, so callers must not assume the main thread.
     */
    suspend fun exec(
        command: String,
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        try {
            // A new shell, not the shared default one: `cd` and exports of a run must not leak
            // into the app's interactive shell.
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .build()
                .use { shell ->
                    val stdout = object : CallbackList<String>() {
                        override fun onAddElement(e: String?) {
                            onStdout(e ?: "")
                        }
                    }
                    val stderr = object : CallbackList<String>() {
                        override fun onAddElement(e: String?) {
                            onStderr(e ?: "")
                        }
                    }
                    shell.newJob().add(command).to(stdout, stderr).exec().code
                }
        } catch (e: IOException) {
            Timber.w(e, "cannot run one-shot script")
            EXIT_NO_SHELL
        }
    }

    /**
     * `rm -rf` as root, for files a script left behind: it may have created root-owned files in
     * its extraction directory that the app cannot delete itself.
     */
    fun removeAsRoot(path: String): Boolean = runCatching {
        val shell = Shell.getShell() ?: return@runCatching false
        ShellUtils.fastCmdResult(shell, "rm -rf ${path.shellQuoted()}")
    }.onFailure { Timber.w(it, "cannot remove $path as root") }.getOrDefault(false)
}

/** Result of a run, by exit code. */
enum class ScriptOutcome {
    SUCCESS,

    /** Exit code 2: the script decided it does not apply to this device. */
    NOT_APPLICABLE,
    FAILURE;

    companion object {
        const val EXIT_NOT_APPLICABLE = 2

        fun of(exitCode: Int): ScriptOutcome = when (exitCode) {
            0 -> SUCCESS
            EXIT_NOT_APPLICABLE -> NOT_APPLICABLE
            else -> FAILURE
        }
    }
}
