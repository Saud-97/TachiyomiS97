package eu.kanade.tachiyomi.data.track.kitsu

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat

class Kitsu(
    private val context: Context,
    id: Int,
) : TrackService(id) {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5

        const val DEFAULT_STATUS = READING
        const val DEFAULT_SCORE = 0f
    }

    @StringRes
    override fun nameRes() = R.string.kitsu

    override val supportsReadingDates: Boolean = true

    private val json: Json by injectLazy()

    private val interceptor by lazy { KitsuInterceptor(this) }

    private val api by lazy { KitsuApi(client, interceptor) }

    override fun getLogo() = R.drawable.ic_tracker_kitsu

    override fun getTrackerColor() = Color.rgb(253, 117, 92)

    override fun getLogoColor() = Color.rgb(51, 37, 50)

    override fun getStatusList(): List<Int> = listOf(READING, PLAN_TO_READ, COMPLETED, ON_HOLD, DROPPED)

    override fun isCompletedStatus(index: Int) = getStatusList()[index] == COMPLETED

    override fun completedStatus(): Int = COMPLETED

    override fun readingStatus() = READING

    override fun planningStatus() = PLAN_TO_READ

    override fun getStatus(status: Int): String =
        with(context) {
            when (status) {
                READING -> getString(R.string.currently_reading)
                PLAN_TO_READ -> getString(R.string.want_to_read)
                COMPLETED -> getString(R.string.completed)
                ON_HOLD -> getString(R.string.on_hold)
                DROPPED -> getString(R.string.dropped)
                else -> ""
            }
        }

    override fun getGlobalStatus(status: Int): String =
        with(context) {
            when (status) {
                READING -> getString(R.string.reading)
                PLAN_TO_READ -> getString(R.string.plan_to_read)
                COMPLETED -> getString(R.string.completed)
                ON_HOLD -> getString(R.string.on_hold)
                DROPPED -> getString(R.string.dropped)
                else -> ""
            }
        }

    override fun getScoreList(): List<String> {
        val df = DecimalFormat("0.#")
        return listOf("0") + IntRange(2, 20).map { df.format(it / 2f) }
    }

    override fun indexToScore(index: Int): Float = if (index > 0) (index + 1) / 2f else 0f

    override fun displayScore(track: Track): String {
        val df = DecimalFormat("0.#")
        return df.format(track.score)
    }

    override suspend fun update(
        track: Track,
        setToRead: Boolean,
    ): Track {
        updateTrackStatus(track, setToRead, setToComplete = true, mustReadToComplete = false)
        return api.updateLibManga(track)
    }

    override suspend fun add(track: Track): Track {
        track.score = DEFAULT_SCORE
        track.status = DEFAULT_STATUS
        updateNewTrackInfo(track)
        return api.addLibManga(track, getUserId())
    }

    override suspend fun bind(track: Track): Track {
        val remoteTrack = api.findLibManga(track, getUserId())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.media_id = remoteTrack.media_id
            update(track)
        } else {
            add(track)
        }
    }

    override fun canRemoveFromService() = true

    override suspend fun removeFromService(track: Track): Boolean = api.remove(track)

    override suspend fun search(query: String): List<TrackSearch> = api.search(query)

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(
        username: String,
        password: String,
    ): Boolean =
        try {
            val oauth = api.login(username, password)
            interceptor.newAuth(oauth)
            val userId = api.getCurrentUser()
            saveCredentials(username, userId)
            true
        } catch (e: Exception) {
            Timber.e(e)
            false
        }

    override fun logout() {
        super.logout()
        interceptor.newAuth(null)
    }

    private fun getUserId(): String = getPassword()

    fun saveToken(oauth: OAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): OAuth? =
        try {
            json.decodeFromString<OAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
}
