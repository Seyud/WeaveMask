package io.github.seyud.weave.core.registry

import kotlinx.serialization.Serializable

/** Cache shape of a resolved Magisk repository list; keeps the list readable while offline. */
@Serializable
internal data class CachedList(
    val entries: List<CachedEntry>,
    val props: List<CachedProp>,
)

@Serializable
internal data class CachedEntry(
    val id: String,
    val lastUpdate: Long,
    val propUrl: String,
    val zipUrl: String,
    val notesUrl: String? = null,
)

@Serializable
internal data class CachedProp(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int? = null,
    val author: String = "",
    val description: String = "",
    val support: String? = null,
    val metamodule: Boolean = false,
)
