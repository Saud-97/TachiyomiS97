package eu.kanade.tachiyomi.ui.migration

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.util.system.toInt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

object MigrationFlags {
    private const val CHAPTERS = 0b0001
    private const val CATEGORIES = 0b0010
    private const val TRACK = 0b0100
    private const val CUSTOM_MANGA_INFO = 0b1000

    private val coverCache: CoverCache by injectLazy()
    private val db: DatabaseHelper by injectLazy()
    private val customMangaManager: CustomMangaManager by injectLazy()

    val titles get() = arrayOf(R.string.chapters, R.string.categories, R.string.tracking, R.string.custom_manga_info)
    val flags get() = arrayOf(CHAPTERS, CATEGORIES, TRACK, CUSTOM_MANGA_INFO)

    fun hasChapters(value: Int): Boolean = value and CHAPTERS != 0

    fun hasCategories(value: Int): Boolean = value and CATEGORIES != 0

    fun hasTracks(value: Int): Boolean = value and TRACK != 0

    fun hasCustomMangaInfo(value: Int): Boolean = value and CUSTOM_MANGA_INFO != 0

    fun getEnabledFlags(value: Int): List<Boolean> = flags.map { flag -> value and flag != 0 }

    fun getFlagsFromPositions(
        positions: Array<Boolean>,
        manga: Manga?,
    ): Int {
        val flags = flags(manga)
        return positions.foldIndexed(0) { index, accumulated, enabled ->
            accumulated or (if (enabled) flags[index] else 0)
        }
    }

    fun getFlagsFromPositions(positions: Array<Boolean>): Int =
        positions.foldIndexed(0) { index, accumulated, enabled ->
            accumulated or (enabled.toInt() shl index)
        }

    fun flags(manga: Manga?): Array<Int> {
        val flags = arrayOf(CHAPTERS, CATEGORIES).toMutableList()
        if (manga != null) {
            if (db.getTracks(manga).executeAsBlocking().isNotEmpty()) {
                flags.add(TRACK)
            }

            if (coverCache.getCustomCoverFile(manga).exists() || customMangaManager.getManga(manga) != null) {
                flags.add(CUSTOM_MANGA_INFO)
            }
        }
        return flags.toTypedArray()
    }

    private fun titleForFlag(flag: Int): Int =
        when (flag) {
            CHAPTERS -> R.string.chapters
            CATEGORIES -> R.string.categories
            TRACK -> R.string.tracking
            CUSTOM_MANGA_INFO -> R.string.custom_manga_info
            else -> 0
        }

    fun titles(
        context: Context,
        manga: Manga?,
    ): Array<String> = flags(manga).map { context.getString(titleForFlag(it)) }.toTypedArray()
}
