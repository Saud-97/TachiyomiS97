package eu.kanade.tachiyomi.ui.library.filter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.textview.MaterialTextView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.listeners.ClickEventHook
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ExpandedFilterItemBinding
import eu.kanade.tachiyomi.databinding.ExpandedFilterItemTextViewBinding
import eu.kanade.tachiyomi.util.system.dpToPx

class ExpandedFilterItem(
    val filter: LibraryFilter,
) : AbstractItem<FastAdapter.ViewHolder<ExpandedFilterItem>>() {
    /** defines the type defining this item. must be unique. preferably an id */
    override val type: Int = R.id.filter_title

    /** defines the layout which will be used for this item in the list */
    override val layoutRes: Int = R.layout.expanded_filter_item

    override var identifier = filter.hashCode().toLong()

    override fun getViewHolder(v: View): FastAdapter.ViewHolder<ExpandedFilterItem> = ViewHolder(v)

    class ViewHolder(
        view: View,
    ) : FastAdapter.ViewHolder<ExpandedFilterItem>(view) {
        val binding = ExpandedFilterItemBinding.bind(view)

        val textViews: List<MaterialTextView>
            get() =
                binding.filterLinearLayout.children
                    .toList()
                    .filterIsInstance<MaterialTextView>()

        @Suppress("UNCHECKED_CAST")
        override fun bindView(
            item: ExpandedFilterItem,
            payloads: List<Any>,
        ) {
            binding.filterLinearLayout.removeAllViews()
            binding.filterTitle.text = item.filter.headerName
            val allTextView =
                ExpandedFilterItemTextViewBinding.inflate(LayoutInflater.from(itemView.context)).root
            binding.filterLinearLayout.addView(allTextView)
            allTextView.text = itemView.context.getString(R.string.all)
            for (i in 0..<item.filter.filters.size) {
                val divider = MaterialDivider(itemView.context)
                binding.filterLinearLayout.addView(divider)
                divider.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = 1.dpToPx
                }
                val view =
                    ExpandedFilterItemTextViewBinding.inflate(LayoutInflater.from(itemView.context)).root
                binding.filterLinearLayout.addView(view)
                view.text = item.filter.filters[i]
            }
            textViews.forEachIndexed { index, view ->
                view.isActivated = item.filter.activeFilter == index
                view.setOnClickListener { tV ->
                    (
                        (bindingAdapter as? FastAdapter<ExpandedFilterItem>)?.eventHooks?.first()
                            as? ClickEventHook<ExpandedFilterItem>
                    )?.onClick(
                        tV,
                        bindingAdapterPosition,
                        bindingAdapter as FastAdapter<ExpandedFilterItem>,
                        item,
                    )
                }
            }
        }

        override fun unbindView(item: ExpandedFilterItem) {
            binding.filterTitle.text = ""
            binding.filterLinearLayout.removeAllViews()
        }
    }
}
