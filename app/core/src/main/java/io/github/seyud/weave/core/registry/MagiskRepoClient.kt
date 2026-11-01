package io.github.seyud.weave.core.registry

import io.github.seyud.weave.core.model.module.Author
import io.github.seyud.weave.core.model.module.RepoModule
import io.github.seyud.weave.core.model.module.RepoModuleDetail
import io.github.seyud.weave.core.model.module.RepoRelease
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI

/** One entry of a Magisk modules repository index (`modules.json`). */
data class MagiskRepoEntry(
    val id: String,
    val lastUpdate: Long,
    val propUrl: String,
    val zipUrl: String,
    /** Raw README of the module, or null. */
    val notesUrl: String?,
)

/** The fields of `module.prop` this client uses. */
data class MagiskRepoProp(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int?,
    val author: String,
    val description: String,
    val support: String?,
    val metamodule: Boolean,
) {
    companion object {
        /**
         * Parses `module.prop` with Magisk's semantics: `key=value` lines, `#` comment lines,
         * blanks ignored, a UTF-8 BOM tolerated. Unknown keys are dropped.
         */
        fun parse(text: String): MagiskRepoProp? {
            val values = LinkedHashMap<String, String>()
            text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEach
                values[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
            }
            val id = values["id"].orEmpty()
            if (!RegistryParser.isValidModuleId(id)) return null
            return MagiskRepoProp(
                id = id,
                name = values["name"].orEmpty().ifBlank { id },
                version = values["version"].orEmpty(),
                versionCode = values["versionCode"]?.toIntOrNull(),
                author = values["author"].orEmpty(),
                description = values["description"].orEmpty(),
                support = values["support"]?.takeIf { RegistryUrls.isWebUrl(it) },
                metamodule = values["metamodule"].equals("true", ignoreCase = true),
            )
        }
    }
}

/**
 * Client of the Magisk modules repository format (`magisk-modules-repo.github.io/submission/
 * modules.json` and its forks): the index only carries `prop_url` / `zip_url`, so the module
 * metadata is read from each `module.prop`.
 *
 * Nothing here is integrity checked: the format has no size or SHA-256, and the ZIPs are GitHub
 * archive downloads rather than immutable releases, so ZIPs downloaded from such a repository go
 * through the unchecked download path and the UI says so.
 */
