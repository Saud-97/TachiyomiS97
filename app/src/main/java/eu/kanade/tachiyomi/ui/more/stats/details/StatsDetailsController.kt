package eu.kanade.tachiyomi.ui.more.stats.details

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.PluralsRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.util.Pair
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.ConcatAdapter
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.databinding.StatsDetailsChartBinding
import eu.kanade.tachiyomi.databinding.StatsDetailsControllerBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.library.FilteredLibraryController
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper.getReadDuration
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.Stats
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsPresenter.StatsSort
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isLandscape
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toInt
import eu.kanade.tachiyomi.util.system.toLocalCalendar
import eu.kanade.tachiyomi.util.system.toUtcCalendar
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class StatsDetailsController :
    BaseCoroutineController<StatsDetailsControllerBinding, StatsDetailsPresenter>(),
    SmallToolbarInterface,
    StatsDetailsChartLayout.StatDetailsHeaderListener {
    override val presenter = StatsDetailsPresenter()
    private var query = ""
    private val concatAdapter: ConcatAdapter? get() = binding.statsRecyclerView.adapter as? ConcatAdapter
    private val statsAdapter: StatsDetailsAdapter?
        get() = concatAdapter?.adapters?.last() as? StatsDetailsAdapter
    lateinit var searchView: SearchView
    lateinit var searchItem: MenuItem

    private val defaultStat = Stats.SERIES_TYPE
    private val defaultSort = StatsSort.COUNT_DESC
    private var toolbarIsColored = false
    private var colorAnimator: ValueAnimator? = null
    private var highlightedBar: Triple<Float, Float, Int>? = null

    /**
     * Selected day in the read duration stat
     */
    private var highlightedDay: Calendar? = null
    private var jobReadDuration: Job? = null

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle() = resources?.getString(R.string.statistics_details)

    override fun createBinding(inflater: LayoutInflater) = StatsDetailsControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(binding.statsRecyclerView, false, liftOnScroll = { colorToolbar(it) })
        setHasOptionsMenu(true)

        if (presenter.selectedStat == null) {
            resetFilters(true)
        }

        resetAndSetup()
        initializeChips()
        with(binding) {
            chartLinearLayout?.root?.doOnApplyWindowInsetsCompat { view, insets, _ ->
                view.updatePaddingRelative(
                    bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                )
            }
            chartLinearLayout?.statSort?.isVisible = false

            statsClearButtonContainer.compatToolTipText = activity?.getString(R.string.clear_filters)
            statsClearButtonContainer.setOnClickListener {
                resetFilters()
                searchView.clearFocus()
                searchItem.collapseActionView()
                resetAndSetup(keepAdapter = true)
                initializeChips()
            }

            chipStat.setOnClickListener {
                searchView.clearFocus()
                activity
                    ?.materialAlertDialog()
                    ?.setTitle(R.string.stat)
                    ?.setSingleChoiceItems(
                        presenter.getStatsArray(),
                        Stats.entries.indexOf(presenter.selectedStat),
                    ) { dialog, which ->
                        val newSelection = Stats.entries[which]
                        if (newSelection == presenter.selectedStat) return@setSingleChoiceItems
                        chipStat.text = activity?.getString(newSelection.resourceId)
                        presenter.selectedStat = newSelection
                        chipStat.setColors((presenter.selectedStat != defaultStat).toInt())

                        dialog.dismiss()
                        searchItem.collapseActionView()
                        resetAndSetup()
                        highlightedBar = null
                        highlightedDay = null
                        binding.statsRecyclerView.scrollToPosition(0)
                    }?.setNegativeButton(android.R.string.cancel, null)
                    ?.show()
            }
            chipSeriesType.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.seriesTypeStats,
                    presenter.selectedSeriesType,
                    R.string.series_type,
                    R.plurals._series_types,
                )
            }
            chipSeriesType.setOnCloseIconClickListener {
                if (presenter.selectedSeriesType.isNotEmpty()) {
                    presenter.selectedSeriesType = mutableSetOf()
                    chipSeriesType.reset(R.string.series_type)
                } else {
                    chipSeriesType.callOnClick()
                }
            }
            chipSource.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.sources.toTypedArray(),
                    presenter.selectedSource,
                    R.string.source,
                    R.plurals._sources,
                )
            }
            chipSource.setOnCloseIconClickListener {
                if (presenter.selectedSource.isNotEmpty()) {
                    presenter.selectedSource = mutableSetOf()
                    chipSource.reset(R.string.source)
                } else {
                    chipSource.callOnClick()
                }
            }
            chipStatus.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.statusStats,
                    presenter.selectedStatus,
                    R.string.status,
                    R.plurals._statuses,
                )
            }
            chipStatus.setOnCloseIconClickListener {
                if (presenter.selectedStatus.isNotEmpty()) {
                    presenter.selectedStatus = mutableSetOf()
                    chipStatus.reset(R.string.status)
                } else {
                    chipStatus.callOnClick()
                }
            }
            chipLanguage.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.languagesStats.values.toTypedArray(),
                    presenter.selectedLanguage,
                    R.string.language,
                    R.plurals._languages,
                )
            }
            chipLanguage.setOnCloseIconClickListener {
                if (presenter.selectedLanguage.isNotEmpty()) {
                    presenter.selectedLanguage = mutableSetOf()
                    chipLanguage.reset(R.string.language)
                } else {
                    chipLanguage.callOnClick()
                }
            }
            chipCategory.setOnClickListener {
                (it as Chip).setMultiChoiceItemsDialog(
                    presenter.categoriesStats,
                    presenter.selectedCategory,
                    R.string.category,
                    R.plurals.category_plural,
                )
            }
            chipCategory.setOnCloseIconClickListener {
                if (presenter.selectedCategory.isNotEmpty()) {
                    presenter.selectedCategory = mutableSetOf()
                    chipCategory.reset(R.string.category)
                } else {
                    chipCategory.callOnClick()
                }
            }
            statSort?.setOnClickListener { onSortClicked(headerBinding) }
            colorToolbar(binding.statsRecyclerView.canScrollVertically(-1))
        }
    }

    fun updateLibrary() {
        presenter.libraryMangas = presenter.getLibrary()
    }

    /** Set the toolbar to fully transparent or colored and translucent */
    private fun colorToolbar(isColor: Boolean) {
        if (isColor == toolbarIsColored) return
        val activity = activity ?: return
        toolbarIsColored = isColor
        if (isControllerVisible) setTitle()
        val scrollingColor = activity.getResourceColor(R.attr.colorPrimaryVariant)
        val topColor = activity.getResourceColor(R.attr.colorSurface)
        colorAnimator?.cancel()
        val view = binding.filterConstraintLayout ?: binding.statsHorizontalScroll
        val percent =
            ImageUtil.getPercentOfColor(
                view.backgroundColor ?: Color.TRANSPARENT,
                activity.getResourceColor(R.attr.colorSurface),
                activity.getResourceColor(R.attr.colorPrimaryVariant),
            )
        val cA =
            ValueAnimator.ofFloat(
                percent,
                toolbarIsColored.toInt().toFloat(),
            )
        colorAnimator = cA
        colorAnimator?.addUpdateListener { animator ->
            view.setBackgroundColor(
                ColorUtils.blendARGB(
                    topColor,
                    scrollingColor,
                    animator.animatedValue as Float,
                ),
            )
        }
        cA.start()
    }

    /**
     * Initialize the chips state
     */
    private fun initializeChips() {
        with(binding) {
            chipStat.text =
                activity?.getString(presenter.selectedStat?.resourceId ?: defaultStat.resourceId)
            chipStat.setColors((presenter.selectedStat != defaultStat).toInt())
            chipSeriesType.setState(
                presenter.selectedSeriesType,
                R.string.series_type,
                R.plurals._series_types,
            )
            chipSource.setState(presenter.selectedSource, R.string.source, R.plurals._sources)
            chipStatus.setState(presenter.selectedStatus, R.string.status, R.plurals._statuses)
            chipLanguage.setState(
                presenter.selectedLanguage,
                R.string.language,
                R.plurals._languages,
            )
            chipCategory.setState(
                presenter.selectedCategory,
                R.string.category,
                R.plurals.category_plural,
            )
            statsSortTextView?.text =
                activity?.getString(
                    presenter.selectedStatsSort?.resourceId ?: defaultSort.resourceId,
                )
        }
    }

    private val headerBinding: StatsDetailsChartBinding?
        get() =
            binding.chartLinearLayout
                ?: (
                    binding.statsRecyclerView.findViewHolderForAdapterPosition(
                        0,
                    ) as? HeaderStatsDetailsAdapter.HeaderStatsDetailsHolder
                )?.binding

    private val statsSortTextView: TextView?
        get() = binding.statSort ?: headerBinding?.statSort

    /**
     * Changes week of the read duration stat
     * @param toAdd whether to add or remove
     */
    private fun changeReadDurationPeriod(toAdd: Int) {
        presenter.changeReadDurationPeriod(toAdd)
        binding.progress.isVisible = true
        jobReadDuration =
            viewScope.launchIO {
                presenter.updateMangaHistory()
                presenter.getStatisticData()
            }
    }

    private fun updateHighlightedValue(barChart: BarChart) {
        val highlightValue =
            presenter.historyByDayAndManga.keys.toTypedArray().indexOfFirst {
                it.get(Calendar.DAY_OF_YEAR) == highlightedDay?.get(Calendar.DAY_OF_YEAR) &&
                    it.get(Calendar.YEAR) == highlightedDay?.get(Calendar.YEAR)
            }
        if (highlightValue == -1) return
        barChart.highlightValue(highlightValue.toFloat(), 0)
        barChart.marker.refreshContent(
            barChart.data.dataSets[0].getEntryForXValue(highlightValue.toFloat(), 0f),
            barChart.getHighlightByTouchPoint(highlightValue.toFloat(), 0f),
        )
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.stats_bar, menu)
        searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = activity?.getString(R.string.search_, activity?.getString(R.string.statistics)?.lowercase(Locale.ROOT))
        if (query.isNotBlank() && (!searchItem.isActionViewExpanded || searchView.query != query)) {
            searchItem.expandActionView()
            setSearchViewListener(searchView)
            searchView.setQuery(query, true)
            searchView.clearFocus()
        } else {
            setSearchViewListener(searchView)
        }

        searchItem.fixExpand(onExpand = { invalidateMenuOnExpand() })
    }

    /**
     * Listener to update adapter when searchView text changes
     */
    private fun setSearchViewListener(searchView: SearchView?) {
        setOnQueryTextChangeListener(searchView) {
            query = it ?: ""
            statsAdapter?.filter(query)
            true
        }
    }

    /**
     * Displays a multi choice dialog according to the chip selected
     * @param statsList list of values depending of the stat chip
     * @param selectedValues list of already selected values
     * @param resourceId default string resource when no values are selected
     * @param resourceIdPlural string resource when more than 2 values are selected
     */
    private fun <T> Chip.setMultiChoiceItemsDialog(
        statsList: Array<T>,
        selectedValues: MutableSet<T>,
        resourceId: Int,
        @PluralsRes resourceIdPlural: Int,
    ) {
        val tempValues = selectedValues.toMutableSet()
        val items =
            statsList
                .map {
                    when (it) {
                        is Category -> it.name
                        is Source ->
                            it.nameBasedOnEnabledLanguages(
                                presenter.enabledLanguages,
                                presenter.extensionManager,
                            )
                        else -> it.toString()
                    }
                }.toTypedArray()
        searchView.clearFocus()
        activity!!
            .materialAlertDialog()
            .setTitle(resourceId)
            .setMultiChoiceItems(
                items,
                statsList.map { it in selectedValues }.toBooleanArray(),
            ) { _, which, checked ->
                val newSelection = statsList[which]
                if (checked) {
                    tempValues.add(newSelection)
                } else {
                    tempValues.remove(newSelection)
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedValues.clear()
                selectedValues.addAll(tempValues)
                setState(selectedValues, resourceId, resourceIdPlural)
                binding.progress.isVisible = true
                resetAndSetup(keepAdapter = true)
            }.show()
    }

    /**
     * Reset the layout and setup the chart to display
     * @param updateChipsVisibility whether to update the chips visibility
     */
    private fun resetAndSetup(
        updateChipsVisibility: Boolean = true,
        keepAdapter: Boolean = false,
    ) {
        resetLayout(updateChipsVisibility, keepAdapter)
        presenter.getStatisticData(keepAdapter)
    }

    /**
     * Reset the text of the chip selected and reset layout
     * @param resourceId string resource of the stat name
     */
    private fun Chip.reset(resourceId: Int) {
        resetAndSetup(keepAdapter = true)
        this.setColors(0)
        this.text = activity?.getString(resourceId)
    }

    /**
     * Reset the layout to the default state
     * @param updateChipsVisibility whether to update the chips visibility
     */
    private fun resetLayout(
        updateChipsVisibility: Boolean = false,
        keepAdapter: Boolean = false,
    ) {
        binding.progress.isVisible = true
        binding.statsRecyclerView.isInvisible = !keepAdapter
        binding.chartLinearLayout?.root?.isInvisible = !keepAdapter
        if (keepAdapter) {
            binding.statsRecyclerView.scrollToPosition(0)
        }

        if (updateChipsVisibility) updateChipsVisibility()
    }

    fun updateStats(
        binding: StatsDetailsChartBinding? = null,
        keepAdapter: Boolean = false,
    ) {
        val currentStats = presenter.currentStats
        with(binding ?: headerBinding) {
            val hasNoData = currentStats.isNullOrEmpty() || currentStats.all { it.count == 0 }
            if (hasNoData) {
                this@StatsDetailsController.binding.noChartData.show(R.drawable.ic_heart_off_24dp, R.string.no_data_for_filters)
                presenter.currentStats?.removeAll { it.count == 0 }
                handleNoChartLayout()
                this?.statsPieChart?.isVisible = false
                this?.statsBarChart?.isVisible = false
                this?.statsLineChart?.isVisible = false
                highlightedDay = null
                highlightedBar = null
                this@StatsDetailsController
                    .binding.chartLinearLayout
                    ?.root
                    ?.listener = this@StatsDetailsController
                this@StatsDetailsController
                    .binding.chartLinearLayout
                    ?.root
                    ?.setupChart(presenter)
                this@StatsDetailsController
                    .binding.chartLinearLayout
                    ?.root
                    ?.hideChart()
            } else {
                this@StatsDetailsController.binding.noChartData.hide()
                if (!keepAdapter) {
                    this@StatsDetailsController.binding.statsRecyclerView.adapter = null
                }
                handleLayout(keepAdapter)
            }
            this@StatsDetailsController.binding.statsRecyclerView.isVisible = true
            this@StatsDetailsController.binding.progress.isVisible = false
            if (this == null) return
            totalDurationStatsText.text = statsAdapter?.list?.sumOf { it.readDuration }?.getReadDuration()
            if (highlightedDay != null) updateHighlightedValue(statsBarChart) else statsDateText.text = presenter.getPeriodString()
        }
    }

    /**
     * Update the chips visibility according to the selected stat
     */
    private fun updateChipsVisibility() {
        with(binding) {
            statsClearButtonContainer.isVisible = hasActiveFilters()
            chipSeriesType.isVisible = presenter.selectedStat !in listOf(Stats.SERIES_TYPE, Stats.READ_DURATION)
            chipSource.isVisible =
                presenter.selectedStat !in listOf(Stats.LANGUAGE, Stats.SOURCE, Stats.READ_DURATION) &&
                presenter.selectedLanguage.isEmpty()
            chipStatus.isVisible = presenter.selectedStat !in listOf(Stats.STATUS, Stats.READ_DURATION)
            chipLanguage.isVisible = presenter.selectedStat !in listOf(Stats.LANGUAGE, Stats.READ_DURATION) &&
                (presenter.selectedStat == Stats.SOURCE || presenter.selectedSource.isEmpty())
            chipCategory.isVisible = presenter.selectedStat !in listOf(Stats.CATEGORY, Stats.READ_DURATION) &&
                presenter.categoriesStats.size > 1
            statsSortTextView?.isVisible = presenter.selectedStat !in
                listOf(
                    Stats.SCORE,
                    Stats.LENGTH,
                    Stats.START_YEAR,
                    Stats.READ_DURATION,
                )
        }
    }

    /**
     * Update the chip state according to the number of selected values
     */
    private fun <T> Chip.setState(
        selectedValues: MutableSet<T>,
        resourceId: Int,
        @PluralsRes
        resourceIdPlural: Int,
    ) {
        this.setColors(selectedValues.size)
        this.text =
            when (selectedValues.size) {
                0 -> activity?.getString(resourceId)
                1 -> {
                    when (val firstValue = selectedValues.first()) {
                        is Category -> firstValue.name
                        is Source ->
                            firstValue.nameBasedOnEnabledLanguages(
                                presenter.enabledLanguages,
                                presenter.extensionManager,
                            )
                        else -> firstValue.toString()
                    }
                }
                else -> activity?.resources?.getQuantityString(resourceIdPlural, selectedValues.size, selectedValues.size)
            }
    }

    /**
     * Reset all the filters selected
     */
    private fun resetFilters(init: Boolean = false) {
        with(binding) {
            if (init) {
                presenter.selectedStat = defaultStat
                chipStat.text = activity?.getString(defaultStat.resourceId)
                presenter.selectedStatsSort = defaultSort
                statsSortTextView?.text = activity?.getString(defaultSort.resourceId)
            }
            presenter.selectedSeriesType = mutableSetOf()
            chipSeriesType.text = activity?.getString(R.string.series_type)
            presenter.selectedSource = mutableSetOf()
            chipSource.text = activity?.getString(R.string.source)
            presenter.selectedStatus = mutableSetOf()
            chipStatus.text = activity?.getString(R.string.status)
            presenter.selectedLanguage = mutableSetOf()
            chipLanguage.text = activity?.getString(R.string.language)
            presenter.selectedCategory = mutableSetOf()
            chipCategory.text = activity?.getString(R.string.category)
        }
    }

    private fun hasActiveFilters() =
        with(presenter) {
            listOf(selectedSeriesType, selectedSource, selectedStatus, selectedLanguage, selectedCategory).any {
                it.isNotEmpty()
            }
        }

    fun Chip.setColors(sizeStat: Int) {
        val emptyTextColor = activity!!.getResourceColor(R.attr.colorOnBackground)
        val filteredBackColor = activity!!.getResourceColor(R.attr.colorSecondary)
        val emptyBackColor = activity!!.getResourceColor(R.attr.colorSurface)
        val alwaysShowIcon = this == binding.chipStat
        val neverSelect = alwaysShowIcon || sizeStat == 0
        setTextColor(if (neverSelect) emptyTextColor else emptyBackColor)
        chipBackgroundColor = ColorStateList.valueOf(if (neverSelect) emptyBackColor else filteredBackColor)
        closeIcon =
            if (neverSelect) {
                context.contextCompatDrawable(R.drawable.ic_arrow_drop_down_24dp)
            } else {
                context.contextCompatDrawable(R.drawable.ic_close_24dp)
            }
        closeIconTint = ColorStateList.valueOf(if (neverSelect) emptyTextColor else emptyBackColor)
        isChipIconVisible = alwaysShowIcon || sizeStat == 1
        chipIconTint = ColorStateList.valueOf(if (neverSelect) emptyTextColor else emptyBackColor)
    }

    /**
     * Handle which layout should be displayed according to the selected stat
     */
    private fun handleLayout(onlyUpdateDetails: Boolean) {
        binding.chartLinearLayout?.root?.updateLayoutParams<ConstraintLayout.LayoutParams> {
            matchConstraintPercentWidth =
                when (presenter.selectedStat) {
                    Stats.SERIES_TYPE, Stats.STATUS, Stats.LANGUAGE, Stats.TRACKER, Stats.CATEGORY -> 0.33f
                    else -> 0.5f
                }
        }
        assignAdapter(onlyUpdateDetails)
        binding.chartLinearLayout?.root?.listener = this
        binding.chartLinearLayout?.root?.setupChart(presenter)
    }

    private fun handleNoChartLayout() {
        assignAdapter(false)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun assignAdapter(onlyUpdateDetails: Boolean) {
        if (concatAdapter == null) {
            val statsAdapter =
                StatsDetailsAdapter(
                    activity!!,
                    presenter.selectedStat!!,
                    presenter.currentStats ?: ArrayList(),
                )
            statsAdapter.setHasStableIds(true)
            val headAdapter = HeaderStatsDetailsAdapter(this, presenter)
            headAdapter.setHasStableIds(true)
            val concatConfig =
                ConcatAdapter.Config
                    .Builder()
                    .setIsolateViewTypes(false)
                    .setStableIdMode(ConcatAdapter.Config.StableIdMode.SHARED_STABLE_IDS)
                    .build()
            val concatAdapter = ConcatAdapter(concatConfig, statsAdapter)
            if (!binding.root.context.isLandscape()) {
                concatAdapter.addAdapter(0, headAdapter)
            }
            binding.statsRecyclerView.adapter = concatAdapter
            statsAdapter.listener = StatsDetailsAdapter.OnItemClickedListener(::onItemClicked)
        } else {
            updateStatsAdapter(onlyUpdateDetails)
            concatAdapter?.notifyDataSetChanged()
        }
        if (query.isNotBlank()) statsAdapter?.filter(query)
    }

    fun onItemClicked(
        id: Long?,
        name: String?,
    ) {
        name ?: return
        val statuses = presenter.selectedStatus.map { presenter.statusStats.indexOf(it) + 1 }.toTypedArray()
        val seriesTypes = presenter.selectedSeriesType.map { presenter.seriesTypeStats.indexOf(it) + 1 }.toTypedArray()
        val languages =
            presenter.selectedLanguage
                .mapNotNull { lang ->
                    presenter.languagesStats.firstNotNullOfOrNull { if (it.value == lang) it.key else null }
                }.toTypedArray()
        val categories = presenter.selectedCategory.mapNotNull { it.id }.toTypedArray()
        val sources = presenter.selectedSource.map { it.id }.toTypedArray()
        when (val selectedStat = presenter.selectedStat) {
            Stats.SOURCE, Stats.TAG, Stats.STATUS, Stats.SERIES_TYPE, Stats.SCORE, Stats.START_YEAR, Stats.LANGUAGE, Stats.CATEGORY -> {
                router.pushController(
                    FilteredLibraryController(
                        if (selectedStat == Stats.SCORE) {
                            name + if (name.toIntOrNull() != null) "★" else ""
                        } else {
                            name
                        },
                        filterMangaType =
                            when (selectedStat) {
                                Stats.SERIES_TYPE -> arrayOf(presenter.seriesTypeStats.indexOf(name) + 1)
                                else -> seriesTypes
                            },
                        filterStatus =
                            when (selectedStat) {
                                Stats.STATUS -> arrayOf(presenter.statusStats.indexOf(name) + 1)
                                else -> statuses
                            },
                        filterSources =
                            when (selectedStat) {
                                Stats.SOURCE -> arrayOf(id!!)
                                Stats.LANGUAGE -> emptyArray()
                                else -> sources
                            },
                        filterLanguages =
                            when (selectedStat) {
                                Stats.LANGUAGE -> {
                                    val language =
                                        presenter.languagesStats.firstNotNullOfOrNull {
                                            if (it.value == name) it.key else null
                                        }
                                    listOfNotNull(language).toTypedArray()
                                }
                                else -> languages
                            },
                        filterCategories =
                            when (selectedStat) {
                                Stats.CATEGORY -> arrayOf(id!!.toInt())
                                else -> categories
                            },
                        filterTags = if (selectedStat == Stats.TAG) arrayOf(name) else emptyArray(),
                        filterTrackingScore = if (selectedStat == Stats.SCORE) id?.toInt() ?: -1 else 0,
                        filterStartYear = if (selectedStat == Stats.START_YEAR) id?.toInt() ?: -1 else 0,
                    ).withFadeTransaction(),
                )
            }
            Stats.TRACKER -> {
                val serviceName: String? =
                    id?.let {
                        val loggedServices = presenter.trackManager.services.filter { it.isLogged }
                        val service = loggedServices.find { it.id == id.toInt() } ?: return
                        return@let binding.root.context.getString(service.nameRes())
                    }
                router.pushController(
                    FilteredLibraryController(
                        serviceName ?: name,
                        filterMangaType = seriesTypes,
                        filterStatus = statuses,
                        filterSources = sources,
                        filterLanguages = languages,
                        filterCategories = categories,
                        filterTracked =
                            if (serviceName == null) {
                                FilterBottomSheet.STATE_EXCLUDE
                            } else {
                                FilterBottomSheet.STATE_INCLUDE
                            },
                        filterTrackerName = serviceName,
                    ).withFadeTransaction(),
                )
            }
            Stats.LENGTH -> {
                val range: IntRange =
                    if (name.contains("-")) {
                        val values = name.split("-").map { it.toInt() }
                        IntRange(values.min(), values.max())
                    } else if (name.contains("+")) {
                        val values = name.split("+").mapNotNull { it.toIntOrNull() }
                        IntRange(values[0], Int.MAX_VALUE)
                    } else {
                        IntRange(name.toInt(), name.toInt())
                    }
                router.pushController(
                    FilteredLibraryController(
                        binding.root.resources.getQuantityString(R.plurals.chapters_plural, range.last, name),
                        filterMangaType = seriesTypes,
                        filterStatus = statuses,
                        filterSources = sources,
                        filterLanguages = languages,
                        filterCategories = categories,
                        filterLength = range,
                    ).withFadeTransaction(),
                )
            }
            Stats.READ_DURATION -> {
                id?.let {
                    router.pushController(MangaDetailsController(id).withFadeTransaction())
                }
            }
            else -> {
                return
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateStatsAdapter(onlyUpdateDetails: Boolean) {
        val oldCount = statsAdapter?.list?.size ?: 0
        statsAdapter?.stat = presenter.selectedStat!!
        statsAdapter?.mainList = presenter.currentStats ?: ArrayList()
        if (onlyUpdateDetails) {
            val newCount = statsAdapter?.list?.size ?: 0
            if (oldCount > newCount) {
                statsAdapter?.notifyItemRangeRemoved(newCount, oldCount - newCount)
            } else if (oldCount < newCount) {
                statsAdapter?.notifyItemRangeInserted(oldCount, newCount - oldCount)
            }
            statsAdapter?.notifyItemRangeChanged(0, newCount)
        } else {
            statsAdapter?.notifyDataSetChanged()
        }
    }

    override fun onBarValueChanged(
        highlight: Highlight?,
        e: Entry?,
    ) {
        highlightedBar = highlight?.let { Triple(it.x, it.y, it.dataSetIndex) }
        highlightedDay = e?.let { presenter.historyByDayAndManga.keys.toTypedArray()[e.x.toInt()] }
        presenter.setupReadDuration(highlightedDay)
        updateStatsAdapter(true)
    }

    override fun getHighlight(): Highlight? = highlightedBar?.let { Highlight(it.first, it.second, it.third) }

    override fun getReadDates(): String =
        (
            highlightedDay?.let { presenter.convertCalendarToLongString(it) }
                ?: presenter.getPeriodString()
        )

    override fun getReadDuration(): String = statsAdapter?.list?.sumOf { it.readDuration }?.getReadDuration() ?: ""

    override fun onSortClicked(binding: StatsDetailsChartBinding?) {
        searchView.clearFocus()
        activity!!
            .materialAlertDialog()
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(
                presenter.getSortDataArray(),
                StatsSort.entries.indexOf(presenter.selectedStatsSort),
            ) { dialog, which ->
                val newSelection = StatsSort.entries[which]
                if (newSelection == presenter.selectedStatsSort) return@setSingleChoiceItems
                statsSortTextView?.text = activity?.getString(newSelection.resourceId)
                presenter.selectedStatsSort = newSelection

                dialog.dismiss()
                presenter.sortCurrentStats()
                resetLayout()
                updateStats(binding, true)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDateTextClicked(
        statsDateText: TextView,
        barChart: BarChart,
    ) {
        val dialog =
            MaterialDatePicker.Builder
                .dateRangePicker()
                .setTitleText(R.string.read_duration)
                .setSelection(
                    Pair(
                        presenter.startDate.timeInMillis
                            .toUtcCalendar()
                            ?.timeInMillis,
                        presenter.endDate.timeInMillis
                            .toUtcCalendar()
                            ?.timeInMillis,
                    ),
                ).build()

        dialog.addOnPositiveButtonClickListener { utcMillis ->
            binding.progress.isVisible = true
            viewScope.launch {
                presenter.updateReadDurationPeriod(
                    utcMillis.first.toLocalCalendar()?.timeInMillis ?: utcMillis.first,
                    utcMillis.second.toLocalCalendar()?.timeInMillis
                        ?: utcMillis.second,
                )
                withIOContext { presenter.updateMangaHistory() }
                statsDateText.text = presenter.getPeriodString()
                barChart.highlightValues(null)
                highlightedDay = null
                presenter.getStatisticData()
            }
        }
        dialog.show((activity as AppCompatActivity).supportFragmentManager, activity?.getString(R.string.read_duration))
    }

    /**
     * Changes dates of the read duration stat with the arrows
     * @param referenceDate date used to determine if should change week
     * @param toAdd whether to add or remove
     */
    override fun changeDatesReadDurationWithArrow(
        referenceDate: Calendar,
        toAdd: Int,
        barChart: BarChart,
    ) {
        jobReadDuration?.cancel()
        if (highlightedDay == null) {
            changeReadDurationPeriod(toAdd)
        } else {
            val daySelected = highlightedDay?.get(Calendar.DAY_OF_YEAR)
            val endDay = referenceDate.get(Calendar.DAY_OF_YEAR)
            barChart.highlightValues(null)
            highlightedDay =
                Calendar.getInstance().apply {
                    timeInMillis = highlightedDay!!.timeInMillis
                    add(Calendar.DAY_OF_YEAR, toAdd)
                }
            if (daySelected == endDay) {
                changeReadDurationPeriod(toAdd)
            } else {
                updateHighlightedValue(barChart)
            }
        }
    }
}
