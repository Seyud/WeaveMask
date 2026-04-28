package io.github.seyud.weave.ui.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import androidx.core.os.ProcessCompat
import com.topjohnwu.superuser.Shell
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.ktx.concurrentMap
import io.github.seyud.weave.core.utils.InstalledPackageLoader
import io.github.seyud.weave.ui.deny.CmdlineListItem
import io.github.seyud.weave.ui.deny.ISOLATED_MAGIC
import io.github.seyud.weave.ui.deny.buildDenyListAppInfo
import io.github.seyud.weave.ui.deny.fetchProcesses
import io.github.seyud.weave.ui.superuser.isInstalledPackage
import io.github.seyud.weave.ui.superuser.isSystemApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.withContext

internal data class DenyListEntryRecord(
    val packageName: String,
    val processName: String = packageName,
) {
    fun rawLine(): String = if (processName == packageName) packageName else "$packageName|$processName"

    companion object {
        fun parse(rawLine: String): DenyListEntryRecord? {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                return null
            }
            val split = trimmed.split(Regex("\\|"), 2)
            val packageName = split[0].trim()
            if (packageName.isEmpty()) {
                return null
            }
            val processName = split.getOrElse(1) { packageName }.trim().ifEmpty { packageName }
            return DenyListEntryRecord(
                packageName = packageName,
                processName = processName,
            )
        }
    }
}

internal data class BlacklistDenyListSnapshot(
    val enabled: Boolean,
    val entries: List<DenyListEntryRecord>,
)

internal data class WhitelistModeDenyListResult(
    val success: Boolean,
    val denyListEnabled: Boolean,
)

internal interface BlacklistDenyListSnapshotStore {
    fun get(): BlacklistDenyListSnapshot?
    fun set(snapshot: BlacklistDenyListSnapshot?)
}

private object ConfigBlacklistDenyListSnapshotStore : BlacklistDenyListSnapshotStore {
    override fun get(): BlacklistDenyListSnapshot? {
        if (!Config.suListModeDenyListSnapshotValid) {
            return null
        }
        return BlacklistDenyListSnapshot(
            enabled = Config.suListModeDenyListSnapshotEnabled,
            entries = Config.suListModeDenyListSnapshot
                .lineSequence()
                .mapNotNull(DenyListEntryRecord::parse)
                .toList(),
        )
    }

    override fun set(snapshot: BlacklistDenyListSnapshot?) {
        if (snapshot == null) {
            Config.suListModeDenyListSnapshot = ""
            Config.suListModeDenyListSnapshotEnabled = false
            Config.suListModeDenyListSnapshotValid = false
            return
        }
        Config.suListModeDenyListSnapshot = snapshot.entries.joinToString(separator = "\n") { it.rawLine() }
        Config.suListModeDenyListSnapshotEnabled = snapshot.enabled
        Config.suListModeDenyListSnapshotValid = true
    }
}

internal data class DenyListShellResult(
    val code: Int,
    val out: List<String>,
) {
    val isSuccess: Boolean
        get() = code == 0
}

internal interface DenyListShellRunner {
    fun run(command: String): DenyListShellResult
    fun runAll(commands: List<String>): DenyListShellResult
}

private object LibSuDenyListShellRunner : DenyListShellRunner {
    override fun run(command: String): DenyListShellResult {
        val result = Shell.cmd(command).exec()
        return DenyListShellResult(
            code = result.code,
            out = result.out,
        )
    }

    override fun runAll(commands: List<String>): DenyListShellResult {
        if (commands.isEmpty()) {
            return DenyListShellResult(code = 0, out = emptyList())
        }
        val result = Shell.cmd(*commands.toTypedArray()).exec()
        return DenyListShellResult(
            code = result.code,
            out = result.out,
        )
    }
}

internal interface OrdinaryDenyListEntryProvider {
    suspend fun loadEntries(currentEntries: List<DenyListEntryRecord>): List<DenyListEntryRecord>
    suspend fun loadEntriesForPackage(
        packageName: String,
        currentEntries: List<DenyListEntryRecord>,
    ): List<DenyListEntryRecord>
}

