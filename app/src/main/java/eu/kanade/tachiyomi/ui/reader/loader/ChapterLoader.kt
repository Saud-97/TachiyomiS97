package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import com.github.junrar.exception.UnsupportedRarV5Exception
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.util.system.withIOContext
import timber.log.Timber

/**
 * Loader used to retrieve the [PageLoader] for a given chapter.
 */
class ChapterLoader(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val manga: Manga,
    private val source: Source,
) {
    /**
     * Assigns the chapter's page loader and loads the its pages. Returns immediately if the chapter
     * is already loaded.
     */
    suspend fun loadChapter(chapter: ReaderChapter) {
        if (chapterIsReady(chapter)) {
            return
        }

        chapter.state = ReaderChapter.State.Loading
        withIOContext {
            Timber.d("Loading pages for ${chapter.chapter.name}")
            try {
                val loader = getPageLoader(chapter)
                chapter.pageLoader = loader

                val pages =
                    loader
                        .getPages()
                        .onEach { it.chapter = chapter }

                if (pages.isEmpty()) {
                    throw Exception(context.getString(R.string.no_pages_found))
                }

                // If the chapter is partially read, set the starting page to the last the user read
                // otherwise use the requested page.
                if (!chapter.chapter.read) {
                    chapter.requestedPage = chapter.chapter.last_page_read
                }

                chapter.state = ReaderChapter.State.Loaded(pages)
            } catch (e: Throwable) {
                chapter.state = ReaderChapter.State.Error(e)
                throw e
            }
        }
    }

    /**
     * Checks [chapter] to be loaded based on present pages and loader in addition to state.
     */
    private fun chapterIsReady(chapter: ReaderChapter): Boolean = chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null

    /**
     * Returns the page loader to use for this [chapter].
     */
    private fun getPageLoader(chapter: ReaderChapter): PageLoader {
        val dbChapter = chapter.chapter
        val isDownloaded = downloadManager.isChapterDownloaded(dbChapter, manga, skipCache = true)
        return when {
            isDownloaded -> DownloadPageLoader(chapter, manga, source, downloadManager, downloadProvider)
            source is HttpSource -> HttpPageLoader(chapter, source)
            source is LocalSource ->
                source.getFormat(chapter.chapter).let { format ->
                    when (format) {
                        is LocalSource.Format.Directory -> DirectoryPageLoader(format.file)
                        is LocalSource.Format.Zip -> ZipPageLoader(format.file)
                        is LocalSource.Format.Rar ->
                            try {
                                RarPageLoader(format.file)
                            } catch (e: UnsupportedRarV5Exception) {
                                error(context.getString(R.string.loader_rar5_error))
                            }
                        is LocalSource.Format.Epub -> EpubPageLoader(format.file)
                    }
                }
            else -> error(context.getString(R.string.source_not_installed))
        }
    }
}
