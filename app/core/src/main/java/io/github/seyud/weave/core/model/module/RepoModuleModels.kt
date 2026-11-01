package io.github.seyud.weave.core.model.module

import java.text.Collator
import java.util.Locale

/** An author of a registry entry; [link] is empty when the registry has no profile URL. */
data class Author(
    val name: String,
    val link: String,
)

/** A module of the registry index (list screen row). */
data class RepoModule(
    val moduleId: String,
    val moduleName: String,
    /** Authors pre-joined for list display and search. */
    val authors: String,
    val authorList: List<Author>,
    val summary: String,
    val tags: List<String>,
    val metamodule: Boolean,
    /** A one-shot script (registry `kind: script`): run by the manager, never installed. */
    val isScript: Boolean = false,
    val iconUrl: String?,
    val latestVersion: String,
    val latestVersionCode: Int,
    /** Formatted publish date of the stable release. */
    val latestReleaseTime: String,
    /** Epoch millis of the stable release, used for sorting. */
    val latestReleaseEpoch: Long,
    /** GitHub stargazer count, or null when the registry doesn't know it. */
    val stars: Int? = null,
    /** "owner/name" of the repository [stars] belongs to. */
    val starsRepository: String? = null,
) {
    val displayName: String get() = moduleName.ifBlank { moduleId }
}

/** The whole index. [error] is non-null when the last good cached copy is shown instead. */
data class RepoModuleList(
    val modules: List<RepoModule>,
    val buildId: String,
    val stale: Boolean,
    val error: Throwable?,
)

/** A stable (non-prerelease), immutable release; its ZIP is verified after download. */
data class RepoRelease(
    val tag: String,
    val name: String,
    val version: String,
    val versionCode: Int,
    val publishedAt: String,
    val changelogHtml: String?,
    val zipUrl: String,
    val zipName: String,
    val zipSize: Long,
    val sha256: String,
    val tagCommit: String,
    /** https only (validated by the registry parser). */
    val releaseUrl: String,
    /** https only, or null. */
    val attestationUrl: String?,
    val immutable: Boolean,
)

/** A registry entry with its full metadata and README, as shown on the detail screen. */
data class RepoModuleDetail(
    val module: RepoModule,
    val license: String?,
    val homepage: String?,
    val support: String?,
    val sourceFullName: String,
    val sourceUrl: String,
    val sourceCommit: String,
    val readmeHtml: String?,
    /**
     * Raw Markdown README, used by repositories that only publish Markdown (Magisk modules
     * repository format). Rendered as Markdown when [readmeHtml] is null.
     */
    val readmeMarkdown: String? = null,
    /** The module has a README but it could not be loaded (show a retry, not "no README"). */
    val readmeFailed: Boolean,
    /** Base URL relative resources in the readme are resolved against (the site root). */
    val readmeBaseUrl: String,
    val releases: List<RepoRelease>,
    val healthStale: Boolean,
    val webUrl: String,
    val fromCache: Boolean,
)

/** Installed state of a registry module; updates are detected by versionCode only. */
enum class RepoInstallState { NONE, INSTALLED, UPDATABLE }

/** Scripts are never installed, so they have no installed / update state. */
fun repoInstallStateOf(module: RepoModule, installedVersions: Map<String, Int>): RepoInstallState =
    if (module.isScript) RepoInstallState.NONE
    else repoInstallState(installedVersions[module.moduleId], module.latestVersionCode)

fun repoInstallState(installedVersionCode: Int?, latestVersionCode: Int): RepoInstallState = when {
    installedVersionCode == null -> RepoInstallState.NONE
    latestVersionCode > installedVersionCode -> RepoInstallState.UPDATABLE
    else -> RepoInstallState.INSTALLED
}

/** Ordering of the module store list. */
enum class RepoSort {
    UPDATED,
    NAME,
    STARS;

    /** Returns [modules] in this order; ties (and unknown star counts) fall back to newest release first. */
    fun sort(modules: List<RepoModule>, locale: Locale = Locale.getDefault()): List<RepoModule> {
        if (modules.isEmpty()) return modules
        return when (this) {
            UPDATED -> modules.sortedByDescending { it.latestReleaseEpoch }
            NAME -> {
                val collator = Collator.getInstance(locale)
                modules.sortedWith(compareBy(collator) { it.displayName })
            }
            // Unknown counts (null) sort after every known count, including 0.
            STARS -> modules.sortedWith(
                compareByDescending<RepoModule> { it.stars ?: -1 }.thenByDescending { it.latestReleaseEpoch }
            )
        }
    }

    companion object {
        /**
         * Restores the saved order. The enum name is stored; older builds stored a
         * "sort by name" boolean, which callers pass as [legacySortByName] while migrating.
         */
        fun fromStored(name: String?, legacySortByName: Boolean? = null): RepoSort {
            name?.let { n -> entries.firstOrNull { it.name == n }?.let { return it } }
            return if (legacySortByName == true) NAME else UPDATED
        }
    }
}
