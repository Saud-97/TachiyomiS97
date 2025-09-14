package eu.kanade.tachiyomi.ui.migration

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.doOnNextLayout
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MigrationControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationController :
    BaseCoroutineController<MigrationControllerBinding, MigrationPresenter>(),
    FlexibleAdapter.OnItemClickListener,
    SourceAdapter.OnAllClickListener,
    BaseMigrationInterface {
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    private var title: String? = null
        set(value) {
            field = value
            setTitle()
        }

    override val presenter = MigrationPresenter()

    override fun createBinding(inflater: LayoutInflater) = MigrationControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        scrollViewWith(binding.migrationRecycler, padBottom = true)

        adapter = FlexibleAdapter(null, this)
        binding.migrationRecycler.layoutManager = LinearLayoutManagerAccurateOffset(view.context)
        binding.migrationRecycler.adapter = adapter
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun getTitle(): String? = title

    override fun canStillGoBack(): Boolean = adapter is MangaAdapter

    override fun handleBack(): Boolean =
        if (adapter is MangaAdapter) {
            presenter.deselectSource()
            true
        } else {
            super.handleBack()
        }

    override fun onItemClick(
        view: View?,
        position: Int,
    ): Boolean {
        val item = adapter?.getItem(position) ?: return false

        if (item is MangaItem) {
            PreMigrationController.navigateToMigration(
                Injekt.get<PreferencesHelper>().skipPreMigration().get(),
                router,
                listOf(item.manga.id!!),
            )
        } else if (item is SourceItem) {
            presenter.setSelectedSource(item.source)
        }
        return false
    }

    override fun onAllClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return

        launchUI {
            val manga = Injekt.get<DatabaseHelper>().getFavoriteMangas().executeAsBlocking()
            val sourceMangas =
                manga
                    .asSequence()
                    .filter { it.source == item.source.id }
                    .map { it.id!! }
                    .toList()
            withContext(Dispatchers.Main) {
                PreMigrationController.navigateToMigration(
                    Injekt.get<PreferencesHelper>().skipPreMigration().get(),
                    router,
                    sourceMangas,
                )
            }
        }
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.migration_main, menu)
        menu.findItem(R.id.action_sources_settings).isVisible = false
        val id =
            when (PreferenceValues.MigrationSourceOrder.fromPreference(presenter.preferences)) {
                PreferenceValues.MigrationSourceOrder.Alphabetically -> R.id.action_sort_alpha
                PreferenceValues.MigrationSourceOrder.MostEntries -> R.id.action_sort_largest
                PreferenceValues.MigrationSourceOrder.Obsolete -> R.id.action_sort_obsolete
            }
        menu.findItem(id).isChecked = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val sorting =
            when (item.itemId) {
                R.id.action_sort_alpha -> PreferenceValues.MigrationSourceOrder.Alphabetically
                R.id.action_sort_largest -> PreferenceValues.MigrationSourceOrder.MostEntries
                R.id.action_sort_obsolete -> PreferenceValues.MigrationSourceOrder.Obsolete
                else -> null
            }
        if (sorting != null) {
            presenter.preferences.migrationSourceOrder().set(sorting.value)
            presenter.refreshMigrations()
            item.isChecked = true
        }
        when (item.itemId) {
            R.id.action_migration_guide -> {
                activity?.openInBrowser(BrowseController.HELP_URL)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun setMigrationManga(
        title: String,
        manga: List<MangaItem>?,
    ) {
        this.title = title
        if (adapter !is MangaAdapter) {
            adapter = MangaAdapter(this, presenter.preferences.outlineOnCovers().get())
            binding.migrationRecycler.adapter = adapter
        }
        adapter?.updateDataSet(manga, true)
        activityBinding?.appBar?.doOnNextLayout {
            binding.migrationRecycler.requestApplyInsets()
        }
    }

    override fun setMigrationSources(sources: List<SourceItem>) {
        title = resources?.getString(R.string.source_migration)
        if (adapter !is SourceAdapter) {
            adapter = SourceAdapter(this)
            binding.migrationRecycler.adapter = adapter
        }
        adapter?.updateDataSet(sources)
    }
}
