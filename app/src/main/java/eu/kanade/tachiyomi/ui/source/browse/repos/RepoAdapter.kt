package eu.kanade.tachiyomi.ui.source.browse.repos

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem

/**
 * Custom adapter for repos.
 *
 * @param controller The containing controller.
 */
class RepoAdapter(
    controller: RepoController,
) : FlexibleAdapter<AbstractFlexibleItem<*>>(null, controller, true) {
    /**
     * Listener called when an item of the list is released.
     */
    val repoItemListener: RepoItemListener = controller

    /**
     * Clears the active selections from the model.
     */
    fun resetEditing(position: Int) {
        for (i in 0..itemCount) {
            (getItem(i) as? RepoItem)?.isEditing = false
        }
        (getItem(position) as? RepoItem)?.isEditing = true
        notifyDataSetChanged()
    }

    interface RepoItemListener {
        /**
         * Called when an item of the list is released.
         */
        fun onLogoClick(position: Int)

        fun onRepoRename(
            position: Int,
            newName: String,
        ): Boolean

        fun onItemDelete(position: Int)
    }
}
