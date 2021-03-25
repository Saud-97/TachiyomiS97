package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.updater.UpdaterJob
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.ui.library.LibraryPresenter
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object Migrations {

    /**
     * Performs a migration when the application is updated.
     *
     * @param preferences Preferences of the application.
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(preferences: PreferencesHelper): Boolean {
        val context = preferences.context
        val oldVersion = preferences.lastVersionCode().getOrDefault()
        if (oldVersion < BuildConfig.VERSION_CODE) {
            preferences.lastVersionCode().set(BuildConfig.VERSION_CODE)

            if (oldVersion == 0) {
                if (BuildConfig.INCLUDE_UPDATER && preferences.automaticUpdates()) {
                    UpdaterJob.setupTask()
                }
                ExtensionUpdateJob.setupTask()
                LibraryUpdateJob.setupTask()
                return BuildConfig.DEBUG
            }

            if (oldVersion < 14) {
                // Restore jobs after upgrading to evernote's job scheduler.
                if (BuildConfig.INCLUDE_UPDATER && preferences.automaticUpdates()) {
                    UpdaterJob.setupTask()
                }
                LibraryUpdateJob.setupTask()
            }
            if (oldVersion < 15) {
                // Delete internal chapter cache dir.
                File(context.cacheDir, "chapter_disk_cache").deleteRecursively()
            }
            if (oldVersion < 19) {
                // Move covers to external files dir.
                val oldDir = File(context.externalCacheDir, "cover_disk_cache")
                if (oldDir.exists()) {
                    val destDir = context.getExternalFilesDir("covers")
                    if (destDir != null) {
                        oldDir.listFiles()?.forEach {
                            it.renameTo(File(destDir, it.name))
                        }
                    }
                }
            }
            if (oldVersion < 26) {
                // Delete external chapter cache dir.
                val extCache = context.externalCacheDir
                if (extCache != null) {
                    val chapterCache = File(extCache, "chapter_disk_cache")
                    if (chapterCache.exists()) {
                        chapterCache.deleteRecursively()
                    }
                }
            }
            if (oldVersion < 54) {
                DownloadProvider(context).renameChapters()
            }
            if (oldVersion < 62) {
                LibraryPresenter.updateDB()
                // Restore jobs after migrating from Evernote's job scheduler to WorkManager.
                if (BuildConfig.INCLUDE_UPDATER && preferences.automaticUpdates()) {
                    UpdaterJob.setupTask()
                }
                LibraryUpdateJob.setupTask()
                BackupCreatorJob.setupTask(context)
                ExtensionUpdateJob.setupTask()
            }
            if (oldVersion < 66) {
                LibraryPresenter.updateCustoms()
            }
            if (oldVersion < 68) {
                // Force MAL log out due to login flow change
                // v67: switched from scraping to WebView
                // v68: switched from WebView to OAuth
                val trackManager = Injekt.get<TrackManager>()
                if (trackManager.myAnimeList.isLogged) {
                    trackManager.myAnimeList.logout()
                    context.toast(R.string.myanimelist_relogin)
                }
            }
            return true
        }
        return false
    }
}
