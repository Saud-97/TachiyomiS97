package eu.kanade.tachiyomi.extension.api

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

internal class ExtensionApi {
    private val json: Json by injectLazy()
    private val networkService: NetworkHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext {
            val repos = preferences.extensionRepos().get()
            if (repos.isEmpty()) {
                return@withIOContext emptyList()
            }
            val extensions = repos.flatMap { getExtensions(it) }

            if (extensions.isEmpty()) {
                throw Exception()
            }

            extensions
        }
    }

    private suspend fun getExtensions(repoBaseUrl: String): List<Extension.Available> =
        try {
            val response =
                networkService.client
                    .newCall(GET("$repoBaseUrl/index.min.json"))
                    .awaitSuccess()

            with(json) {
                response
                    .parseAs<List<ExtensionJsonObject>>()
                    .toExtensions(repoBaseUrl)
            }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to get extensions from $repoBaseUrl")
            emptyList()
        }

    suspend fun checkForUpdates(
        context: Context,
        prefetchedExtensions: List<Extension.Available>? = null,
    ): List<Extension.Available> =
        withIOContext {
            val extensions = prefetchedExtensions ?: findExtensions()

            val extensionManager: ExtensionManager = Injekt.get()
            val installedExtensions =
                extensionManager.installedExtensionsFlow.value.ifEmpty {
                    ExtensionLoader
                        .loadExtensionAsync(context)
                        .filterIsInstance<LoadResult.Success>()
                        .map { it.extension }
                }

            val extensionsWithUpdate = mutableListOf<Extension.Available>()
            for (installedExt in installedExtensions) {
                val pkgName = installedExt.pkgName
                val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
                val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
                val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
                val hasUpdate = hasUpdatedVer || hasUpdatedLib
                if (hasUpdate) {
                    extensionsWithUpdate.add(availableExt)
                }
            }

            extensionsWithUpdate
        }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<Extension.Available> =
        this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }.map {
                Extension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources ?: emptyList(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }

    fun getApkUrl(extension: ExtensionManager.ExtensionInfo): String = "${extension.repoUrl}/apk/${extension.apkName}"

    private fun ExtensionJsonObject.extractLibVersion(): Double = version.substringBeforeLast('.').toDouble()
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<Extension.AvailableSource>?,
)
