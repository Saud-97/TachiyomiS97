package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category

class LibraryHeaderItem(
    private val categoryF: (Int) -> Category,
    val catId: Int,
) : AbstractHeaderItem<LibraryHeaderHolder>() {
    override fun getLayoutRes(): Int = R.layout.library_category_header_item

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): LibraryHeaderHolder = LibraryHeaderHolder(view, adapter as LibraryCategoryAdapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: LibraryHeaderHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        holder.bind(this)
        val layoutParams = holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams
        layoutParams?.isFullSpan = true
    }

    val category: Category
        get() = categoryF(catId)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is LibraryHeaderItem) {
            return category.id == other.category.id
        }
        return false
    }

    override fun isDraggable(): Boolean = false

    override fun isSelectable(): Boolean = false

    override fun hashCode(): Int = (category.id ?: 0L).hashCode()
}
