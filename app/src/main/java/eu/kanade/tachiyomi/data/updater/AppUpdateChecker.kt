package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.concurrent.TimeUnit

class AppUpdateChecker {
    private val json: Json by injectLazy()
    private val networkService: NetworkHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    suspend fun checkForUpdate(
        context: Context,
        isUserPrompt: Boolean = false,
        doExtrasAfterNewUpdate: Boolean = true,
    ): AppUpdateResult {
        // Limit checks to once a day at most
        if (!isUserPrompt && Date().time < preferences.lastAppCheck().get() + TimeUnit.DAYS.toMillis(1)) {
            return AppUpdateResult.NoNewUpdate
        }

        return withIOContext {
            val result =
                with(json) {
                    if (preferences.checkForBetas().get()) {
                        networkService.client
                            .newCall(GET("https://api.github.com/repos/$GITHUB_REPO/releases"))
                            .await()
                            .parseAs<List<GithubRelease>>()
                            .let { githubReleases ->
                                val releases =
                                    githubReleases.take(10).filter { isNewVersion(it.version) }
                                // Check if any of the latest versions are newer than the current version
                                val release =
                                    releases
                                        .maxWithOrNull { r1, r2 ->
                                            when {
                                                r1.version == r2.version -> 0
                                                isNewVersion(r2.version, r1.version) -> -1
                                                else -> 1
                                            }
                                        }
                                preferences.lastAppCheck().set(Date().time)

                                if (release != null) {
                                    AppUpdateResult.NewUpdate(release)
                                } else {
                                    AppUpdateResult.NoNewUpdate
                                }
                            }
                    } else {
                        networkService.client
                            .newCall(GET("https://api.github.com/repos/$GITHUB_REPO/releases/latest"))
                            .await()
                            .parseAs<GithubRelease>()
                            .let {
                                preferences.lastAppCheck().set(Date().time)

                                // Check if latest version is newer than the current version
                                if (isNewVersion(it.version)) {
                                    AppUpdateResult.NewUpdate(it)
                                } else {
                                    AppUpdateResult.NoNewUpdate
                                }
                            }
                    }
                }
            if (doExtrasAfterNewUpdate && result is AppUpdateResult.NewUpdate) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    preferences.appShouldAutoUpdate() != AppDownloadInstallJob.NEVER
                ) {
                    AppDownloadInstallJob.start(context, null, false, waitUntilIdle = true)
                }
                AppUpdateNotifier(context.localeContext).promptUpdate(result.release)
            }

            result
        }
    }

    private fun isNewVersion(
        versionTag: String,
        currentVersion: String = BuildConfig.VERSION_NAME,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.-]".toRegex(), "")
        val oldVersion = currentVersion.replace("[^\\d.-]".toRegex(), "")
        val newPreReleaseVer = newVersion.split("-")
        val oldPreReleaseVer = oldVersion.split("-")
        val newSemVer = newPreReleaseVer.first().split(".").map { it.toInt() }
        val oldSemVer = oldPreReleaseVer.first().split(".").map { it.toInt() }

        oldSemVer.mapIndexed { index, i ->
            if (newSemVer.getOrElse(index) { i } > i) {
                return true
            } else if (newSemVer.getOrElse(index) { i } < i) {
                return false
            }
        }
        // For cases of extreme patch versions (new: 1.2.3.1 vs old: 1.2.3, return true)
        return if (newSemVer.size > oldSemVer.size) {
            true
        } else if (newSemVer.size < oldSemVer.size) {
            false
        } else {
            // If the version numbers match, check the beta versions
            val newPreVersion =
                newPreReleaseVer.getOrNull(1)?.replace("[^\\d.-]".toRegex(), "")?.toIntOrNull()
            val oldPreVersion =
                oldPreReleaseVer.getOrNull(1)?.replace("[^\\d.-]".toRegex(), "")?.toIntOrNull()
            when {
                // For prod, don't bother with betas (current: 1.2.3 vs new: 1.2.3-b1)
                oldPreVersion == null -> false
                // For betas, always use prod builds (current: 1.2.3-b1 vs new: 1.2.3)
                newPreVersion == null -> true
                // For betas, higher beta ver is newer (current: 1.2.3-b1 vs new: 1.2.3-b2)
                else -> (oldPreVersion < newPreVersion)
            }
        }
    }
}

val RELEASE_TAG: String by lazy {
    "v${BuildConfig.VERSION_NAME}"
}

const val GITHUB_REPO: String = "saud-97/TachiyomiS97"

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
