package io.github.seyud.weave.core.model.module

import android.os.Parcelable
import io.github.seyud.weave.core.model.ModuleJson
import kotlinx.parcelize.Parcelize

@Parcelize
data class OnlineModule(
    override var id: String,
    override var name: String,
    override var version: String,
    override var versionCode: Int,
    val zipUrl: String,
    val changelog: String,
    /** Size in bytes of the release ZIP recorded by the registry; 0 when it is not known. */
    val zipSize: Long = 0L,
    /** SHA-256 (hex) of the release ZIP recorded by the registry; null when it is not known. */
    val zipSha256: String? = null,
) : Module(), Parcelable {
    constructor(local: LocalModule, json: ModuleJson) :
        this(local.id, local.name, json.version, json.versionCode, json.zipUrl, json.changelog)

    val downloadFilename get() = "$name-$version($versionCode).zip".legalFilename()

    private fun String.legalFilename() = replace(" ", "_")
        .replace("'", "").replace("\"", "")
        .replace("$", "").replace("`", "")
        .replace("*", "").replace("/", "_")
        .replace("#", "").replace("@", "")
        .replace("\\", "_")
}
