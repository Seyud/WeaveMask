package io.github.seyud.weave.core.model.module

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RepoSortTest {

    @Test
    fun storedNameWins() {
        assertEquals(RepoSort.NAME, RepoSort.fromStored("NAME", false))
        assertEquals(RepoSort.UPDATED, RepoSort.fromStored("UPDATED", true))
        assertEquals(RepoSort.STARS, RepoSort.fromStored("STARS", null))
        assertEquals(RepoSort.STARS, RepoSort.fromStored("STARS", true))
    }

    @Test
    fun legacySortByNameBooleanIsMigrated() {
        assertEquals(RepoSort.NAME, RepoSort.fromStored(null, true))
        assertEquals(RepoSort.UPDATED, RepoSort.fromStored(null, false))
        assertEquals(RepoSort.UPDATED, RepoSort.fromStored(null, null))
        // An unknown stored name falls back to the legacy value, not to the default.
        assertEquals(RepoSort.NAME, RepoSort.fromStored("CREATED", true))
        assertEquals(RepoSort.UPDATED, RepoSort.fromStored("CREATED", false))
    }

    private fun module(id: String, stars: Int?, epoch: Long, name: String = id) = RepoModule(
        moduleId = id,
        moduleName = name,
        authors = "",
        authorList = emptyList(),
        summary = "",
        tags = emptyList(),
        metamodule = false,
        iconUrl = null,
        latestVersion = "1.0",
        latestVersionCode = 1,
        latestReleaseTime = "",
        latestReleaseEpoch = epoch,
        stars = stars,
        starsRepository = null,
    )

    private val modules = listOf(
        module("a", stars = 5, epoch = 100, name = "Charlie"),
        module("b", stars = null, epoch = 500, name = "alpha"),
        module("c", stars = 50, epoch = 10, name = "Bravo"),
        module("d", stars = 5, epoch = 300, name = "delta"),
        module("e", stars = 0, epoch = 50, name = "Echo"),
        module("f", stars = null, epoch = 20, name = "foxtrot"),
    )

    @Test
    fun starsSortByCountThenNewestRelease() {
        // Unknown counts come after 0; ties are broken by the newest release.
        assertEquals(
            listOf("c", "d", "a", "e", "b", "f"),
            RepoSort.STARS.sort(modules).map { it.moduleId },
        )
    }

    @Test
    fun otherSortsAreUnchanged() {
        assertEquals(
            listOf("b", "d", "a", "e", "f", "c"),
            RepoSort.UPDATED.sort(modules).map { it.moduleId },
        )
        assertEquals(
            listOf("alpha", "Bravo", "Charlie", "delta", "Echo", "foxtrot"),
            RepoSort.NAME.sort(modules, Locale.ENGLISH).map { it.moduleName },
        )
        assertEquals(emptyList<RepoModule>(), RepoSort.STARS.sort(emptyList()))
    }

    @Test
    fun installStateUsesVersionCodesOnly() {
        assertEquals(RepoInstallState.NONE, repoInstallState(null, 10))
        assertEquals(RepoInstallState.INSTALLED, repoInstallState(10, 10))
        assertEquals(RepoInstallState.INSTALLED, repoInstallState(11, 10))
        assertEquals(RepoInstallState.UPDATABLE, repoInstallState(9, 10))
    }

    @Test
    fun scriptsAreNeverInstalled() {
        val script = module("s", null, 0).copy(isScript = true, latestVersionCode = 7)
        assertEquals(RepoInstallState.NONE, repoInstallStateOf(script, mapOf("s" to 1)))
        assertEquals(
            RepoInstallState.UPDATABLE,
            repoInstallStateOf(module("m", null, 0).copy(latestVersionCode = 7), mapOf("m" to 1)),
        )
    }
}
