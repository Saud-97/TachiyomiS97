package eu.kanade.tachiyomi.source.model

import eu.kanade.tachiyomi.data.database.models.MangaImpl
import java.io.Serializable

interface SManga : Serializable {
    var url: String

    var title: String

    var artist: String?

    var author: String?

    var description: String?

    var genre: String?

    var status: Int

    var thumbnail_url: String?

    var update_strategy: UpdateStrategy

    var initialized: Boolean

    val originalTitle: String
        get() = (this as? MangaImpl)?.ogTitle ?: title
    val originalAuthor: String?
        get() = (this as? MangaImpl)?.ogAuthor ?: author
    val originalArtist: String?
        get() = (this as? MangaImpl)?.ogArtist ?: artist
    val originalDescription: String?
        get() = (this as? MangaImpl)?.ogDesc ?: description
    val originalGenre: String?
        get() = (this as? MangaImpl)?.ogGenre ?: genre
    val originalStatus: Int
        get() = (this as? MangaImpl)?.ogStatus ?: status

    val hasSameAuthorAndArtist: Boolean
        get() =
            author == artist ||
                artist.isNullOrBlank() ||
                author?.contains(artist ?: "", true) == true

    fun copyFrom(other: SManga) {
        if (other.author != null) {
            author = other.originalAuthor
        }

        if (other.artist != null) {
            artist = other.originalArtist
        }

        if (other.description != null) {
            description = other.originalDescription
        }

        if (other.genre != null) {
            genre = other.originalGenre
        }

        if (other.thumbnail_url != null) {
            thumbnail_url = other.thumbnail_url
        }

        status = other.originalStatus

        update_strategy = other.update_strategy

        if (!initialized) {
            initialized = other.initialized
        }
    }

    fun copy() =
        create().also {
            it.url = url
            it.title = title
            it.artist = artist
            it.author = author
            it.description = description
            it.genre = genre
            it.status = status
            it.thumbnail_url = thumbnail_url
            it.initialized = initialized
        }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SManga = MangaImpl()
    }
}
