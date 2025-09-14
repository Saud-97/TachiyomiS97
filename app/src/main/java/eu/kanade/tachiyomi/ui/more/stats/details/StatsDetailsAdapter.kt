package eu.kanade.tachiyomi.ui.more.stats.details

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.bold
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ListStatsDetailsBinding
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.Stats
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.StatsData
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.roundToTwoDecimal

class StatsDetailsAdapter(
    internal val context: Context,
    var stat: Stats,
    statList: MutableList<StatsData>,
) : RecyclerView.Adapter<StatsDetailsAdapter.StatsDetailsHolder>() {
    var listener: OnItemClickedListener? = null

    var list = statList.filterEmpty()
        private set
    var mainList: MutableList<StatsData> = statList.filterEmpty()
        set(value) {
            val filtered = value.filterEmpty()
            field = filtered
            list = filtered
        }

    private fun Iterable<StatsData>.filterEmpty(): MutableList<StatsData> =
        filter {
            when (stat) {
                Stats.SCORE, Stats.LENGTH -> it.count > 0
                else -> true
            }
        }.toMutableList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): StatsDetailsHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_stats_details, parent, false)
        return StatsDetailsHolder(view)
    }

    override fun getItemId(position: Int): Long = list[position].id ?: list[position].label?.hashCode()?.toLong() ?: 0L

    override fun onBindViewHolder(
        holder: StatsDetailsHolder,
        position: Int,
    ) {
        when (stat) {
            Stats.SCORE -> handleScoreLayout(holder, position)
            Stats.TAG, Stats.SOURCE -> handleRankedLayout(holder, position)
            Stats.READ_DURATION -> handleDurationLayout(holder, position)
            else -> handleLayout(holder, position)
        }
        holder.itemView.setOnClickListener {
            list[position].let { item -> listener?.onItemClicked(item.id, item.label) }
        }
    }

    override fun getItemCount(): Int = list.size

    private fun handleLayout(
        holder: StatsDetailsHolder,
        position: Int,
    ) {
        val item = list[position]
        with(holder.binding) {
            statsRankLayout.isVisible = false
            statsDataLayout.isVisible = true
            statsScoreStarImage.isVisible = true
            statsSublabelText.isVisible = false

            statsLabelText.setTextColor(
                item.color ?: context.getResourceColor(R.attr.colorOnBackground),
            )
            val label =
                item.label?.let {
                    if (stat == Stats.LENGTH) {
                        val max = item.id?.toInt() ?: 0
                        root.resources.getQuantityString(R.plurals.chapters_plural, max, it)
                    } else {
                        it
                    }
                } ?: ""
            statsLabelText.text = label.uppercase()
            if (item.iconRes != null) {
                logoContainer.isVisible = true
                item.iconBGColor?.let { logoContainer.setCardBackgroundColor(it) }
                val padding =
                    if (Color.alpha(item.iconBGColor ?: Color.TRANSPARENT) == 0) 0 else 2.dpToPx
                logoIcon.setPadding(padding)
                logoIcon.setImageResource(item.iconRes!!)
            } else {
                logoContainer.isVisible = false
            }
            statsCountText.text = getCountText(item)
            statsCountPercentageText.text = getCountPercentageText(item)
            statsProgressText.text = getProgressText(item)
            statsProgressPercentageText.text = getProgressPercentageString(item)
            val score = item.meanScore?.roundToTwoDecimal()?.toString() ?: ""
            statsMeanScoreLayout.isVisible = score.isNotBlank()
            statsScoreText.text = score
            statsReadDurationText.text =
                item.readDuration.getReadDuration(context.getString(R.string.none))
        }
    }

    private fun handleScoreLayout(
        holder: StatsDetailsHolder,
        position: Int,
    ) {
        val item = list[position]
        with(holder.binding) {
            statsRankLayout.isVisible = false
            statsMeanScoreLayout.isVisible = false
            statsDataLayout.isVisible = true
            statsScoreStarImage.isVisible = true
            statsSublabelText.isVisible = false
            logoContainer.isVisible = false

            statsLabelText.setTextColor(
                item.color ?: context.getResourceColor(R.attr.colorOnBackground),
            )
            val formattedScore = item.label?.uppercase() + if (item.label?.toIntOrNull() != null) "★" else ""
            statsLabelText.text = formattedScore
            statsCountText.text = getCountText(item)
            statsCountPercentageText.text = getCountPercentageText(item)
            statsProgressText.text = getProgressText(item)
            statsProgressPercentageText.text = getProgressPercentageString(item)
            statsReadDurationText.text =
                item.readDuration.getReadDuration(context.getString(R.string.none))
        }
    }

    private fun handleRankedLayout(
        holder: StatsDetailsHolder,
        position: Int,
    ) {
        val item = list[position]
        with(holder.binding) {
            statsRankLayout.isVisible = true
            statsDataLayout.isVisible = true
            statsScoreStarImage.isVisible = true
            statsSublabelText.isVisible = false
            logoContainer.isVisible = false

            statsRankText.text = String.format("%02d.", position + 1)
            statsLabelText.setTextColor(
                item.color ?: context.getResourceColor(R.attr.colorOnBackground),
            )
            statsLabelText.text = item.label?.uppercase()
            if (item.icon != null) {
                logoContainer.isVisible = true
                logoContainer.setCardBackgroundColor(Color.TRANSPARENT)
                logoIcon.setImageDrawable(item.icon!!)
                logoIcon.setPadding(0)
            } else {
                logoContainer.isVisible = false
            }
            statsCountText.text = getCountText(item)
            statsCountPercentageText.text = getCountPercentageText(item)
            statsProgressText.text = getProgressText(item)
            statsProgressPercentageText.text = getProgressPercentageString(item)
            val score = item.meanScore?.roundToTwoDecimal()?.toString() ?: ""
            statsMeanScoreLayout.isVisible = score.isNotBlank()
            statsScoreText.text = score
            statsReadDurationText.text =
                item.readDuration.getReadDuration(context.getString(R.string.none))
        }
    }

    private fun handleDurationLayout(
        holder: StatsDetailsHolder,
        position: Int,
    ) {
        val item = list[position]
        with(holder.binding) {
            statsRankLayout.isVisible = true
            statsMeanScoreLayout.isVisible = true
            statsDataLayout.isVisible = false
            statsScoreStarImage.isVisible = false
            logoContainer.isVisible = false

            statsRankText.text = String.format("%02d.", position + 1)
            statsLabelText.setTextColor(
                item.color ?: context.getResourceColor(R.attr.colorOnBackground),
            )
            statsLabelText.text = item.label?.uppercase()
            statsScoreText.text =
                item.readDuration.getReadDuration(context.getString(R.string.none))
            statsSublabelText.isVisible = !item.subLabel.isNullOrBlank()
            statsSublabelText.text = item.subLabel
        }
    }

    private fun getCountText(item: StatsData): SpannableStringBuilder = SpannableStringBuilder().bold { append(item.count.toString()) }

    private fun getCountPercentageText(item: StatsData): String {
        val sumCount = list.sumOf { it.count.toDouble() }.coerceAtLeast(1.0)
        val percentage = (item.count / sumCount * 100).roundToTwoDecimal()
        return "($percentage%)"
    }

    private fun getProgressText(item: StatsData): SpannableStringBuilder =
        SpannableStringBuilder().bold { append(item.chaptersRead.toString()) }.apply {
            if (item.totalChapters != 0) append(" / ${item.totalChapters}")
        }

    private fun getProgressPercentageString(item: StatsData): String {
        if (item.chaptersRead == 0) return "(0%)"
        val percentage = (item.chaptersRead / list.sumOf { it.chaptersRead.toDouble() } * 100).roundToTwoDecimal()
        return "($percentage%)"
    }

    fun filter(text: String) {
        val oldCount = list.size
        list =
            if (text.isEmpty()) {
                mainList
            } else {
                mainList.filter { it.label?.contains(text, true) == true }.toMutableList()
            }
        val newCount = list.size
        if (oldCount > newCount) {
            notifyItemRangeRemoved(newCount, oldCount - newCount)
        } else if (oldCount < newCount) {
            notifyItemRangeInserted(oldCount, newCount - oldCount)
        }
        notifyItemRangeChanged(0, newCount)
    }

    class StatsDetailsHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val binding = ListStatsDetailsBinding.bind(view)
    }

    fun interface OnItemClickedListener {
        fun onItemClicked(
            id: Long?,
            label: String?,
        )
    }

    override fun getItemViewType(position: Int): Int = R.layout.list_stats_details
}
