package eu.kanade.tachiyomi.ui.migration

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import uy.kohesive.injekt.api.get

/**
 * Adapter that holds the catalogue cards.
 *
 * @param allClickListener instance of [MigrationController].
 */
class SourceAdapter(
    val allClickListener: OnAllClickListener,
) : FlexibleAdapter<IFlexible<*>>(null, allClickListener, true) {
    init {
        setDisplayHeadersAtStartUp(true)
    }

    /**
     * Listener which should be called when user clicks select.
     */
    interface OnAllClickListener {
        fun onAllClick(position: Int)
    }
}
