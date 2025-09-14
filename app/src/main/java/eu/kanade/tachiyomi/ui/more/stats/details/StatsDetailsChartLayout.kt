package eu.kanade.tachiyomi.ui.more.stats.details

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.MPPointF
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.StatsDetailsChartBinding
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.Stats
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.StatsSort
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isLandscape
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class StatsDetailsChartLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        var presenter: StatsDetailsPresenter? = null
        var listener: StatDetailsHeaderListener? = null
        lateinit var binding: StatsDetailsChartBinding
        private val defaultSort = StatsSort.COUNT_DESC
        private val isLandscape: Boolean
            get() = binding.root.context.isLandscape()

        override fun onFinishInflate() {
            super.onFinishInflate()
            binding = StatsDetailsChartBinding.bind(this)
            with(binding) {
                statSort.setOnClickListener { listener?.onSortClicked(this) }
                statsDateText.setOnClickListener {
                    listener?.onDateTextClicked(statsDateText, statsBarChart)
                }
                statsDateStartArrow.setOnClickListener {
                    val presenter = presenter ?: return@setOnClickListener
                    listener?.changeDatesReadDurationWithArrow(presenter.startDate, -1, statsBarChart)
                }
                statsDateEndArrow.setOnClickListener {
                    val presenter = presenter ?: return@setOnClickListener
                    listener?.changeDatesReadDurationWithArrow(presenter.endDate, 1, statsBarChart)
                }
            }
        }

        fun setupChart(presenter: StatsDetailsPresenter?) {
            isVisible = true
            binding.statsChartLayout.isVisible = true
            this.presenter = presenter ?: return
            val selectedStat = presenter.selectedStat
            val statSort = presenter.selectedStatsSort

            binding.statSort.text = context.getString(presenter.selectedStatsSort?.resourceId ?: defaultSort.resourceId)
            binding.statsPieChart.isVisible = false
            binding.statsBarChart.isVisible = false
            binding.statsLineChart.isVisible = false
            binding.statsDateLayout.isVisible = selectedStat == Stats.READ_DURATION
            binding.totalDurationStatsText.isVisible = selectedStat == Stats.READ_DURATION
            binding.statsDateText.text = listener?.getReadDates()
            binding.totalDurationStatsText.text = listener?.getReadDuration()
            binding.statSort.isVisible = !isLandscape &&
                presenter.selectedStat !in
                listOf(
                    Stats.SCORE,
                    Stats.LENGTH,
                    Stats.START_YEAR,
                    Stats.READ_DURATION,
                )

            when (selectedStat) {
                Stats.SERIES_TYPE, Stats.STATUS, Stats.LANGUAGE, Stats.TRACKER, Stats.CATEGORY -> handlePieChart(statSort)
                Stats.SCORE -> handleScoreLayout()
                Stats.LENGTH -> handleLengthLayout()
                Stats.SOURCE, Stats.TAG -> hideChart()
                Stats.START_YEAR -> handleStartYearLayout()
                Stats.READ_DURATION -> handleReadDurationLayout()
                else -> {}
            }
        }

        fun hideChart() {
            if (isLandscape) {
                when (presenter?.selectedStat) {
                    Stats.READ_DURATION -> {
                        binding.statsDateLayout.isVisible = true
                        binding.totalDurationStatsText.isVisible = true
                        binding.statsChartLayout.isVisible = false
                    }
                    else -> {
                        isVisible = false
                    }
                }
            }
        }

        private fun handlePieChart(statSort: StatsSort?) {
            if (statSort == StatsSort.MEAN_SCORE_DESC) {
                hideChart()
                return
            }

            val pieEntries =
                presenter?.currentStats?.map {
                    val progress =
                        if (statSort == StatsSort.COUNT_DESC) {
                            it.count
                        } else {
                            it.chaptersRead
                        }
                    PieEntry(progress.toFloat(), it.label)
                }

            if (pieEntries?.all { it.value == 0f } == true) {
                hideChart()
                return
            }
            val pieDataSet = PieDataSet(pieEntries, "Pie Chart Distribution")
            pieDataSet.colors = presenter?.currentStats?.map { it.color }
            setupPieChart(pieDataSet)
        }

        private fun handleScoreLayout() {
            val scoreMap = StatsHelper.SCORE_COLOR_MAP

            val barEntries =
                scoreMap.map { (score, _) ->
                    BarEntry(
                        score.toFloat(),
                        presenter
                            ?.currentStats
                            ?.find { it.label == score.toString() }
                            ?.count
                            ?.toFloat() ?: 0f,
                    )
                }
//        presenter?.currentStats?.removeAll { it.count == 0 }
//        listener?.assignAdapter()
            if (barEntries.all { it.y == 0f }) return
            val barDataSet = BarDataSet(barEntries, "Score Distribution")
            barDataSet.colors = scoreMap.values.toList()
            setupBarChart(barDataSet)
        }

        private fun handleLengthLayout() {
            val barEntries = ArrayList<BarEntry>()

            presenter?.currentStats?.forEachIndexed { index, stats ->
                barEntries.add(BarEntry(index.toFloat(), stats.count.toFloat()))
            }

            val barDataSet = BarDataSet(barEntries, "Length Distribution")
            barDataSet.colors = presenter?.currentStats?.map { it.color }
            setupBarChart(barDataSet, presenter?.currentStats?.mapNotNull { it.label })
//        presenter?.currentStats?.removeAll { it.count == 0 }
//        listener?.assignAdapter()
        }

        private fun handleReadDurationLayout() {
            val presenter = presenter ?: return
            val barEntries = ArrayList<BarEntry>()

            presenter.historyByDayAndManga.entries.forEachIndexed { index, entry ->
                barEntries.add(
                    BarEntry(
                        index.toFloat(),
                        entry.value.values
                            .sumOf { it.sumOf { h -> h.time_read } }
                            .toFloat(),
                    ),
                )
            }
            if (barEntries.all { it.y == 0f }) {
                hideChart()
                return
            }
            val barDataSet = BarDataSet(barEntries, "Read Duration Distribution")
            barDataSet.color = StatsHelper.PIE_CHART_COLOR_LIST[1]
            setupBarChart(
                barDataSet,
                presenter.historyByDayAndManga.keys
                    .map { presenter.getCalendarShortDay(it) }
                    .toList(),
                true,
            )
        }

        private fun handleStartYearLayout() {
            presenter?.currentStats?.sortBy { it.label }

            val lineEntries =
                presenter
                    ?.currentStats
                    ?.filterNot { it.label?.toFloatOrNull() == null }
                    ?.map { Entry(it.label?.toFloat()!!, it.count.toFloat()) }
            if (lineEntries.isNullOrEmpty()) return

            val lineDataSet = LineDataSet(lineEntries, "Start Year Distribution")
            lineDataSet.color = context.getResourceColor(R.attr.colorOnBackground)
            lineDataSet.setDrawFilled(true)
            lineDataSet.fillDrawable = ContextCompat.getDrawable(context, R.drawable.line_chart_fill)
            setupLineChart(lineDataSet)
        }

        private fun setupPieChart(pieDataSet: PieDataSet) {
            with(binding) {
                statsPieChart.clear()
                statsPieChart.invalidate()

                statsPieChart.isVisible = true
                statsBarChart.isVisible = false
                statsLineChart.isVisible = false

                try {
                    val pieData = PieData(pieDataSet)
                    pieData.setDrawValues(false)

                    statsPieChart.apply {
                        setHoleColor(ContextCompat.getColor(context, android.R.color.transparent))
                        setDrawEntryLabels(false)
                        setTouchEnabled(false)
                        description.isEnabled = false
                        legend.isEnabled = false
                        data = pieData
                        invalidate()
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }

        private fun setupBarChart(
            barDataSet: BarDataSet,
            xAxisLabel: List<String>? = null,
            touchEnabled: Boolean = false,
        ) {
            val presenter = presenter ?: return
            with(binding) {
                statsBarChart.data?.clearValues()
                statsBarChart.xAxis.valueFormatter = null
                statsBarChart.notifyDataSetChanged()
                statsBarChart.clear()
                statsBarChart.invalidate()
                statsBarChart.axisLeft.resetAxisMinimum()
                statsBarChart.axisLeft.resetAxisMaximum()

                statsPieChart.isVisible = false
                statsBarChart.isVisible = true
                statsLineChart.isVisible = false

                try {
                    val newValueFormatter =
                        object : ValueFormatter() {
                            override fun getFormattedValue(value: Float) =
                                if (touchEnabled) value.toLong().getReadDuration() else value.toInt().toString()
                        }

                    val barData = BarData(barDataSet)
                    barData.setValueTextColor(context.getResourceColor(R.attr.colorOnBackground))
                    barData.barWidth = 0.6F
                    barData.setValueFormatter(newValueFormatter)
                    barData.setValueTextSize(10f)
                    barData.setDrawValues(!touchEnabled)
                    statsBarChart.axisLeft.isEnabled = touchEnabled
                    statsBarChart.axisRight.isEnabled = false

                    statsBarChart.xAxis.apply {
                        setDrawGridLines(false)
                        position = XAxis.XAxisPosition.BOTTOM
                        setLabelCount(barDataSet.entryCount, false)
                        textColor = context.getResourceColor(R.attr.colorOnBackground)

                        if (!xAxisLabel.isNullOrEmpty()) {
                            valueFormatter =
                                object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String =
                                        if (value < xAxisLabel.size) xAxisLabel[value.toInt()] else ""
                                }
                        }
                    }

                    statsBarChart.apply {
                        setTouchEnabled(touchEnabled)
                        isDragEnabled = false
                        isDoubleTapToZoomEnabled = false
                        description.isEnabled = false
                        legend.isEnabled = false

                        if (touchEnabled) {
                            val mv = MyMarkerView(context, R.layout.custom_marker_view)
                            mv.chartView = this
                            marker = mv

                            axisLeft.apply {
                                textColor = context.getResourceColor(R.attr.colorOnBackground)
                                axisLineColor = context.getResourceColor(R.attr.colorOnBackground)
                                valueFormatter = newValueFormatter
                                val topValue = barData.yMax.getRoundedMaxLabel()
                                axisMaximum = topValue
                                axisMinimum = 0f
                                setLabelCount(4, true)
                            }

                            setOnChartValueSelectedListener(
                                object : OnChartValueSelectedListener {
                                    override fun onValueSelected(
                                        e: Entry,
                                        h: Highlight,
                                    ) {
                                        highlightValue(h)
                                        listener?.onBarValueChanged(h, e)
                                        statsDateText.text = listener?.getReadDates()
                                        totalDurationStatsText.text = listener?.getReadDuration()
                                    }

                                    override fun onNothingSelected() {
                                        listener?.onBarValueChanged(null, null)
                                        statsDateText.text = listener?.getReadDates()
                                        totalDurationStatsText.text = listener?.getReadDuration()
                                    }
                                },
                            )
                        }
                        data = barData
                        invalidate()
                        listener?.getHighlight()?.let { highlightValue(it) }
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }

        /**
         * Round the rounded max label of the bar chart to avoid weird values
         */
        private fun Float.getRoundedMaxLabel(): Float {
            val longValue = toLong()
            val hours = TimeUnit.MILLISECONDS.toHours(longValue) % 24
            val minutes = TimeUnit.MILLISECONDS.toMinutes(longValue) % 60

            val multiple =
                when {
                    hours > 1L -> 3600 / 2 // 30min
                    minutes >= 15L || hours == 1L -> 300 * 3 // 15min
                    else -> 60 * 3 // 3min
                } * 1000
            return ceil(this / multiple) * multiple
        }

        private fun setupLineChart(lineDataSet: LineDataSet) {
            with(binding) {
                statsLineChart.data?.clearValues()
                statsLineChart.fitScreen()
                statsLineChart.notifyDataSetChanged()
                statsLineChart.clear()
                statsLineChart.invalidate()

                statsPieChart.isVisible = false
                statsBarChart.isVisible = false
                statsLineChart.isVisible = true

                try {
                    val newValueFormatter =
                        object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String = value.toInt().toString()
                        }

                    val lineData = LineData(lineDataSet)
                    lineData.setValueTextColor(context.getResourceColor(R.attr.colorOnBackground))
                    lineData.setValueFormatter(newValueFormatter)
                    lineData.setValueTextSize(10f)
                    statsLineChart.axisLeft.isEnabled = false
                    statsLineChart.axisRight.isEnabled = false

                    statsLineChart.xAxis.apply {
                        setDrawGridLines(false)
                        position = XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        textColor = context.getResourceColor(R.attr.colorOnBackground)

                        valueFormatter =
                            object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String = value.toInt().toString()
                            }
                    }

                    statsLineChart.apply {
                        description.isEnabled = false
                        legend.isEnabled = false
                        data = lineData
                        invalidate()
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }

        /**
         * Custom MarkerView displayed when a bar is selected in the bar chart
         */
        inner class MyMarkerView(
            context: Context?,
            layoutResource: Int,
        ) : MarkerView(context, layoutResource) {
            private val markerText: TextView = findViewById(R.id.marker_text)

            override fun refreshContent(
                e: Entry,
                highlight: Highlight,
            ) {
                markerText.text = e.y.toLong().getReadDuration()
                super.refreshContent(e, highlight)
            }

            override fun getOffset(): MPPointF = MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
        }

        interface StatDetailsHeaderListener {
            fun onBarValueChanged(
                highlight: Highlight?,
                e: Entry?,
            )

            fun onSortClicked(binding: StatsDetailsChartBinding?)

            fun onDateTextClicked(
                statsDateText: TextView,
                barChart: BarChart,
            )

            fun changeDatesReadDurationWithArrow(
                referenceDate: Calendar,
                toAdd: Int,
                barChart: BarChart,
            )

            fun getReadDuration(): String

            fun getReadDates(): String

            fun getHighlight(): Highlight?
        }
    }
