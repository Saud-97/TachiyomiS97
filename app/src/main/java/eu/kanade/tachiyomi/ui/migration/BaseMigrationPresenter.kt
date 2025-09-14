package eu.kanade.tachiyomi.ui.migration

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.system.executeOnIO
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

abstract class BaseMigrationPresenter<T : BaseMigrationInterface>(
    protected val sourceManager: SourceManager = Injekt.get(),
    protected val db: DatabaseHelper = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
) : BaseCoroutinePresenter<T>() {
    private var selectedSource: Pair<String, Long>? = null
    var sourceItems = emptyList<SourceItem>()
        protected set

    var mangaItems = hashMapOf<Long, List<MangaItem>>()
        protected set
    protected val extensionManager: ExtensionManager by injectLazy()

    fun refreshMigrations() {
        presenterScope.launch {
            val favs = db.getFavoriteMangas().executeOnIO()
            sourceItems = findSourcesWithManga(favs)
            mangaItems =
                HashMap(
                    sourceItems.associate {
                        it.source.id to libraryToMigrationItem(favs, it.source.id)
                    },
                )
            withContext(Dispatchers.Main) {
                if (selectedSource != null) {
                    view?.setMigrationManga(selectedSource!!.first, mangaItems[selectedSource!!.second])
                } else {
                    view?.setMigrationSources(sourceItems)
                }
            }
        }
    }

    private fun findSourcesWithManga(library: List<Manga>): List<SourceItem> {
        val header = SelectionHeader()
        val sourceGroup = library.groupBy { it.source }
        val sortOrder = PreferenceValues.MigrationSourceOrder.fromPreference(preferences)
        val extensions = extensionManager.installedExtensionsFlow.value
        val obsoleteSources =
            extensions
                .filter { it.isObsolete }
                .map { it.sources }
                .flatten()
                .map { it.id }

        return sourceGroup
            .mapNotNull { if (it.key != LocalSource.ID) sourceManager.getOrStub(it.key) to it.value.size else null }
            .sortedWith(
                compareBy(
                    {
                        when (sortOrder) {
                            PreferenceValues.MigrationSourceOrder.Alphabetically -> it.first.name
                            PreferenceValues.MigrationSourceOrder.MostEntries -> Long.MAX_VALUE - it.second
                            PreferenceValues.MigrationSourceOrder.Obsolete ->
                                it.first !is SourceManager.StubSource &&
                                    it.first.id !in obsoleteSources
                        }
                    },
                    { it.first.name },
                ),
            ).map {
                SourceItem(
                    it.first,
                    header,
                    it.second,
                    it.first is SourceManager.StubSource,
                    it.first.id in obsoleteSources,
                )
            }
    }

    private fun libraryToMigrationItem(
        library: List<Manga>,
        sourceId: Long,
    ): List<MangaItem> =
        library
            .filter {
                it.source == sourceId
            }.map(::MangaItem)

    protected suspend fun firstTimeMigration() {
        val favs = db.getFavoriteMangas().executeOnIO()
        sourceItems = findSourcesWithManga(favs)
        mangaItems =
            HashMap(
                sourceItems.associate {
                    it.source.id to
                        libraryToMigrationItem(
                            favs,
                            it.source.id,
                        )
                },
            )
        withContext(Dispatchers.Main) {
            if (selectedSource != null) {
                view?.setMigrationManga(selectedSource!!.first, mangaItems[selectedSource!!.second])
            } else {
                view?.setMigrationSources(sourceItems)
            }
        }
    }

    fun setSelectedSource(source: Source) {
        selectedSource = source.name to source.id
        presenterScope.launch {
            withUIContext { view?.setMigrationManga(source.name, mangaItems[source.id]) }
        }
    }

    fun deselectSource() {
        selectedSource = null
        presenterScope.launch {
            withUIContext { view?.setMigrationSources(sourceItems) }
        }
    }
}

interface BaseMigrationInterface {
    fun setMigrationManga(
        title: String,
        manga: List<MangaItem>?,
    )

    fun setMigrationSources(sources: List<SourceItem>)
}
