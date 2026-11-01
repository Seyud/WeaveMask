package io.github.seyud.weave.core.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The module store address field accepts a site root **and** the full URL of a repository index
 * document (issue #7); both have to end up as the same resolvable endpoint.
 */
class RegistryEndpointTest {

    @Test
    fun siteRootIsNormalized() {
        val plain = RegistryUrls.parseEndpoint("https://modules.kernelsu.org")!!
        assertEquals("https://modules.kernelsu.org/", plain.baseUrl)
        assertEquals("https://modules.kernelsu.org/api/v1/index.json", plain.indexUrl)

        val trailing = RegistryUrls.parseEndpoint("  https://modules.kernelsu.org/  ")!!
        assertEquals(plain, trailing)
    }

    @Test
    fun mirrorBelowAPathPrefixKeepsThePrefix() {
        val endpoint = RegistryUrls.parseEndpoint("https://mirror.example.com/ksu")!!
        assertEquals("https://mirror.example.com/ksu/", endpoint.baseUrl)
        assertEquals("https://mirror.example.com/ksu/api/v1/index.json", endpoint.indexUrl)
        assertEquals(
            "https://mirror.example.com/ksu/api/v1/modules.json",
            RegistryUrls.resolve(endpoint.baseUrl, "api/v1/modules.json"),
        )
    }

    @Test
    fun fullIndexUrlIsAccepted() {
        // The address the user copies out of a browser must work as well.
        val endpoint = RegistryUrls.parseEndpoint("https://modules.kernelsu.org/api/v1/index.json")!!
        assertEquals("https://modules.kernelsu.org/", endpoint.baseUrl)
        assertEquals("https://modules.kernelsu.org/api/v1/index.json", endpoint.indexUrl)

        val mirror = RegistryUrls.parseEndpoint("https://mirror.example.com/ksu/api/v1/index.json")!!
        assertEquals("https://mirror.example.com/ksu/", mirror.baseUrl)
        assertEquals("https://mirror.example.com/ksu/api/v1/index.json", mirror.indexUrl)
    }

    @Test
    fun anyJsonDocumentUrlUsesItsDirectoryAsRoot() {
        val endpoint = RegistryUrls.parseEndpoint("https://example.com/repo/submission/modules.json")!!
        assertEquals("https://example.com/repo/submission/", endpoint.baseUrl)
        assertEquals("https://example.com/repo/submission/modules.json", endpoint.indexUrl)
        // Documents of an unknown repository are resolved next to the index, never above it.
        assertEquals(
            "https://example.com/repo/submission/api/v1/modules.json",
            RegistryUrls.resolve(endpoint.baseUrl, "api/v1/modules.json"),
        )
    }

    @Test
    fun queryAndFragmentAreDropped() {
        val endpoint = RegistryUrls.parseEndpoint("https://example.com/ksu/?utm=1#top")!!
        assertEquals("https://example.com/ksu/", endpoint.baseUrl)
        assertEquals("https://example.com/ksu/api/v1/index.json", endpoint.indexUrl)
    }

    @Test
    fun unsafeOrUnsupportedAddressesAreRejected() {
        assertNull(RegistryUrls.parseEndpoint(""))
        assertNull(RegistryUrls.parseEndpoint("   "))
        assertNull(RegistryUrls.parseEndpoint("modules.kernelsu.org"))
        assertNull(RegistryUrls.parseEndpoint("ftp://example.com/ksu"))
        assertNull(RegistryUrls.parseEndpoint("http://example.com/ksu"))
        assertNull(RegistryUrls.parseEndpoint("https://user:pass@example.com/ksu"))
        assertNull(RegistryUrls.parseEndpoint("https:///ksu"))
        assertFalse(RegistryUrls.isValidEndpointInput("javascript:alert(1)"))
        assertTrue(RegistryUrls.isValidEndpointInput(RegistryUrls.DEFAULT_BASE_URL))
        assertTrue(RegistryUrls.isValidEndpointInput("https://example.com/a/api/v1/index.json"))
    }

    @Test
    fun defaultEndpointMatchesThePublishedSite() {
        val endpoint = RegistryUrls.parseEndpoint(RegistryUrls.DEFAULT_BASE_URL)!!
        assertEquals(RegistryUrls.DEFAULT_BASE_URL, endpoint.baseUrl)
        assertEquals(
            "https://modules.kernelsu.org/api/v1/index.json",
            endpoint.indexUrl,
        )
    }
}
