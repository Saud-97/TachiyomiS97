package eu.kanade.tachiyomi.data.track.kavita

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.sourcePreferences
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest

class Kavita(
    private val context: Context,
    id: Int,
) : TrackService(id),
    EnhancedTrackService {
    companion object {
        const val UNREAD = 1
        const val READING = 2
        const val COMPLETED = 3
    }

    var authentications: OAuth? = null

    private val interceptor by lazy { KavitaInterceptor(this) }
    val api by lazy { KavitaApi(client, interceptor) }

    private val sourceManager: SourceManager by injectLazy()

    @StringRes
    override fun nameRes() = R.string.kavita

    override fun getLogo(): Int = R.drawable.ic_tracker_kavita

    override fun getTrackerColor() = Color.rgb(85, 199, 148)

    override fun getLogoColor() = Color.argb(0, 85, 199, 148)

    override fun getStatusList() = listOf(UNREAD, READING, COMPLETED)

    override fun isCompletedStatus(index: Int): Boolean = getStatusList()[index] == COMPLETED

    override fun getStatus(status: Int): String =
        with(context) {
            when (status) {
                UNREAD -> getString(R.string.unread)
                READING -> getString(R.string.reading)
                COMPLETED -> getString(R.string.completed)
                else -> ""
            }
        }

    override fun getGlobalStatus(status: Int): String =
        with(context) {
            when (status) {
                UNREAD -> getString(R.string.plan_to_read)
                READING -> getString(R.string.reading)
                COMPLETED -> getString(R.string.completed)
                else -> ""
            }
        }

    override fun completedStatus(): Int = COMPLETED

    override fun readingStatus() = READING

    override fun planningStatus() = UNREAD

    override fun getScoreList(): List<String> = emptyList()

    override fun displayScore(track: Track): String = ""

    override suspend fun add(track: Track): Track {
        track.status = READING
        updateNewTrackInfo(track)
        return api.updateProgress(track)
    }

    override suspend fun update(
        track: Track,
        setToRead: Boolean,
    ): Track {
        updateTrackStatus(track, setToRead)
        return api.updateProgress(track)
    }

    override suspend fun bind(track: Track): Track = track

    override suspend fun search(query: String): List<TrackSearch> {
        TODO("Not yet implemented: search")
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(
        username: String,
        password: String,
    ): Boolean {
        saveCredentials("user", "pass")
        return true
    }

    // TrackService.isLogged works by checking that credentials are saved.
    // By saving dummy, unused credentials, we can activate the tracker simply by login/logout
    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources() = listOf("eu.kanade.tachiyomi.extension.all.kavita.Kavita")

    override suspend fun match(manga: Manga): TrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            null
        }

    override fun isTrackFrom(
        track: Track,
        manga: Manga,
        source: Source?,
    ): Boolean = track.tracking_url == manga.url && source?.let { accept(it) } == true

    fun loadOAuth() {
        val oauth = OAuth()
        for (id in 1..3) {
            val authentication = oauth.authentications[id - 1]
            val sourceId by lazy {
                val key = "kavita_$id/all/1" // Hardcoded versionID to 1
                val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
                (0..7)
                    .map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
                    .reduce(Long::or) and Long.MAX_VALUE
            }
            val preferences = (sourceManager.get(sourceId) as ConfigurableSource).sourcePreferences()

            val prefApiUrl = preferences.getString("APIURL", "")
            val prefApiKey = preferences.getString("APIKEY", "")
            if (prefApiUrl.isNullOrEmpty() || prefApiKey.isNullOrEmpty()) {
                // Source not configured. Skip
                continue
            }

            val token = api.getNewToken(apiUrl = prefApiUrl, apiKey = prefApiKey)
            if (token.isNullOrEmpty()) {
                // Source is not accessible. Skip
                continue
            }

            authentication.apiUrl = prefApiUrl
            authentication.jwtToken = token.toString()
        }
        authentications = oauth
    }
}
