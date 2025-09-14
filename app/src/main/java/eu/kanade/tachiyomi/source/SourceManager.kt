package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.DelegatedHttpSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.Cubari
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.english.KireiCake
import eu.kanade.tachiyomi.source.online.english.MangaPlus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

class SourceManager(
    private val context: Context,
    private val extensionManager: ExtensionManager,
) {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, Source>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubSource>()

    val catalogueSources: Flow<List<CatalogueSource>> = sourcesMapFlow.map { it.values.filterIsInstance<CatalogueSource>() }
    val onlineSources: Flow<List<HttpSource>> = catalogueSources.map { it.filterIsInstance<HttpSource>() }

    private val delegatedSources =
        listOf(
            DelegatedSource(
                "reader.kireicake.com",
                5509224355268673176,
                KireiCake(),
            ),
            DelegatedSource(
                "mangadex.org",
                2499283573021220255,
                MangaDex(),
            ),
            DelegatedSource(
                "mangaplus.shueisha.co.jp",
                1998944621602463790,
                MangaPlus(),
            ),
            DelegatedSource(
                "cubari.moe",
                6338219619148105941,
                Cubari(),
            ),
        ).associateBy { it.sourceId }

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = ConcurrentHashMap<Long, Source>(mapOf(LocalSource.ID to LocalSource(context)))
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            delegatedSources[it.id]?.delegatedHttpSource?.delegate = it as? HttpSource
//                            registerStubSource(it)
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                }
        }

//        scope.launch {
//            sourceRepository.subscribeAll()
//                .collectLatest { sources ->
//                    val mutableMap = stubSourcesMap.toMutableMap()
//                    sources.forEach {
//                        mutableMap[it.id] = StubSource(it)
//                    }
//                }
//        }
    }

    fun get(sourceKey: Long): Source? = sourcesMapFlow.value[sourceKey]

    fun getOrStub(sourceKey: Long): Source =
        sourcesMapFlow.value[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { StubSource(sourceKey) }
        }

    fun isDelegatedSource(source: Source): Boolean = delegatedSources.values.count { it.sourceId == source.id } > 0

    fun getDelegatedSource(urlName: String): DelegatedHttpSource? =
        delegatedSources.values.find { it.urlName == urlName }?.delegatedHttpSource

    fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<CatalogueSource>()

    @Suppress("OverridingDeprecatedMember")
    inner class StubSource(
        override val id: Long,
    ) : Source {
        override val name: String
            get() = extensionManager.getStubSource(id)?.name ?: id.toString()

        override suspend fun getMangaDetails(manga: SManga): SManga = throw getSourceNotInstalledException()

        override suspend fun getChapterList(manga: SManga): List<SChapter> = throw getSourceNotInstalledException()

        override suspend fun getPageList(chapter: SChapter): List<Page> = throw getSourceNotInstalledException()

        override fun toString(): String = name

        private fun getSourceNotInstalledException(): Exception =
            SourceNotFoundException(
                context.getString(
                    R.string.source_not_installed_,
                    extensionManager.getStubSource(id)?.name ?: id.toString(),
                ),
                id,
            )

        override fun hashCode(): Int = id.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as StubSource
            return id == other.id
        }
    }

    private data class DelegatedSource(
        val urlName: String,
        val sourceId: Long,
        val delegatedHttpSource: DelegatedHttpSource,
    )
}

class SourceNotFoundException(
    message: String,
    val id: Long,
) : Exception(message)
