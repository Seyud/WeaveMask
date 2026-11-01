package io.github.seyud.weave.core.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Support for the Magisk modules repository format, i.e. the address from issue #7
 * (`https://magisk-modules-repo.github.io/submission/modules.json`): the index only carries
 * `prop_url` / `zip_url`, so the metadata has to be read from each `module.prop`.
 */
class MagiskRepoTest {

    private val index = """
        {
          "name": "Magisk Modules Repo (Official)",
          "last_update": 1640056254000,
          "modules": [
            {
              "id": "nano-ndk",
              "last_update": 1640056254000,
              "prop_url": "https://cdn.jsdelivr.net/gh/Magisk-Modules-Repo/nano-ndk@6167a6a/module.prop",
              "zip_url": "https://github.com/Magisk-Modules-Repo/nano-ndk/archive/6167a6a6f1f378a83cda64a73fbcfc22dd4a8cd1.zip",
              "notes_url": "https://cdn.jsdelivr.net/gh/Magisk-Modules-Repo/nano-ndk@6167a6a/README.md"
            },
            {
              "id": "broken",
              "prop_url": "not-a-url",
              "zip_url": "http://insecure.example.com/x.zip"
            }
          ]
        }
    """.trimIndent()

    private val prop = """
        id=nano-ndk
        name=Nano for Android NDK
        version=6.0
        versionCode=6000
        author=osm0sis @ xda-developers
        description=Static ARM nano binary for Android built with the NDK
        support=https://forum.xda-developers.com/showthread.php?t=2239421
    """.trimIndent()

    private val readme = "# nano for Android NDK\n\nA static `nano` binary.\n"

    private fun fetcher(files: Map<String, String>, failWhen: (String) -> Boolean = { false }) =
        RegistryFetcher { url ->
            if (failWhen(url)) throw java.io.IOException("offline: $url")
            files[url] ?: throw java.io.IOException("404: $url")
        }

