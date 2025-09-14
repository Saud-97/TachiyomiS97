package eu.kanade.tachiyomi.data.track.shikimori

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

class Shikimori(
    private val context: Context,
    id: Int,
) : TrackService(id) {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5
        const val REREADING = 6

        const val DEFAULT_STATUS = READING
        const val DEFAULT_SCORE = 0
    }

    @StringRes
    override fun nameRes() = R.string.shikimori

    private val json: Json by injectLazy()

    private val interceptor by lazy { ShikimoriInterceptor(this) }

    private val api by lazy { ShikimoriApi(client, interceptor) }

    override fun getLogo() = R.drawable.ic_tracker_shikimori

    override fun getTrackerColor() = Color.rgb(218, 241, 255)

    override fun getLogoColor() = Color.rgb(40, 40, 40)

    override fun getStatusList(): List<Int> = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)

    override fun isCompletedStatus(index: Int) = getStatusList()[index] == COMPLETED

    override fun completedStatus(): Int = COMPLETED

    override fun readingStatus() = READING

    override fun planningStatus() = PLAN_TO_READ

    override fun getStatus(status: Int): String =
        with(context) {
            when (status) {
                READING -> getString(R.string.reading)
                COMPLETED -> getString(R.string.completed)
                ON_HOLD -> getString(R.string.on_hold)
                DROPPED -> getString(R.string.dropped)
                PLAN_TO_READ -> getString(R.string.plan_to_read)
                REREADING -> getString(R.string.rereading)
                else -> ""
            }
        }

    override fun getGlobalStatus(status: Int): String = getStatus(status)

    override fun getScoreList(): List<String> = IntRange(0, 10).map(Int::toString)

    override fun displayScore(track: Track): String = track.score.toInt().toString()

    override suspend fun update(
        track: Track,
        setToRead: Boolean,
    ): Track {
        updateTrackStatus(track, setToRead, setToComplete = true, mustReadToComplete = false)
        return api.updateLibManga(track, getUsername())
    }

    override suspend fun add(track: Track): Track {
        track.score = DEFAULT_SCORE.toFloat()
        track.status = DEFAULT_STATUS
        updateNewTrackInfo(track)
        return api.addLibManga(track, getUsername())
    }

    override suspend fun bind(track: Track): Track {
        val remoteTrack = api.findLibManga(track, getUsername())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id
            update(track)
        } else {
            add(track)
        }
    }

    override fun canRemoveFromService(): Boolean = true

    override suspend fun removeFromService(track: Track) = api.remove(track, getUsername())

    override suspend fun search(query: String) = api.search(query)

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.findLibManga(track, getUsername())

        if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.total_chapters = remoteTrack.total_chapters
        }
        return track
    }

    override suspend fun login(
        username: String,
        password: String,
    ) = login(password)

    suspend fun login(code: String): Boolean =
        try {
            val oauth = api.accessToken(code)
            interceptor.newAuth(oauth)
            val user = api.getCurrentUser()
            saveCredentials(user.toString(), oauth.access_token)
            true
        } catch (e: java.lang.Exception) {
            Timber.e(e)
            logout()
            false
        }

    fun saveToken(oauth: OAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): OAuth? =
        try {
            json.decodeFromString<OAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.newAuth(null)
    }
}
