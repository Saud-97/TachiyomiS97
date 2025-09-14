package eu.kanade.tachiyomi.data.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import uy.kohesive.injekt.injectLazy
import java.util.regex.Pattern

/**
 * DownloadNotifier is used to show notifications when downloading one or multiple chapters.
 *
 * @param context context of application
 */
internal class DownloadNotifier(
    private val context: Context,
) {
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Notification builder.
     */
    private val notification by lazy {
        NotificationCompat
            .Builder(context, Notifications.CHANNEL_DOWNLOADER)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
    }

    /**
     * Status of download. Used for correct notification icon.
     */
    private var isDownloading = false

    /**
     * Updated when error is thrown
     */
    var errorThrown = false

    /**
     * Shows a notification from this builder.
     *
     * @param id the id of the notification.
     */
    private fun NotificationCompat.Builder.show(id: Int = Notifications.ID_DOWNLOAD_CHAPTER) {
        context.notificationManager.notify(id, build())
    }

    /**
     * Dismiss the downloader's notification. Downloader error notifications use a different id, so
     * those can only be dismissed by the user.
     */
    fun dismiss() {
        context.notificationManager.cancel(Notifications.ID_DOWNLOAD_CHAPTER)
    }

    fun setPlaceholder(download: Download?): NotificationCompat.Builder {
        val context = context.localeContext
        with(notification) {
            // Check if first call.
            if (!isDownloading) {
                setSmallIcon(android.R.drawable.stat_sys_download)
                setAutoCancel(false)
                clearActions()
                setOngoing(true)
                // Open download manager when clicked
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
                color = ContextCompat.getColor(context, R.color.secondaryTachiyomi)
                isDownloading = true
                // Pause action
                addAction(
                    R.drawable.ic_pause_24dp,
                    context.getString(R.string.pause),
                    NotificationReceiver.pauseDownloadsPendingBroadcast(context),
                )
            }

            if (download != null && !preferences.hideNotificationContent()) {
                val title = download.manga.title.chop(15)
                val quotedTitle = Pattern.quote(title)
                val name = download.chapter.preferredChapterName(context, download.manga, preferences)
                val chapter =
                    name.replaceFirst(
                        "$quotedTitle[\\s]*[-]*[\\s]*"
                            .toRegex(RegexOption.IGNORE_CASE),
                        "",
                    )
                setContentTitle("$title - $chapter".chop(30))
                setContentText(context.getString(R.string.downloading))
            } else {
                setContentTitle(context.getString(R.string.downloading))
                setContentText(null)
            }
            setProgress(0, 0, true)
            setStyle(null)
        }
        return notification
    }

    /**
     * Called when download progress changes.
     *
     * @param download download object containing download information.
     */
    fun onProgressChange(download: Download) {
        // Create notification
        with(notification) {
            // Check if first call.
            if (!isDownloading) {
                setSmallIcon(android.R.drawable.stat_sys_download)
                setAutoCancel(false)
                clearActions()
                setOngoing(true)
                // Open download manager when clicked
                color = ContextCompat.getColor(context, R.color.secondaryTachiyomi)
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
                isDownloading = true
                // Pause action
                addAction(
                    R.drawable.ic_pause_24dp,
                    context.getString(R.string.pause),
                    NotificationReceiver.pauseDownloadsPendingBroadcast(context),
                )
            }

            val downloadingProgressText =
                context.localeContext
                    .getString(R.string.downloading_progress)
                    .format(download.downloadedImages, download.pages!!.size)

            if (preferences.hideNotificationContent()) {
                setContentTitle(downloadingProgressText)
            } else {
                val title = download.manga.title.chop(15)
                val quotedTitle = Pattern.quote(title)
                val name = download.chapter.preferredChapterName(context, download.manga, preferences)
                val chapter =
                    name.replaceFirst(
                        "$quotedTitle[\\s]*[-]*[\\s]*".toRegex(RegexOption.IGNORE_CASE),
                        "",
                    )
                setContentTitle("$title - $chapter".chop(30))
                setContentText(downloadingProgressText)
            }
            setStyle(null)
            setProgress(download.pages!!.size, download.downloadedImages, false)
        }
        // Displays the progress bar on notification
        notification.show()
    }

    /**
     * Show notification when download is paused.
     */
    fun onDownloadPaused() {
        val context = context.localeContext
        with(notification) {
            setContentTitle(context.getString(R.string.paused))
            setContentText(context.getString(R.string.download_paused))
            setSmallIcon(R.drawable.ic_pause_24dp)
            setAutoCancel(false)
            setOngoing(false)
            setProgress(0, 0, false)
            color = ContextCompat.getColor(context, R.color.secondaryTachiyomi)
            clearActions()
            // Open download manager when clicked
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            // Resume action
            addAction(
                R.drawable.ic_play_arrow_24dp,
                context.getString(R.string.resume),
                NotificationReceiver.resumeDownloadsPendingBroadcast(context),
            )
            // Clear action
            addAction(
                R.drawable.ic_close_24dp,
                context.getString(R.string.cancel_all),
                NotificationReceiver.clearDownloadsPendingBroadcast(context),
            )
            show()
        }

        // Reset initial values
        isDownloading = false
    }

    /**
     * Called when the downloader receives a warning.
     *
     * @param reason the text to show.
     */
    fun onWarning(reason: String) {
        val context = context.localeContext
        with(notification) {
            setContentTitle(context.getString(R.string.downloads))
            setContentText(reason)
            color = ContextCompat.getColor(context, R.color.secondaryTachiyomi)
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setOngoing(false)
            setAutoCancel(true)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            setProgress(0, 0, false)
        }
        notification.show(Notifications.ID_DOWNLOAD_CHAPTER_ERROR)

        // Reset download information
        isDownloading = false
    }

    /**
     * Called when the downloader has too many downloads from one source.
     */
    fun massDownloadWarning() {
        val context = context.localeContext
        val notification =
            context
                .notificationBuilder(Notifications.CHANNEL_DOWNLOADER) {
                    setContentTitle(context.getString(R.string.warning))
                    setSmallIcon(R.drawable.ic_warning_white_24dp)
                    setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(context.getString(R.string.download_queue_size_warning)),
                    )
                    setContentIntent(
                        NotificationHandler.openUrl(
                            context,
                            LibraryUpdateNotifier.HELP_WARNING_URL,
                        ),
                    )
                    setTimeoutAfter(30000)
                }.build()

        context.notificationManager.notify(
            Notifications.ID_DOWNLOAD_SIZE_WARNING,
            notification,
        )
    }

    /**
     * Called when the downloader receives an error. It's shown as a separate notification to avoid
     * being overwritten.
     *
     * @param error string containing error information.
     * @param chapter string containing chapter title.
     */
    fun onError(
        error: String? = null,
        chapter: String? = null,
        mangaTitle: String? = null,
        customIntent: Intent? = null,
    ) {
        // Create notification
        val context = context.localeContext
        with(notification) {
            setContentTitle(
                mangaTitle?.plus(": $chapter") ?: context.getString(R.string.download_error),
            )
            setContentText(error ?: context.getString(R.string.could_not_download_unexpected_error))
            setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    error ?: context.getString(R.string.could_not_download_unexpected_error),
                ),
            )
            setSmallIcon(android.R.drawable.stat_sys_warning)
            setCategory(NotificationCompat.CATEGORY_ERROR)
            setOngoing(false)
            clearActions()
            setAutoCancel(true)
            if (customIntent != null) {
                setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        customIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            } else {
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            }
            color = ContextCompat.getColor(context, R.color.secondaryTachiyomi)
            setProgress(0, 0, false)
        }
        notification.show(Notifications.ID_DOWNLOAD_CHAPTER_ERROR)

        // Reset download information
        errorThrown = true
        isDownloading = false
    }
}
