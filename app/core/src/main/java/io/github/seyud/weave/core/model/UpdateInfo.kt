package io.github.seyud.weave.core.model

import android.os.Parcelable
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.ToJson
import kotlinx.parcelize.Parcelize
import java.time.Instant

@JsonClass(generateAdapter = true)
class UpdateJson(
    val magisk: UpdateInfo = UpdateInfo(),
)

@Parcelize
@JsonClass(generateAdapter = true)
data class UpdateInfo(
    val version: String = "",
    val versionCode: Int = -1,
    val link: String = "",
    val note: String = ""
) : Parcelable {
    val hasValidDownload: Boolean
        get() = versionCode > 0 && (
            link.startsWith("http://") || link.startsWith("https://")
        )
}

@JsonClass(generateAdapter = true)
data class ModuleJson(
    val version: String,
    val versionCode: Int,
    val zipUrl: String,
    val changelog: String,
)

@JsonClass(generateAdapter = true)
data class ReleaseAssets(
    val name: String,
    @param:Json(name = "browser_download_url") val url: String,
)

class DateTimeAdapter {
    @ToJson
    fun toJson(date: Instant): String {
        return date.toString()
    }

    @FromJson
    fun fromJson(date: String): Instant {
        return Instant.parse(date)
    }
}

@JsonClass(generateAdapter = true)
data class Release(
    @param:Json(name = "tag_name") val tag: String,
    val name: String,
    val prerelease: Boolean,
    val assets: List<ReleaseAssets>,
    val body: String,
    @param:Json(name = "created_at") val createdTime: Instant,
) {
    val versionCode: Int get() {
        return if (tag[0] == 'v') {
            val parts = tag.drop(1).split('.')
            val major = parts.getOrElse(0) { "0" }.toInt()
            val minor = parts.getOrElse(1) { "0" }.toInt()
            val patch = parts.getOrElse(2) { "0" }.toInt()
            ((major + minor / 10.0 + patch / 100.0) * 1000).toInt()
        } else {
            tag.drop(7).toInt()
        }
    }
}
