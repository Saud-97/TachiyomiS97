package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * This class is used to manage chapter downloads in the application. It must be instantiated once
 * and retrieved through dependency injection. You can use this class to queue new chapters or query
 * downloaded chapters.
 *
 * @param context the application context.
 */
class DownloadManager(
    val context: Context,
) {
    /**
     * The sources manager.
     */
    private val sourceManager by injectLazy<SourceManager>()

    private val preferences by injectLazy<PreferencesHelper>()

    /**
     * Downloads provider, used to retrieve the folders where the chapters are or should be stored.
     */
    private val provider = DownloadProvider(context)

    /**
     * Cache of downloaded chapters.
     */
    private val cache = DownloadCache(context, provider, sourceManager)

    /**
     * Downloader whose only task is to download chapters.
     */
    private val downloader = Downloader(context, provider, cache, sourceManager)

    val isRunning: Boolean get() = downloader.isRunning

    /**
     * Queue to delay the deletion of a list of chapters until triggered.
     */
    private val pendingDeleter = DownloadPendingDeleter(context)

    /**
     * Downloads queue, where the pending chapters are stored.
     */
    val queue: DownloadQueue
        get() = downloader.queue

    /**
     * Tells the downloader to begin downloads.
     *
     * @return true if it's started, false otherwise (empty queue).
     */
    fun startDownloads(): Boolean {
        val hasStarted = downloader.start()
        DownloadJob.callListeners(downloadManager = this)
        return hasStarted
    }

    /**
     * Tells the downloader to stop downloads.
     *
     * @param reason an optional reason for being stopped, used to notify the user.
     */
    fun stopDownloads(reason: String? = null) = downloader.stop(reason)

    /**
     * Tells the downloader to pause downloads.
     */
    fun pauseDownloads() {
        downloader.pause()
        downloader.stop()
    }

    /**
     * Empties the download queue.
     *
     * @param isNotification value that determines if status is set (needed for view updates)
     */
    fun clearQueue(isNotification: Boolean = false) {
        deletePendingDownloads(*downloader.queue.toTypedArray())
        downloader.clearQueue(isNotification)
        DownloadJob.callListeners(false, this)
    }

    fun startDownloadNow(chapter: Chapter) {
        val download = downloader.queue.find { it.chapter.id == chapter.id } ?: return
        val queue = downloader.queue.toMutableList()
        queue.remove(download)
        queue.add(0, download)
        reorderQueue(queue)
        if (isPaused()) {
            if (DownloadJob.isRunning(context)) {
                downloader.start()
                DownloadJob.callListeners(true, this)
            } else {
                DownloadJob.start(context)
            }
        }
    }

    /**
     * Reorders the download queue.
     *
     * @param downloads value to set the download queue to
     */
    fun reorderQueue(downloads: List<Download>) {
        val wasPaused = isPaused()
        if (downloads.isEmpty()) {
            DownloadJob.stop(context)
            downloader.queue.clear()
            return
        }
        downloader.pause()
        downloader.queue.clear()
        downloader.queue.addAll(downloads)
        if (!wasPaused) {
            downloader.start()
            DownloadJob.callListeners(true, this)
        }
    }

    fun isPaused() = !downloader.isRunning

    fun hasQueue() = downloader.queue.isNotEmpty()

    /**
     * Tells the downloader to enqueue the given list of chapters.
     *
     * @param manga the manga of the chapters.
     * @param chapters the list of chapters to enqueue.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun downloadChapters(
        manga: Manga,
        chapters: List<Chapter>,
        autoStart: Boolean = true,
    ) {
        downloader.queueChapters(manga, chapters, autoStart)
    }

    /**
     * Tells the downloader to enqueue the given list of downloads at the start of the queue.
     *
     * @param downloads the list of downloads to enqueue.
     */
    fun addDownloadsToStartOfQueue(downloads: List<Download>) {
        if (downloads.isEmpty()) return
        queue.toMutableList().apply {
            addAll(0, downloads)
            reorderQueue(this)
        }
        if (!DownloadJob.isRunning(context)) DownloadJob.start(context)
    }

    /**
     * Builds the page list of a downloaded chapter.
     *
     * @param source the source of the chapter.
     * @param manga the manga of the chapter.
     * @param chapter the downloaded chapter.
     * @return the list of pages from the chapter.
     */
    fun buildPageList(
        source: Source,
        manga: Manga,
        chapter: Chapter,
    ): List<Page> {
        val chapterDir = provider.findChapterDir(chapter, manga, source)
        val files =
            chapterDir
                ?.listFiles()
                .orEmpty()
                .filter { "image" in it.type.orEmpty() }

        if (files.isEmpty()) {
            throw Exception(context.getString(R.string.no_pages_found))
        }

        return files
            .sortedBy { it.name }
            .mapIndexed { i, file ->
                Page(i, uri = file.uri).apply { status = Page.State.READY }
            }
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapter the chapter to check.
     * @param manga the manga of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(
        chapter: Chapter,
        manga: Manga,
        skipCache: Boolean = false,
    ): Boolean = cache.isChapterDownloaded(chapter, manga, skipCache)

    /**
     * Returns the download from queue if the chapter is queued for download
     * else it will return null which means that the chapter is not queued for download
     *
     * @param chapter the chapter to check.
     */
    fun getChapterDownloadOrNull(chapter: Chapter): Download? =
        downloader.queue
            .firstOrNull { it.chapter.id == chapter.id && it.chapter.manga_id == chapter.manga_id }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int = cache.getDownloadCount(manga)

    /*fun renameCache(from: String, to: String, source: Long) {
        cache.renameFolder(from, to, source)
    }*/

    /**
     * Calls delete chapter, which deletes temp downloads
     *  @param downloads list of downloads to cancel
     */
    fun deletePendingDownloads(vararg downloads: Download) {
        val downloadsByManga = downloads.groupBy { it.manga.id }
        downloadsByManga.map { entry ->
            val manga = entry.value.first().manga
            val source = entry.value.first().source
            deleteChapters(entry.value.map { it.chapter }, manga, source)
        }
    }

    /**
     * Deletes the directories of a list of partially downloaded chapters.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     */
    fun deleteChapters(
        chapters: List<Chapter>,
        manga: Manga,
        source: Source,
        force: Boolean = false,
    ) {
        val filteredChapters = if (force) chapters else getChaptersToDelete(chapters, manga)
        GlobalScope.launch(Dispatchers.IO) {
            val wasPaused = isPaused()
            if (filteredChapters.isEmpty()) {
                return@launch
            }
            downloader.pause()
            downloader.queue.remove(filteredChapters)
            if (!wasPaused && downloader.queue.isNotEmpty()) {
                downloader.start()
                DownloadJob.callListeners(true)
            } else if (downloader.queue.isEmpty() && DownloadJob.isRunning(context)) {
                DownloadJob.callListeners(false)
                DownloadJob.stop(context)
            } else if (downloader.queue.isEmpty()) {
                DownloadJob.callListeners(false)
                downloader.stop()
            }
            queue.remove(filteredChapters)
            val chapterDirs =
                provider.findChapterDirs(filteredChapters, manga, source) +
                    provider.findTempChapterDirs(
                        filteredChapters,
                        manga,
                        source,
                    )
            chapterDirs.forEach { it.delete() }
            cache.removeChapters(filteredChapters, manga)
            if (cache.getDownloadCount(manga, true) == 0) { // Delete manga directory if empty
                chapterDirs.firstOrNull()?.parentFile?.delete()
            }
            queue.updateListeners()
        }
    }

    /**
     * return the list of all manga folders
     */
    fun getMangaFolders(source: Source): List<UniFile> = provider.findSourceDir(source)?.listFiles()?.toList() ?: emptyList()

    /**
     * Deletes the directories of chapters that were read or have no match
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     */
    fun cleanupChapters(
        allChapters: List<Chapter>,
        manga: Manga,
        source: Source,
        removeRead: Boolean,
        removeNonFavorite: Boolean,
    ): Int {
        var cleaned = 0

        if (removeNonFavorite && !manga.favorite) {
            val mangaFolder = provider.getMangaDir(manga, source)
            cleaned += 1 + (mangaFolder.listFiles()?.size ?: 0)
            mangaFolder.delete()
            cache.removeManga(manga)
            return cleaned
        }

        val filesWithNoChapter = provider.findUnmatchedChapterDirs(allChapters, manga, source)
        cleaned += filesWithNoChapter.size
        cache.removeFolders(filesWithNoChapter.mapNotNull { it.name }, manga)
        filesWithNoChapter.forEach { it.delete() }

        if (removeRead) {
            val readChapters = allChapters.filter { it.read }
            val readChapterDirs = provider.findChapterDirs(readChapters, manga, source)
            readChapterDirs.forEach { it.delete() }
            cleaned += readChapterDirs.size
            cache.removeChapters(readChapters, manga)
        }

        if (cache.getDownloadCount(manga) == 0) {
            val mangaFolder = provider.getMangaDir(manga, source)
            val size = mangaFolder.listFiles()?.size ?: 0
            if (size == 0) {
                mangaFolder.delete()
                cache.removeManga(manga)
            } else {
                Timber.e("Cache and download folder doesn't match for %s", manga.title)
            }
        }
        return cleaned
    }

    /**
     * Deletes the directory of a downloaded manga.
     *
     * @param manga the manga to delete.
     * @param source the source of the manga.
     */
    fun deleteManga(
        manga: Manga,
        source: Source,
    ) {
        downloader.clearQueue(manga, true)
        queue.remove(manga)
        provider.findMangaDir(manga, source)?.delete()
        cache.removeManga(manga)
        queue.updateListeners()
    }

    /**
     * Adds a list of chapters to be deleted later.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     */
    fun enqueueDeleteChapters(
        chapters: List<Chapter>,
        manga: Manga,
    ) {
        pendingDeleter.addChapters(getChaptersToDelete(chapters, manga), manga)
    }

    /**
     * Triggers the execution of the deletion of pending chapters.
     */
    fun deletePendingChapters() {
        val pendingChapters = pendingDeleter.getPendingChapters()
        for ((manga, chapters) in pendingChapters) {
            val source = sourceManager.get(manga.source) ?: continue
            deleteChapters(chapters, manga, source)
        }
    }

    /**
     * Renames an already downloaded chapter
     *
     * @param manga the manga of the chapter.
     * @param oldChapter the existing chapter with the old name.
     * @param newChapter the target chapter with the new name.
     */
    fun renameChapter(
        source: Source,
        manga: Manga,
        oldChapter: Chapter,
        newChapter: Chapter,
    ) {
        val oldNames = provider.getValidChapterDirNames(oldChapter).map { listOf(it, "$it.cbz") }.flatten()
        var newName = provider.getChapterDirName(newChapter)
        val mangaDir = provider.getMangaDir(manga, source)

        // Assume there's only 1 version of the chapter name formats present
        val oldDownload =
            oldNames
                .asSequence()
                .mapNotNull { mangaDir.findFile(it) }
                .firstOrNull() ?: return

        if (oldDownload.isFile && oldDownload.name?.endsWith(".cbz") == true) {
            newName += ".cbz"
        }

        if (oldDownload.name == newName) return

        if (oldDownload.renameTo(newName)) {
            cache.removeChapters(listOf(oldChapter), manga)
            cache.addChapter(newName, manga)
        } else {
            Timber.e("Could not rename downloaded chapter: ${oldNames.joinToString()}")
        }
    }

    // forceRefresh the download cache
    fun refreshCache() {
        cache.forceRenewCache()
    }

    fun addListener(listener: DownloadQueue.DownloadListener) = queue.addListener(listener)

    fun removeListener(listener: DownloadQueue.DownloadListener) = queue.removeListener(listener)

    private fun getChaptersToDelete(
        chapters: List<Chapter>,
        manga: Manga,
    ): List<Chapter> {
        // Retrieve the categories that are set to exclude from being deleted on read
        return if (!preferences.removeBookmarkedChapters().get()) {
            chapters.filterNot { it.bookmark }
        } else {
            chapters
        }
    }
}
