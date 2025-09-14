package eu.kanade.tachiyomi.ui.source.browse

import eu.kanade.tachiyomi.source.CatalogueSource

/**
 * LatestUpdatesPager inherited from the general Pager.
 */
class LatestUpdatesPager(
    val source: CatalogueSource,
) : Pager() {
    override suspend fun requestNextPage() {
        val mangasPage = source.getLatestUpdates(currentPage)
        onPageReceived(mangasPage)
    }
}
