package io.github.seyud.weave.core.registry

import io.github.seyud.weave.core.di.ServiceLocator
import okhttp3.Request
import timber.log.Timber

/**
 * Content-addressed assets of the registry site (module icons).
 *
 * Kept in `:core` because only this module owns the HTTP client, and because the same
 * restricted-resource rule as for README sub-resources has to apply: registry assets are
 * fetched from the registry origin only, without following redirects anywhere else.
 */
object RegistryAssets {

    private const val TAG = "RegistryAssets"

    private val client by lazy {
        ServiceLocator.okhttp.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * Returns the bytes at [url] when they come from [origin] and fit in [maxBytes], otherwise
     * null. Never throws: a missing icon only hides the image.
     */
    fun fetch(url: String, origin: String, maxBytes: Long): ByteArray? {
        if (!RegistryUrls.isAllowedRestrictedResource(url, origin)) return null
        return runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val source = response.body.source()
                if (source.request(maxBytes + 1)) return null
                return source.readByteArray()
            }
        }.onFailure { Timber.tag(TAG).w(it, "registry asset fetch failed: %s", url) }
            .getOrNull()
    }
}