internal class PackageManagerOrdinaryDenyListEntryProvider(
    private val packageManager: PackageManager = AppContext.packageManager,
) : OrdinaryDenyListEntryProvider {

    override suspend fun loadEntries(currentEntries: List<DenyListEntryRecord>): List<DenyListEntryRecord> =
        withContext(Dispatchers.IO) {
            val denyEntries = currentEntries.map { CmdlineListItem(it.rawLine()) }

            InstalledPackageLoader.loadApplications(
                flags = MATCH_UNINSTALLED_PACKAGES,
                packageManager = packageManager,
            ).items
                .asFlow()
                .filter { it.packageName != AppContext.packageName }
                .filter { isInstalledPackage(it) }
                .filter { ProcessCompat.isApplicationUid(it.uid) }
                .filterNot { isSystemApp(it) }
                .concurrentMap { appInfo -> buildEntriesForApplication(appInfo, denyEntries) }
                .toCollection(ArrayList<List<DenyListEntryRecord>>())
                .asSequence()
                .flatten()
                .distinct()
                .sortedWith(compareBy<DenyListEntryRecord>({ it.packageName }, { it.processName }))
                .toList()
        }

    override suspend fun loadEntriesForPackage(
        packageName: String,
        currentEntries: List<DenyListEntryRecord>,
    ): List<DenyListEntryRecord> = withContext(Dispatchers.IO) {
        val denyEntries = currentEntries.map { CmdlineListItem(it.rawLine()) }
        val applicationInfo = InstalledPackageLoader.loadApplications(
            flags = MATCH_UNINSTALLED_PACKAGES,
            packageManager = packageManager,
        ).items.firstOrNull { it.packageName == packageName }
            ?: return@withContext emptyList()
        if (!isOrdinaryApplication(applicationInfo)) {
            return@withContext emptyList()
        }
        buildEntriesForApplication(applicationInfo, denyEntries)
    }

    private fun isOrdinaryApplication(applicationInfo: ApplicationInfo): Boolean =
        applicationInfo.packageName != AppContext.packageName &&
            isInstalledPackage(applicationInfo) &&
            ProcessCompat.isApplicationUid(applicationInfo.uid) &&
            !isSystemApp(applicationInfo)

    private fun buildEntriesForApplication(
        applicationInfo: ApplicationInfo,
        denyEntries: List<CmdlineListItem>,
    ): List<DenyListEntryRecord> {
        val app = buildDenyListAppInfo(applicationInfo, packageManager, denyEntries)
        return buildList<DenyListEntryRecord> {
            add(DenyListEntryRecord(app.packageName))
            fetchProcesses(packageManager, app, denyEntries)
                .asSequence()
                .filter { it.defaultSelection }
                .map { DenyListEntryRecord(it.packageName, it.name) }
                .filterNot { it.processName == it.packageName }
                .forEach(::add)
        }
    }
}

