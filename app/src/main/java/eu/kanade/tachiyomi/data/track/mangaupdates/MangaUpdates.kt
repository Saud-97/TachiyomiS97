package eu.kanade.tachiyomi.data.track.mangaupdates

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.ListItem
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.Rating
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.copyTo
import eu.kanade.tachiyomi.data.track.mangaupdates.dto.toTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo

class MangaUpdates(
    private val context: Context,
    id: Int,
) : TrackService(id) {
    companion object {
        const val READING_LIST = 0
        const val WISH_LIST = 1
        const val COMPLETE_LIST = 2
        const val UNFINISHED_LIST = 3
        const val ON_HOLD_LIST = 4

        const val DEFAULT_STATUS = READING_LIST
        const val DEFAULT_SCORE = 0
    }

    private val interceptor by lazy { MangaUpdatesInterceptor(this) }

    private val api by lazy { MangaUpdatesApi(interceptor, client) }

    @StringRes
    override fun nameRes(): Int = R.string.manga_updates

    override fun getLogo(): Int = R.drawable.ic_tracker_manga_updates

    override fun getTrackerColor() = Color.rgb(251, 148, 46)

    override fun getLogoColor() = Color.argb(0, 146, 160, 173)

    override fun getStatusList(): List<Int> = listOf(READING_LIST, COMPLETE_LIST, ON_HOLD_LIST, UNFINISHED_LIST, WISH_LIST)

    override fun isCompletedStatus(index: Int) = getStatusList()[index] == COMPLETE_LIST

    override fun completedStatus() = COMPLETE_LIST

    override fun readingStatus() = READING_LIST

    override fun planningStatus() = WISH_LIST

    override fun getStatus(status: Int): String =
        with(context) {
            when (status) {
                READING_LIST -> getString(R.string.reading_list)
                WISH_LIST -> getString(R.string.wish_list)
                COMPLETE_LIST -> getString(R.string.complete_list)
                ON_HOLD_LIST -> getString(R.string.on_hold_list)
                UNFINISHED_LIST -> getString(R.string.unfinished_list)
                else -> ""
            }
        }

    override fun getGlobalStatus(status: Int) =
        with(context) {
            when (status) {
                READING_LIST -> getString(R.string.reading)
                COMPLETE_LIST -> getString(R.string.completed)
                ON_HOLD_LIST -> getString(R.string.on_hold)
                UNFINISHED_LIST -> getString(R.string.dropped)
                WISH_LIST -> getString(R.string.plan_to_read)
                else -> ""
            }
        }

    private val _scoreList =
        (0..10)
            .flatMap { decimal ->
                when (decimal) {
                    0 -> listOf("-")
                    10 -> listOf("10.0")
                    else ->
                        (0..9).map { fraction ->
                            "$decimal.$fraction"
                        }
                }
            }.toList()

    override fun getScoreList(): List<String> = _scoreList

    override fun indexToScore(index: Int): Float = if (index == 0) 0f else _scoreList[index].toFloat()

    override fun displayScore(track: Track): String = track.score.toString()

    override suspend fun add(track: Track): Track {
        track.score = DEFAULT_SCORE.toFloat()
        track.status = DEFAULT_STATUS
        updateNewTrackInfo(track)
        api.addSeriesToList(track)
        return track
    }

    override suspend fun update(
        track: Track,
        setToRead: Boolean,
    ): Track {
        updateTrackStatus(track, setToRead, setToComplete = true, mustReadToComplete = true)
        api.updateSeriesListItem(track)
        return track
    }

    override suspend fun bind(track: Track): Track =
        try {
            val (series, rating) = api.getSeriesListItem(track)
            track.copyFrom(series, rating)
            update(track)
        } catch (e: Exception) {
            track.score = 0f
            add(track)
        }

    override suspend fun search(query: String): List<TrackSearch> =
        api
            .search(query)
            .map {
                it.toTrackSearch(id)
            }

    override suspend fun refresh(track: Track): Track {
        val (series, rating) = api.getSeriesListItem(track)
        series.copyTo(track)
        return rating?.copyTo(track) ?: track
    }

    private fun Track.copyFrom(
        item: ListItem,
        rating: Rating?,
    ): Track =
        apply {
            item.copyTo(this)
            score = rating?.rating ?: 0f
        }

    override fun canRemoveFromService(): Boolean = true

    override suspend fun removeFromService(track: Track): Boolean = api.removeSeriesFromList(track)

    override suspend fun login(
        username: String,
        password: String,
    ): Boolean {
        val authenticated = api.authenticate(username, password) ?: throw Throwable("Unable to login")
        saveCredentials(authenticated.uid.toString(), authenticated.sessionToken)
        interceptor.newAuth(authenticated.sessionToken)
        return true
    }

    fun restoreSession(): String? = getPassword().ifBlank { null }
}