    private fun memoryCache() = object : RegistryCache {
        val map = HashMap<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String) {
            map[key] = value
        }
    }

    @Test
    fun detectsTheFormatOfAnIndexDocument() {
        assertEquals(ModuleRepoFormat.MAGISK, ModuleRepoFormatDetector.detect(index))
        assertEquals(
            ModuleRepoFormat.REGISTRY,
            ModuleRepoFormatDetector.detect("""{"schemaVersion":1,"buildId":"c62be6732597f791","modules":"api/v1/modules.json"}"""),
        )
        assertEquals(ModuleRepoFormat.MAGISK, ModuleRepoFormatDetector.detect("""{"modules":[]}"""))
    }

    @Test
    fun rejectsTheDeprecatedKernelsuRepository() {
        val e = assertThrows(RegistryFormatException::class.java) {
            ModuleRepoFormatDetector.detect("""[{"moduleId":"x","moduleName":"X"}]""")
        }
        assertTrue(e.message.orEmpty().contains("deprecated"))
        assertThrows(RegistryFormatException::class.java) { ModuleRepoFormatDetector.detect("""{"foo":1}""") }
        assertThrows(RegistryFormatException::class.java) { ModuleRepoFormatDetector.detect("not json") }
    }

    @Test
    fun entryAddressIsAcceptedAsAnEndpoint() {
        val endpoint = RegistryUrls.parseEndpoint("https://magisk-modules-repo.github.io/submission/modules.json")!!
        assertEquals("https://magisk-modules-repo.github.io/submission/", endpoint.baseUrl)
        assertEquals("https://magisk-modules-repo.github.io/submission/modules.json", endpoint.indexUrl)
    }

    @Test
    fun parsesIndexEntriesAndSkipsUnusableOnes() {
        val entries = MagiskRepoClient.parseIndex(index)
        assertEquals(setOf("nano-ndk"), entries.keys)
        val entry = entries.getValue("nano-ndk")
        assertEquals(1640056254000L, entry.lastUpdate)
        assertNotNull(entry.notesUrl)
        // http zip URLs are dropped: a module ZIP is always fetched over https.
        assertNull(entries["broken"])
    }

    @Test
    fun parsesModuleProp() {
        val parsed = MagiskRepoProp.parse("\uFEFF" + prop.replace("\n", "\r\n"))!!
        assertEquals("nano-ndk", parsed.id)
        assertEquals("Nano for Android NDK", parsed.name)
        assertEquals("6.0", parsed.version)
        assertEquals(6000, parsed.versionCode)
        assertEquals("osm0sis @ xda-developers", parsed.author)
        assertTrue(parsed.description.startsWith("Static ARM nano"))
        assertNotNull(parsed.support)
        assertEquals(false, parsed.metamodule)
        // An unusable id makes the whole entry unusable.
        assertNull(MagiskRepoProp.parse("id=bad id\nname=x\n"))
        assertNull(MagiskRepoProp.parse("name=no id\n"))
    }

    @Test
    fun derivesSourceAndCommitFromAnArchiveUrl() {
        val zip = "https://github.com/Magisk-Modules-Repo/nano-ndk/archive/6167a6a6f1f378a83cda64a73fbcfc22dd4a8cd1.zip"
        assertEquals("https://github.com/Magisk-Modules-Repo/nano-ndk", MagiskRepoClient.sourceOf(zip))
        assertEquals("6167a6a6f1f378a83cda64a73fbcfc22dd4a8cd1", MagiskRepoClient.archiveCommitOf(zip))
        assertNull(MagiskRepoClient.archiveCommitOf("https://github.com/a/b/archive/main.zip"))
        assertNull(MagiskRepoClient.sourceOf("https://example.com/x.zip"))
    }

    @Test
    fun loadsTheListFromModuleProps() {
        val propUrl = "https://cdn.jsdelivr.net/gh/Magisk-Modules-Repo/nano-ndk@6167a6a/module.prop"
        val notesUrl = "https://cdn.jsdelivr.net/gh/Magisk-Modules-Repo/nano-ndk@6167a6a/README.md"
        val client = MagiskRepoClient(
            indexUrl = "https://magisk-modules-repo.github.io/submission/modules.json",
            fetcher = fetcher(
                mapOf(
                    "https://magisk-modules-repo.github.io/submission/modules.json" to index,
                    propUrl to prop,
                    notesUrl to readme,
                )
            ),
            cache = memoryCache(),
        )

        val load = client.load()
        assertEquals(1, load.modules.size)
        val module = load.modules.single()
        assertEquals("nano-ndk", module.moduleId)
        assertEquals("Nano for Android NDK", module.displayName)
        assertEquals("6.0", module.latestVersion)
        assertEquals(6000, module.latestVersionCode)
        assertEquals(false, module.isScript)
        assertEquals(false, module.metamodule)

        val detail = client.detail("nano-ndk")
        assertEquals(readme, detail.readmeMarkdown)
        assertNull(detail.readmeHtml)
        assertEquals("https://github.com/Magisk-Modules-Repo/nano-ndk", detail.sourceUrl)
        assertEquals("Magisk-Modules-Repo/nano-ndk", detail.sourceFullName)
        val release = detail.releases.single()
        // No size and no hash: the ZIP is downloaded unchecked and the UI says so.
        assertEquals(0L, release.zipSize)
        assertEquals("", release.sha256)
        assertEquals(false, release.immutable)
        assertTrue(release.zipUrl.endsWith(".zip"))
    }

    @Test
    fun fallsBackToTheCachedListWhenTheNetworkFails() {
        val propUrl = "https://cdn.jsdelivr.net/gh/Magisk-Modules-Repo/nano-ndk@6167a6a/module.prop"
        val cache = memoryCache()
        val files = mapOf(
            "https://magisk-modules-repo.github.io/submission/modules.json" to index,
            propUrl to prop,
        )
        val indexUrl = "https://magisk-modules-repo.github.io/submission/modules.json"
        MagiskRepoClient(indexUrl, fetcher(files), cache).load()

        val offline = MagiskRepoClient(indexUrl, fetcher(files) { true }, cache).load()
        assertTrue(offline.fromCache)
        assertNotNull(offline.error)
        assertEquals("nano-ndk", offline.modules.single().moduleId)
    }

    @Test
    fun loadsTheIndexOnlyOncePerSession() {
        val indexUrl = "https://magisk-modules-repo.github.io/submission/modules.json"
        val propUrl = "https://cdn.jsdelivr.net/gh/Magisk-Modules-Repo/nano-ndk@6167a6a/module.prop"
        val files = mapOf(indexUrl to index, propUrl to prop)
        var requests = 0
        val counting = RegistryFetcher { url ->
            requests++
            files[url] ?: throw java.io.IOException("404: $url")
        }
        val client = MagiskRepoClient(indexUrl, counting, memoryCache())
        client.load()
        val afterFirst = requests
        client.load()
        assertEquals(afterFirst, requests)
        assertEquals(1, client.load().modules.size)
    }
}
