package io.github.seyud.weave.core.download

import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.registry.DownloadVerificationException
import io.github.seyud.weave.core.registry.RegistryUrls
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Opens a verified registry download URL, following redirects by hand.
 *
 * A registry entry is third-party data, so the URL itself *and* every redirect target are checked
 * against the allow-list before the request that would hit them is made. The shared client follows
 * redirects on its own, which would let a harmless looking github.com link end up on any host.
 */
object VerifiedRequest {

    /** GitHub release downloads need at most a single hop to their CDN; extra ones are suspicious. */
    private const val MAX_REDIRECTS = 10

    /**
     * [ServiceLocator.okhttp] without automatic redirects (each hop is validated in [open]) and
     * without the HTTP cache: a multi-megabyte ZIP is streamed into its own verified file, so a
     * cached second copy would only cost space.
     */
    private val client: OkHttpClient by lazy {
        ServiceLocator.okhttp.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cache(null)
            .build()
    }

    /**
     * Executes a GET for [url] and returns the successful response; the caller must close it.
     *
     * @throws DownloadVerificationException when [url] is not a release download or any hop leaves
     * the allow-list, before that hop is requested.
     */
    @Throws(IOException::class)
    fun open(url: String): Response {
        if (!RegistryUrls.isAllowedReleaseDownloadUrl(url)) {
            throw DownloadVerificationException(
                DownloadVerificationException.Reason.URL_NOT_ALLOWED,
                "Not a GitHub release download URL: $url"
            )
        }
        var target = url
        repeat(MAX_REDIRECTS + 1) {
            if (!RegistryUrls.isAllowedDownloadHop(target)) {
                throw DownloadVerificationException(
                    DownloadVerificationException.Reason.URL_NOT_ALLOWED,
                    "Download host not allowed: $target"
                )
            }
            val response = client.newCall(Request.Builder().url(target).build()).execute()
            val code = response.code
            if (code !in 300 until 400) {
                if (!response.isSuccessful) {
                    response.close()
                    throw IOException("HTTP $code for $target")
                }
                return response
            }
            val requestUrl = response.request.url
            val location = response.header("Location")
            response.close()
            if (location == null) throw IOException("Redirect $code without Location: $target")
            // Resolved against the request URL so a relative Location still works.
            target = requestUrl.resolve(location)?.toString()
                ?: throw IOException("Unresolvable redirect target: $location")
        }
        throw IOException("Too many redirects: $url")
    }
}
