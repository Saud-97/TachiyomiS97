package eu.kanade.tachiyomi.ui.recents

import android.text.format.DateUtils
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import java.util.Date

class DateItem(
    val date: Date,
    val addedString: Boolean = false,
) : AbstractHeaderItem<DateItem.Holder>() {
    override fun getLayoutRes(): Int = R.layout.recent_chapters_section_item

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): Holder = Holder(view, adapter as RecentMangaAdapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        holder.bind(this)
    }

    override fun isSwipeable(): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is DateItem) {
            return date == other.date
        }
        return false
    }

    override fun hashCode(): Int = date.hashCode()

    class Holder(
        view: View,
        val adapter: RecentMangaAdapter,
    ) : FlexibleViewHolder(view, adapter, true) {
        private val now = Date().time

        private val sectionText: TextView = view.findViewById(R.id.section_text)
        private val lastUpdatedText: TextView = view.findViewById(R.id.last_updated_text)

        fun bind(item: DateItem) {
            val dateString = DateUtils.getRelativeTimeSpanString(item.date.time, now, DateUtils.DAY_IN_MILLIS)
            sectionText.text =
                if (item.addedString) itemView.context.getString(R.string.fetched_, dateString) else dateString
            lastUpdatedText.isVisible = false
            if (bindingAdapterPosition == 0) {
                sectionText.updatePadding(
                    top =
                        if (adapter.lastUpdatedTime > 0L) {
                            lastUpdatedText.isVisible = true
                            lastUpdatedText.text =
                                lastUpdatedText.context.timeSpanFromNow(
                                    R.string.updates_last_update_info,
                                    adapter.lastUpdatedTime,
                                )
                            18.spToPx + 8.dpToPx
                        } else {
                            4.dpToPx
                        },
                )
            } else {
                sectionText.updatePadding(18.dpToPx)
            }
        }

        override fun onLongClick(view: View?): Boolean {
            super.onLongClick(view)
            return false
        }
    }
}
