package io.github.seyud.weave.core.repository

import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Config
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.model.module.Author
import io.github.seyud.weave.core.model.module.RepoModule
import io.github.seyud.weave.core.model.module.RepoModuleDetail
import io.github.seyud.weave.core.model.module.RepoModuleList
import io.github.seyud.weave.core.model.module.RepoRelease
import io.github.seyud.weave.core.registry.ApiAuthor
import io.github.seyud.weave.core.registry.ApiModuleDetail
import io.github.seyud.weave.core.registry.ApiModuleSummary
import io.github.seyud.weave.core.registry.ApiVersionRef
import io.github.seyud.weave.core.registry.FileRegistryCache
import io.github.seyud.weave.core.registry.MagiskRepoClient
import io.github.seyud.weave.core.registry.ModuleRepoFormat
import io.github.seyud.weave.core.registry.ModuleRepoFormatDetector
import io.github.seyud.weave.core.registry.RegistryClient
import io.github.seyud.weave.core.registry.RegistryFetcher
import io.github.seyud.weave.core.registry.RegistryKind
import io.github.seyud.weave.core.registry.RegistryLocale
import io.github.seyud.weave.core.registry.RegistryUpdatePolicy
import io.github.seyud.weave.core.registry.RegistryUrls
import io.github.seyud.weave.core.registry.UpdateVerification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Reads the KernelSU Modules registry (`https://modules.kernelsu.org`, `/api/v1`).
 *
 * The repository address is user configurable (`Config.moduleRepoUrl`): a site root, the URL of
 * a KernelSU Modules index document, or the URL of a Magisk modules repository index, see
 * [RegistryUrls.parseEndpoint] and [ModuleRepoFormatDetector].
 */
interface ModuleRepoRepository {
    /** Index + module list; falls back to the last complete cached copy on failure. */
    suspend fun fetchModules(baseUrl: String = Config.moduleRepoUrl): Result<RepoModuleList>

    /**
     * Detail (and readme) of a module in the current index. Fails with
     * [io.github.seyud.weave.core.registry.ModuleNotInIndexException] if it is not listed.
     */
    suspend fun fetchDetail(
        moduleId: String,
        baseUrl: String = Config.moduleRepoUrl,
        forceRefresh: Boolean = false,
    ): Result<RepoModuleDetail>

    /** The last loaded detail of [moduleId], for showing content while it is re-read. */
    fun getCachedDetail(moduleId: String, baseUrl: String = Config.moduleRepoUrl): RepoModuleDetail?

    /** Sanitized changelog HTML of a release (by tag, or the stable release), or null. */
    suspend fun fetchChangelogHtml(moduleId: String, tag: String?): String?

    /**
     * How an `updateJson` update of an installed module must be verified: against the matching
     * registry release, rejected, or legacy (not a registry update).
     */
    suspend fun resolveUpdate(
        moduleId: String,
        updateJsonUrl: String,
        zipUrl: String,
        versionCode: Int,
    ): UpdateVerification

    /** Raw Markdown/text of a registry-hosted document (e.g. `changelog.md`), or null. */
    suspend fun fetchRegistryText(url: String): String?

    /** Whether [url] is served by the registry site (its content must use the restricted renderer). */
    fun isRegistryUrl(url: String): Boolean
}

class ModuleRepoRepositoryImpl : ModuleRepoRepository {

    companion object {
        private const val TAG = "ModuleRepoRepository"

        /** Upper bound for a single JSON / HTML document. */
        private const val MAX_DOCUMENT_BYTES = 32L * 1024 * 1024

        /** One client per repository, so the list and detail screens share the same catalog. */
        private val clients = HashMap<String, RegistryClient>()

        /** One client per Magisk-format repository index. */
        private val magiskClients = HashMap<String, MagiskRepoClient>()

        /** Detected format per index URL; detection costs one request, so it is remembered. */
        private val formats = HashMap<String, ModuleRepoFormat>()

        /** Recently loaded details, so a reopening detail screen has content immediately. */
        private val detailCache = HashMap<String, RepoModuleDetail>()
        private const val DETAIL_CACHE_SIZE = 32

        private fun requestedLocale(): String = Locale.getDefault().toLanguageTag()

        internal fun reset() = synchronized(clients) {
            clients.clear()
            magiskClients.clear()
            formats.clear()
            detailCache.clear()
        }
    }

