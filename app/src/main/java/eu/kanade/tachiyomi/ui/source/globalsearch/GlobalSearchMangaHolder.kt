package eu.kanade.tachiyomi.ui.source.globalsearch

import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.core.view.isVisible
import coil.Coil
import coil.dispose
import coil.request.CachePolicy
import coil.request.ImageRequest
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.CoverViewTarget
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerCardItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.makeShapeCorners
import eu.kanade.tachiyomi.util.view.setCards
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat

class GlobalSearchMangaHolder(view: View, adapter: GlobalSearchCardAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    private val binding = SourceGlobalSearchControllerCardItemBinding.bind(view)
    init {
        itemView.setOnClickListener {
            val item = adapter.getItem(flexibleAdapterPosition)
            if (item != null) {
                adapter.mangaClickListener.onMangaClick(item.manga)
            }
        }
        val bottom = 2.dpToPx
        val others = 5.dpToPx
        (binding.constraintLayout.foreground as? RippleDrawable)?.apply {
            setLayerSize(1, 0, 0)
            for (i in 0 until numberOfLayers) {
                setLayerInset(i, others, others, others, bottom)
            }
        }
        binding.favoriteButton.shapeAppearanceModel =
            binding.card.makeShapeCorners(binding.card.radius, binding.card.radius)
        itemView.setOnLongClickListener {
            adapter.mangaClickListener.onMangaLongClick(flexibleAdapterPosition, adapter)
            true
        }
        setCards(adapter.showOutlines, binding.card, binding.favoriteButton)
    }

    fun bind(manga: Manga) {
        binding.title.text = manga.title
        binding.favoriteButton.isVisible = manga.favorite
        setImage(manga)
        setChaptersCount(manga)
    }

    fun setImage(manga: Manga) {
        binding.itemImage.dispose()
        if (!manga.thumbnail_url.isNullOrEmpty()) {
            val request = ImageRequest.Builder(itemView.context).data(manga)
                .placeholder(android.R.color.transparent)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .target(CoverViewTarget(binding.itemImage, binding.progress))
                .setParameter(MangaCoverFetcher.useCustomCover, false)
                .build()
            Coil.imageLoader(itemView.context).enqueue(request)
        }
    }

    fun setChaptersCount(manga: Manga) {
        if (!preferences.fetchMangaChapters().get()) {
            return
        }
        val mangaChapters = db.getChapters(manga).executeAsBlocking()
        if (mangaChapters.isEmpty()) {
            return
        }

        if (!manga.favorite) {
            binding.unreadDownloadBadge.badgeView.setChapters(mangaChapters.size)
        }

        val latestChapter = mangaChapters.maxOfOrNull { it.chapter_number } ?: -1f
        if (latestChapter >= 0f) {
            binding.subtitle.text = binding.root.context.getString(
                R.string.latest_,
                DecimalFormat("#.#").format(latestChapter),
            )
        } else {
            binding.subtitle.text = binding.root.context.getString(
                R.string.latest_,
                binding.root.context.getString(R.string.unknown),
            )
        }
    }

    private companion object {
        private val db: DatabaseHelper by injectLazy()
        private val preferences: PreferencesHelper by injectLazy()
    }
}
