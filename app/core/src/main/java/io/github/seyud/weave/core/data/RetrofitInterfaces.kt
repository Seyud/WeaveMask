package io.github.seyud.weave.core.data

import io.github.seyud.weave.core.model.ModuleJson
import io.github.seyud.weave.core.model.Release
import io.github.seyud.weave.core.model.GooglebookRecoveryJson
import io.github.seyud.weave.core.model.UpdateJson
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface RawUrl {

    @GET
    @Streaming
    suspend fun fetchFile(@Url url: String): ResponseBody

    @GET
    suspend fun fetchString(@Url url: String): String

    @GET
    suspend fun fetchModuleJson(@Url url: String): ModuleJson

    @GET
    suspend fun fetchUpdateJson(@Url url: String): UpdateJson
    @GET
    suspend fun fetchGooglebookRecoveryJson(@Url url: String): List<GooglebookRecoveryJson>

    @GET
    @Headers("Cookie: devsite_wall_acks=nexus-image-tos")
    suspend fun fetchPixelFactoryImages(@Url url: String): String
}

interface GithubApiServices {

    @GET("/repos/{owner}/{repo}/releases")
    @Headers("Accept: application/vnd.github+json")
    suspend fun fetchReleases(
        @Path("owner") owner: String = "Seyud",
        @Path("repo") repo: String = "WeaveMask",
        @Query("per_page") per: Int = 10,
        @Query("page") page: Int = 1,
    ): Response<MutableList<Release>>

    @GET("/repos/{owner}/{repo}/releases/latest")
    @Headers("Accept: application/vnd.github+json")
    suspend fun fetchLatestRelease(
        @Path("owner") owner: String = "Seyud",
        @Path("repo") repo: String = "WeaveMask",
    ): Release
}