class MagiskRepoClient(
    private val indexUrl: String,
    private val fetcher: RegistryFetcher,
    private val cache: RegistryCache,
    /** Parallel `module.prop` fetches; the index of the official repository has ~100 modules. */
    private val propFetchThreads: Int = 8,
) {
    data class Load(
        val modules: List<RepoModule>,
        val entries: Map<String, MagiskRepoEntry>,
        val props: Map<String, MagiskRepoProp>,
        val fromCache: Boolean,
        val error: Throwable?,
    )

    @Volatile
    private var current: Load? = null

    private val cacheKey = "magisk-repo-" + indexUrl

    fun load(): Load {
        current?.let { if (!it.fromCache) return it }
        return try {
            val load = fetch().also { current = it }
            load
        } catch (e: Exception) {
            val cached = current ?: readCache()
            if (cached == null) throw e
            cached.copy(fromCache = true, error = e).also { current = it }
        }
    }

    /** Detail of [id] from the loaded index; [readmeMarkdown] is fetched on demand. */
    fun detail(id: String): RepoModuleDetail {
        val load = load()
        val entry = load.entries[id] ?: throw ModuleNotInIndexException(id)
        val prop = load.props[id] ?: throw ModuleNotInIndexException(id)
        val module = load.modules.firstOrNull { it.moduleId == id } ?: throw ModuleNotInIndexException(id)
        val readme = entry.notesUrl?.let { url ->
            runCatching { fetcher.fetch(url) }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        val source = sourceOf(entry.zipUrl)
        return RepoModuleDetail(
            module = module,
            license = null,
            homepage = source,
            support = prop.support,
            sourceFullName = source?.removePrefix("https://github.com/").orEmpty(),
            sourceUrl = source.orEmpty(),
            sourceCommit = archiveCommitOf(entry.zipUrl).orEmpty(),
            readmeHtml = null,
            readmeMarkdown = readme,
            readmeFailed = entry.notesUrl != null && readme == null,
            readmeBaseUrl = source.orEmpty(),
            releases = listOf(releaseOf(entry, prop)),
            healthStale = false,
            webUrl = "",
            fromCache = load.fromCache,
        )
    }

    /**
     * The single release this format knows: the ZIP pinned to the commit in the index. Its size
     * and SHA-256 are unknown, which the UI shows rather than hides.
     */
    private fun releaseOf(entry: MagiskRepoEntry, prop: MagiskRepoProp): RepoRelease = RepoRelease(
        tag = prop.version.ifBlank { entry.id },
        name = prop.version.ifBlank { entry.id },
        version = prop.version,
        versionCode = prop.versionCode ?: 0,
        publishedAt = formatTimestamp(entry.lastUpdate),
        changelogHtml = null,
        zipUrl = entry.zipUrl,
        zipName = entry.zipUrl.substringAfterLast('/').ifBlank { "${entry.id}.zip" },
        zipSize = 0L,
        sha256 = "",
        tagCommit = archiveCommitOf(entry.zipUrl).orEmpty(),
        releaseUrl = entry.zipUrl,
        attestationUrl = null,
        immutable = false,
    )

    private fun fetch(): Load {
        val indexText = fetcher.fetch(indexUrl)
        val entries = parseIndex(indexText)
        // Keys of the previous copy, so a refresh only fetches metadata that actually changed.
        val previous = current ?: runCatching { readCache() }.getOrNull()
        val cachedEntries = previous?.entries.orEmpty()
        val cachedProps = previous?.props.orEmpty()

        val props = java.util.concurrent.ConcurrentHashMap<String, MagiskRepoProp>()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(propFetchThreads)
        try {
            val futures = entries.values.map { entry ->
                pool.submit {
                    val reused = cachedEntries[entry.id]
                        ?.takeIf { it.propUrl == entry.propUrl }
                        ?.let { cachedProps[entry.id] }
                    val prop = reused ?: runCatching { fetcher.fetch(entry.propUrl) }
                        .getOrNull()
                        ?.let(MagiskRepoProp::parse)
                    if (prop != null) props[entry.id] = prop
                }
            }
            futures.forEach { runCatching { it.get() } }
        } finally {
            pool.shutdownNow()
        }

        val modules = entries.values
            .mapNotNull { entry -> props[entry.id]?.let { moduleOf(entry, it) } }
            .sortedByDescending { it.latestReleaseEpoch }
        val load = Load(
            modules = modules,
            entries = entries,
            props = props.toMap(),
            fromCache = false,
            error = null,
        )
        runCatching { writeCache(load) }
        return load
    }

    private fun moduleOf(entry: MagiskRepoEntry, prop: MagiskRepoProp): RepoModule {
        val authors = prop.author.takeIf { it.isNotBlank() }?.let { listOf(Author(it, "")) }.orEmpty()
        return RepoModule(
            moduleId = entry.id,
            moduleName = prop.name,
            authors = authors.joinToString(", ") { it.name },
            authorList = authors,
            summary = prop.description,
            tags = emptyList(),
            metamodule = prop.metamodule,
            // One-shot scripts are a registry concept; a ZIP from this format is installed as a
            // module, which is also what older managers do with `oneshot` ZIPs.
            isScript = false,
            iconUrl = null,
            latestVersion = prop.version,
            latestVersionCode = prop.versionCode ?: 0,
            latestReleaseTime = formatTimestamp(entry.lastUpdate),
            latestReleaseEpoch = entry.lastUpdate,
            stars = null,
            starsRepository = null,
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = false; explicitNulls = false }

        /** `https://github.com/<owner>/<repo>` of an archive URL, or null. */
        fun sourceOf(zipUrl: String): String? {
            val uri = runCatching { URI(zipUrl) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (!uri.host.equals("github.com", ignoreCase = true)) return null
            val parts = (uri.rawPath ?: return null).trim('/').split('/')
            if (parts.size < 2) return null
            val owner = parts[0]
            val repo = parts[1]
            if (owner.isBlank() || repo.isBlank()) return null
            return "https://github.com/$owner/$repo"
        }

        /** `6180ac…` of `…/archive/6180ac….zip`, or null. */
        fun archiveCommitOf(zipUrl: String): String? {
            val name = zipUrl.substringAfterLast('/').removeSuffix(".zip")
            return name.takeIf { it.matches(Regex("^[0-9a-f]{7,40}$")) }
        }

        fun parseIndex(text: String): Map<String, MagiskRepoEntry> {
            val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
                ?: throw RegistryFormatException("Malformed repository index")
            val array = root["modules"] as? JsonArray
                ?: throw RegistryFormatException("Missing modules array")
            val entries = LinkedHashMap<String, MagiskRepoEntry>()
            for (element in array) {
                val obj = element as? JsonObject ?: continue
                val id = (obj["id"] as? JsonPrimitive)?.content?.trim().orEmpty()
                val propUrl = (obj["prop_url"] as? JsonPrimitive)?.content?.trim().orEmpty()
                val zipUrl = (obj["zip_url"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (!RegistryParser.isValidModuleId(id)) continue
                if (!RegistryUrls.isWebUrl(propUrl) || !isHttps(zipUrl)) continue
                entries[id] = MagiskRepoEntry(
                    id = id,
                    lastUpdate = (obj["last_update"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: 0L,
                    propUrl = propUrl,
                    zipUrl = zipUrl,
                    notesUrl = (obj["notes_url"] as? JsonPrimitive)?.content?.trim()
                        ?.takeIf { RegistryUrls.isWebUrl(it) },
                )
            }
            if (entries.isEmpty()) throw RegistryFormatException("Repository index lists no usable module")
            return entries
        }

        private fun isHttps(url: String): Boolean =
            runCatching { URI(url) }.getOrNull()?.scheme?.equals("https", ignoreCase = true) == true

        /** `1610000000000` -> the date the index recorded, or an empty string. */
        fun formatTimestamp(epochMillis: Long): String {
            if (epochMillis <= 0) return ""
            return runCatching {
                java.time.Instant.ofEpochMilli(epochMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
            }.getOrDefault("")
        }
    }

    // -- on-disk copy of the resolved list ------------------------------------------------------

    private fun writeCache(load: Load) {
        val model = CachedList(
            entries = load.entries.values.map { CachedEntry(it.id, it.lastUpdate, it.propUrl, it.zipUrl, it.notesUrl) },
            props = load.props.values.map {
                CachedProp(it.id, it.name, it.version, it.versionCode, it.author, it.description, it.support, it.metamodule)
            },
        )
        cache.write(cacheKey, json.encodeToString(CachedList.serializer(), model))
    }

    private fun readCache(): Load? = runCatching {
        val text = cache.read(cacheKey) ?: return null
        val model = json.decodeFromString(CachedList.serializer(), text)
        val entries = model.entries.associate { it.id to MagiskRepoEntry(it.id, it.lastUpdate, it.propUrl, it.zipUrl, it.notesUrl) }
        val props = model.props.associate {
            it.id to MagiskRepoProp(it.id, it.name, it.version, it.versionCode, it.author, it.description, it.support, it.metamodule)
        }
        if (entries.isEmpty() || props.isEmpty()) return null
        Load(
            modules = entries.values.mapNotNull { entry -> props[entry.id]?.let { moduleOf(entry, it) } }
                .sortedByDescending { it.latestReleaseEpoch },
            entries = entries,
            props = props,
            fromCache = true,
            error = null,
        )
    }.getOrNull()
}