    private object OkHttpRegistryFetcher : RegistryFetcher {
        override fun fetch(url: String): String {
            val request = Request.Builder()
                .url(url)
                // Revalidate so index, list and detail are never served from different cache ages.
                .cacheControl(CacheControl.Builder().noCache().build())
                .build()
            ServiceLocator.okhttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                val body = response.body
                if (body.contentLength() > MAX_DOCUMENT_BYTES) throw IOException("Document too large: $url")
                val source = body.source()
                if (source.request(MAX_DOCUMENT_BYTES + 1)) throw IOException("Document too large: $url")
                return source.readUtf8()
            }
        }
    }

    private fun endpointOf(baseUrl: String): RegistryUrls.Endpoint =
        RegistryUrls.parseEndpoint(baseUrl) ?: error("Invalid module repository URL: $baseUrl")

    private fun clientFor(endpoint: RegistryUrls.Endpoint): RegistryClient = synchronized(clients) {
        clients.getOrPut(endpoint.baseUrl) {
            RegistryClient(
                endpoint = endpoint,
                fetcher = OkHttpRegistryFetcher,
                cache = FileRegistryCache(File(AppContext.filesDir, "module_registry")),
            )
        }
    }

    private fun magiskClientFor(endpoint: RegistryUrls.Endpoint): MagiskRepoClient = synchronized(clients) {
        magiskClients.getOrPut(endpoint.indexUrl) {
            MagiskRepoClient(
                indexUrl = endpoint.indexUrl,
                fetcher = OkHttpRegistryFetcher,
                cache = FileRegistryCache(File(AppContext.filesDir, "module_repo")),
            )
        }
    }

    /**
     * Which protocol [endpoint] speaks, detected once per address. A site root is a registry by
     * definition (its index is `api/v1/index.json`); any other address is the URL of an index
     * document, which may just as well be a Magisk modules repository (`…/modules.json` as in
     * the reported issue).
     */
    private fun formatFor(endpoint: RegistryUrls.Endpoint): ModuleRepoFormat = synchronized(clients) {
        formats.getOrPut(endpoint.indexUrl) {
            if (endpoint.indexUrl == endpoint.baseUrl + RegistryUrls.INDEX_PATH) {
                ModuleRepoFormat.REGISTRY
            } else {
                // Only document URLs are ambiguous; a failure is reported and retried on the
                // next refresh, which is exactly what the user expects after fixing the address.
                ModuleRepoFormatDetector.detect(OkHttpRegistryFetcher.fetch(endpoint.indexUrl))
                    .also { Timber.tag(TAG).i("repository format %s for %s", it, endpoint.indexUrl) }
            }
        }
    }

    override suspend fun fetchModules(baseUrl: String): Result<RepoModuleList> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = endpointOf(baseUrl)
            if (formatFor(endpoint) == ModuleRepoFormat.MAGISK) {
                val load = magiskClientFor(endpoint).load()
                load.error?.let { Timber.tag(TAG).w(it, "magisk repository fetch failed, using last good copy") }
                return@runCatching RepoModuleList(
                    modules = load.modules,
                    buildId = endpoint.indexUrl,
                    stale = load.error != null || load.fromCache,
                    error = load.error,
                )
            }
            val client = clientFor(endpoint)
            val load = client.loadCatalog()
            load.error?.let { Timber.tag(TAG).w(it, "registry fetch failed, using last good copy") }
            RepoModuleList(
                modules = load.catalog.modules.map { it.toRepoModule(client) },
                buildId = load.catalog.buildId,
                stale = load.error != null || load.catalog.fromCache,
                error = load.error,
            )
        }.onFailure { Timber.tag(TAG).e(it, "cannot load module repository %s", baseUrl) }
    }

    override suspend fun fetchDetail(
        moduleId: String,
        baseUrl: String,
        forceRefresh: Boolean,
    ): Result<RepoModuleDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = endpointOf(baseUrl)
            if (forceRefresh) cacheDetail(null, baseUrl, moduleId)
            if (formatFor(endpoint) == ModuleRepoFormat.MAGISK) {
                val detail = magiskClientFor(endpoint).detail(moduleId)
                cacheDetail(detail, baseUrl, moduleId)
                return@runCatching detail
            }
            val client = clientFor(endpoint)
            val load = client.loadDetail(moduleId)
            load.error?.let { Timber.tag(TAG).w(it, "detail fetch failed, using last good copy") }
            val readmeResult = runCatching { client.loadReadme(load.detail) }
                .onFailure { Timber.tag(TAG).w(it, "readme fetch failed") }
            val detail = load.detail.toRepoModuleDetail(
                client = client,
                siteLocale = RegistryLocale.siteLocale(requestedLocale()),
                readmeHtml = readmeResult.getOrNull()?.html,
                readmeFailed = readmeResult.isFailure,
                fromCache = load.fromCache,
            )
            cacheDetail(detail, baseUrl, moduleId)
            detail
        }
    }

    override fun getCachedDetail(moduleId: String, baseUrl: String): RepoModuleDetail? =
        synchronized(clients) { detailCache[cacheKey(baseUrl, moduleId)] }

    private fun cacheDetail(detail: RepoModuleDetail?, baseUrl: String, moduleId: String) {
        synchronized(clients) {
            val key = cacheKey(baseUrl, moduleId)
            if (detail == null) {
                detailCache.remove(key)
            } else {
                detailCache[key] = detail
                while (detailCache.size > DETAIL_CACHE_SIZE) {
                    detailCache.keys.firstOrNull()?.let(detailCache::remove)
                }
            }
        }
    }

    private fun cacheKey(baseUrl: String, moduleId: String): String =
        "${RegistryUrls.parseEndpoint(baseUrl)?.baseUrl.orEmpty()}|$moduleId"

    override suspend fun fetchChangelogHtml(moduleId: String, tag: String?): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = clientFor(endpointOf(Config.moduleRepoUrl))
                val detail = client.loadDetail(moduleId).detail
                val releases = detail.releases.filter { !it.prerelease }
                val release = if (tag != null) {
                    releases.firstOrNull { it.tag == tag || it.version == tag }
                } else {
                    releases.firstOrNull { it.versionCode == detail.stable.versionCode } ?: releases.firstOrNull()
                }
                release?.changelogHtml?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    override suspend fun resolveUpdate(
        moduleId: String,
        updateJsonUrl: String,
        zipUrl: String,
        versionCode: Int,
    ): UpdateVerification = withContext(Dispatchers.IO) {
        val endpoint = endpointOf(Config.moduleRepoUrl)
        val format = runCatching { formatFor(endpoint) }.getOrElse { return@withContext UpdateVerification.Legacy }
        if (format == ModuleRepoFormat.MAGISK) return@withContext UpdateVerification.Legacy
        val client = clientFor(endpoint)
        if (RegistryUpdatePolicy.isUnrelatedToRegistry(client.baseUrl, updateJsonUrl, zipUrl)) {
            return@withContext UpdateVerification.Legacy
        }
        // Use the index in memory as is; only load one (network, then cache) when there is none.
        val catalog = runCatching { client.catalog() }
            .onFailure { Timber.tag(TAG).w(it, "registry index unavailable for update check") }
            .getOrNull()
        val inIndex = catalog?.let { it.find(moduleId) != null }
        val detail = if (inIndex == true) {
            runCatching { client.loadDetail(moduleId).detail }
                .onFailure { Timber.tag(TAG).w(it, "registry detail unavailable for update check") }
                .getOrNull()
        } else {
            null
        }
        RegistryUpdatePolicy.decide(client.baseUrl, updateJsonUrl, zipUrl, versionCode, inIndex, detail)
    }

    override fun isRegistryUrl(url: String): Boolean {
        val endpoint = RegistryUrls.parseEndpoint(Config.moduleRepoUrl) ?: return false
        if (runCatching { formatFor(endpoint) }.getOrNull() != ModuleRepoFormat.REGISTRY) return false
        return RegistryUrls.isSameOrigin(url, endpoint.baseUrl)
    }

    override suspend fun fetchRegistryText(url: String): String? = withContext(Dispatchers.IO) {
        if (!isRegistryUrl(url) || !RegistryUrls.isHttpsUrl(url)) return@withContext null
        runCatching { OkHttpRegistryFetcher.fetch(url) }
            .onFailure { Timber.tag(TAG).w(it, "registry document fetch failed: $url") }
            .getOrNull()
    }

    private fun formatDate(iso: String): Pair<String, Long> = runCatching {
        val time = OffsetDateTime.parse(iso)
        val text = time.atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        text to time.toInstant().toEpochMilli()
    }.getOrDefault(iso to 0L)

    private fun ApiModuleSummary.toRepoModule(client: RegistryClient): RepoModule =
        buildRepoModule(client, id, name, summary, authors, tags, metamodule, kind, icon, stable, stars, starsRepository)

    private fun buildRepoModule(
        client: RegistryClient,
        id: String,
        name: String,
        summary: String,
        authors: List<ApiAuthor>,
        tags: List<String>,
        metamodule: Boolean,
        kind: String,
        icon: String?,
        stable: ApiVersionRef,
        stars: Int?,
        starsRepository: String?,
    ): RepoModule {
        val authorList = authors
            .filter { it.name.isNotBlank() }
            .map { Author(name = it.name.trim(), link = it.url.orEmpty()) }
        val (date, epoch) = formatDate(stable.publishedAt)
        return RepoModule(
            moduleId = id,
            moduleName = name.trim().ifEmpty { id },
            authors = authorList.joinToString(", ") { it.name },
            authorList = authorList,
            summary = summary.trim(),
            tags = tags,
            metamodule = metamodule,
            isScript = kind == RegistryKind.SCRIPT,
            iconUrl = icon?.let { client.resolve(it) },
            latestVersion = stable.version,
            latestVersionCode = stable.versionCode,
            latestReleaseTime = date,
            latestReleaseEpoch = epoch,
            stars = stars,
            starsRepository = starsRepository,
        )
    }

    private fun ApiModuleDetail.toRepoModuleDetail(
        client: RegistryClient,
        siteLocale: String,
        readmeHtml: String?,
        readmeFailed: Boolean,
        fromCache: Boolean,
    ): RepoModuleDetail {
        val module = buildRepoModule(
            client, id, name, summary, authors, tags, metamodule, kind, icon, stable, stars, starsRepository
        )
        return RepoModuleDetail(
            module = module,
            license = license,
            homepage = homepage,
            support = support,
            sourceFullName = source.fullName,
            sourceUrl = source.url,
            sourceCommit = source.commit,
            readmeHtml = readmeHtml,
            readmeFailed = readmeFailed,
            readmeBaseUrl = client.baseUrl,
            // Prereleases are recorded by the registry but never offered for installation.
            releases = releases.filter { !it.prerelease }.map { r ->
                RepoRelease(
                    tag = r.tag,
                    name = r.name?.takeIf { it.isNotBlank() } ?: r.tag,
                    version = r.version,
                    versionCode = r.versionCode,
                    publishedAt = formatDate(r.publishedAt).first,
                    changelogHtml = r.changelogHtml?.takeIf { it.isNotBlank() },
                    zipUrl = r.zip.url,
                    zipName = r.zip.name.substringAfterLast('/').ifBlank { "$id-${r.versionCode}.zip" },
                    zipSize = r.zip.size,
                    sha256 = r.zip.sha256,
                    tagCommit = r.tagCommit,
                    releaseUrl = r.releaseUrl,
                    attestationUrl = r.attestationUrl,
                    immutable = r.immutable,
                )
            },
            healthStale = health?.status == "stale",
            // The public site only has UI locales, not module content locales.
            webUrl = "${client.baseUrl}$siteLocale/modules/$id/",
            fromCache = fromCache,
        )
    }
}
