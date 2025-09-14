package eu.kanade.tachiyomi.ui.setting.debug

import android.view.View
import androidx.core.view.isVisible
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DebugInfoItemBinding

class DebugInfoItem(
    val text: String,
    val header: Boolean,
) : AbstractItem<FastAdapter.ViewHolder<DebugInfoItem>>() {
    /** defines the type defining this item. must be unique. preferably an id */
    override val type: Int = R.id.debug_title

    /** defines the layout which will be used for this item in the list */
    override val layoutRes: Int = R.layout.debug_info_item

    override var identifier = text.hashCode().toLong()

    override fun getViewHolder(v: View): FastAdapter.ViewHolder<DebugInfoItem> = ViewHolder(v)

    class ViewHolder(
        view: View,
    ) : FastAdapter.ViewHolder<DebugInfoItem>(view) {
        val binding = DebugInfoItemBinding.bind(view)

        override fun bindView(
            item: DebugInfoItem,
            payloads: List<Any>,
        ) {
            binding.debugTitle.isVisible = item.header
            binding.debugSummary.isVisible = !item.header
            if (item.header) {
                binding.debugTitle.text = item.text
            } else {
                binding.debugSummary.text = item.text
            }
        }

        override fun unbindView(item: DebugInfoItem) {
            binding.debugTitle.text = ""
            binding.debugSummary.text = ""
        }
    }
}