internal class WhitelistModeDenyListCoordinator(
    private val shellRunner: DenyListShellRunner = LibSuDenyListShellRunner,
    private val entryProvider: OrdinaryDenyListEntryProvider = PackageManagerOrdinaryDenyListEntryProvider(),
    private val snapshotStore: BlacklistDenyListSnapshotStore = ConfigBlacklistDenyListSnapshotStore,
) {

    suspend fun applyWhitelistMode(): WhitelistModeDenyListResult = withContext(Dispatchers.IO) {
        val currentEntries = listEntries()
        val blacklistSnapshot = snapshotStore.get() ?: BlacklistDenyListSnapshot(
            enabled = isDenyListEnabled(),
            entries = currentEntries,
        ).also(snapshotStore::set)

        if (!blacklistSnapshot.enabled && !setDenyListEnabled(true)) {
            snapshotStore.set(null)
            return@withContext failureResult(blacklistSnapshot.enabled)
        }

        val targetEntries = entryProvider.loadEntries(currentEntries)
        val existingEntries = currentEntries.toMutableSet()
        val entriesToAdd = targetEntries.filter(existingEntries::add)
        if (!addEntries(entriesToAdd)) {
            val rollback = restoreSnapshot(blacklistSnapshot)
            if (rollback.success) {
                snapshotStore.set(null)
            }
            return@withContext rollback.copy(success = false)
        }

        return@withContext WhitelistModeDenyListResult(
            success = true,
            denyListEnabled = true,
        )
    }

    suspend fun restoreBlacklistMode(): WhitelistModeDenyListResult = withContext(Dispatchers.IO) {
        val snapshot = snapshotStore.get()
            ?: return@withContext WhitelistModeDenyListResult(
                success = true,
                denyListEnabled = isDenyListEnabled(),
            )

        val result = restoreSnapshot(snapshot)
        if (result.success) {
            snapshotStore.set(null)
        }
        result
    }

    suspend fun ensurePackageSynced(packageName: String): WhitelistModeDenyListResult = withContext(Dispatchers.IO) {
        val currentEntries = listEntries()
        val targetEntries = entryProvider.loadEntriesForPackage(packageName, currentEntries)
        if (targetEntries.isEmpty()) {
            return@withContext WhitelistModeDenyListResult(
                success = true,
                denyListEnabled = isDenyListEnabled(),
            )
        }

        val wasEnabled = isDenyListEnabled()
        if (!wasEnabled && !setDenyListEnabled(true)) {
            return@withContext failureResult(wasEnabled)
        }

        val existingEntries = currentEntries.toMutableSet()
        val entriesToAdd = targetEntries.filter(existingEntries::add)
        if (entriesToAdd.isNotEmpty() && !addEntries(entriesToAdd)) {
            if (!wasEnabled) {
                setDenyListEnabled(false)
            }
            return@withContext failureResult(wasEnabled)
        }

        return@withContext WhitelistModeDenyListResult(
            success = true,
            denyListEnabled = true,
        )
    }

    private fun restoreSnapshot(snapshot: BlacklistDenyListSnapshot): WhitelistModeDenyListResult {
        if (!clearEntries(listEntries())) {
            return failureResult(snapshot.enabled)
        }
        if (!addEntries(snapshot.entries)) {
            return failureResult(snapshot.enabled)
        }
        if (!setDenyListEnabled(snapshot.enabled)) {
            return failureResult(snapshot.enabled)
        }
        return WhitelistModeDenyListResult(
            success = true,
            denyListEnabled = snapshot.enabled,
        )
    }

    private fun listEntries(): List<DenyListEntryRecord> =
        shellRunner.run("magisk --denylist ls").out.mapNotNull(DenyListEntryRecord::parse)

    private fun clearEntries(entries: List<DenyListEntryRecord>): Boolean {
        val packageCommands = entries.asSequence()
            .map { it.packageName }
            .filter { it != ISOLATED_MAGIC }
            .distinct()
            .map { packageName -> "magisk --denylist rm ${shellQuote(packageName)}" }
            .toList()
        if (!shellRunner.runAll(packageCommands).isSuccess) {
            return false
        }

        val isolatedCommands = entries.asSequence()
            .filter { it.packageName == ISOLATED_MAGIC }
            .distinct()
            .map { entry ->
                "magisk --denylist rm ${shellQuote(entry.packageName)} ${shellQuote(entry.processName)}"
            }
            .toList()
        return shellRunner.runAll(isolatedCommands).isSuccess
    }

    private fun addEntries(entries: List<DenyListEntryRecord>): Boolean =
        shellRunner.runAll(
            entries.map { entry ->
                "magisk --denylist add ${shellQuote(entry.packageName)} ${shellQuote(entry.processName)}"
            },
        ).isSuccess

    private fun setDenyListEnabled(enabled: Boolean): Boolean {
        val command = if (enabled) "enable" else "disable"
        return shellRunner.run("magisk --denylist $command").isSuccess
    }

    private fun isDenyListEnabled(): Boolean =
        shellRunner.run("magisk --denylist status").isSuccess

    private fun failureResult(fallbackEnabled: Boolean): WhitelistModeDenyListResult =
        WhitelistModeDenyListResult(
            success = false,
            denyListEnabled = runCatching(::isDenyListEnabled).getOrDefault(fallbackEnabled),
        )

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\\''")}'"
}
