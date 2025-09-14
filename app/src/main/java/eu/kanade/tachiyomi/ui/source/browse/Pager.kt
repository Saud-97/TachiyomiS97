package eu.kanade.tachiyomi.ui.source.browse

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A general pager for source requests (latest updates, popular, search)
 */
abstract class Pager(
    var currentPage: Int = 1,
) {
    var hasNextPage = true
        private set

    protected val results = MutableSharedFlow<Pair<Int, List<SManga>>>()

    fun results(): SharedFlow<Pair<Int, List<SManga>>> = results.asSharedFlow()

    abstract suspend fun requestNextPage()

    suspend fun onPageReceived(mangasPage: MangasPage) {
        val page = currentPage
        currentPage++
        hasNextPage = mangasPage.hasNextPage && mangasPage.mangas.isNotEmpty()
        results.emit(Pair(page, mangasPage.mangas))
    }
}
