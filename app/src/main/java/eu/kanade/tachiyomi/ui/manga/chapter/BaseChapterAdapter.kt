package eu.kanade.tachiyomi.ui.manga.chapter

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.database.models.Chapter

open class BaseChapterAdapter<T : IFlexible<*>>(
    obj: DownloadInterface,
) : FlexibleAdapter<T>(null, obj, true) {
    val baseDelegate = obj

    interface DownloadInterface {
        fun downloadChapter(position: Int)

        fun startDownloadNow(position: Int)
    }

    interface GroupedDownloadInterface : DownloadInterface {
        fun downloadChapter(
            position: Int,
            chapter: Chapter,
        )

        fun startDownloadNow(
            position: Int,
            chapter: Chapter,
        )
    }
}
