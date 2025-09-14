package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.tryToSetForeground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This worker is used to manage the downloader. The system can decide to stop the worker, in
 * which case the downloader is also stopped. It's also stopped while there's no network available.
 */
class DownloadJob(
    val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    private val downloadManager: DownloadManager = Injekt.get()
    private val preferences: PreferencesHelper = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val firstDL = downloadManager.queue.firstOrNull()
        val notification = DownloadNotifier(context).setPlaceholder(firstDL).build()
        val id = Notifications.ID_DOWNLOAD_CHAPTER
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    override suspend fun doWork(): Result {
        tryToSetForeground()

        var networkCheck = checkConnectivity()
        var active = networkCheck
        if (active) {
            downloadManager.startDownloads()
        }
        val runExtJobAfter = inputData.getBoolean(START_EXT_JOB_AFTER, false)

        // Keep the worker running when needed
        return try {
            while (active) {
                delay(100)
                networkCheck = checkConnectivity()
                active = !isStopped && networkCheck && downloadManager.isRunning
            }
            Result.success()
        } catch (_: CancellationException) {
            Result.success()
        } finally {
            callListeners(false, downloadManager)
            if (runExtJobAfter) {
                ExtensionUpdateJob.runJobAgain(applicationContext, NetworkType.CONNECTED)
            }
        }
    }

    private fun checkConnectivity(): Boolean =
        with(applicationContext) {
            if (isOnline()) {
                val noWifi = preferences.downloadOnlyOverWifi() && !isConnectedToWifi()
                if (noWifi) {
                    downloadManager.stopDownloads(applicationContext.getString(R.string.no_wifi_connection))
                }
                !noWifi
            } else {
                downloadManager.stopDownloads(applicationContext.getString(R.string.no_network_connection))
                false
            }
        }

    companion object {
        private const val TAG = "Downloader"
        private const val START_EXT_JOB_AFTER = "StartExtJobAfter"

        private val downloadChannel =
            MutableSharedFlow<Boolean>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val downloadFlow = downloadChannel.asSharedFlow()

        fun start(
            context: Context,
            alsoStartExtJob: Boolean = false,
        ) {
            val request =
                OneTimeWorkRequestBuilder<DownloadJob>()
                    .addTag(TAG)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .apply {
                        if (alsoStartExtJob) {
                            setInputData(workDataOf(START_EXT_JOB_AFTER to true))
                        }
                    }.build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }

        fun callListeners(
            downloading: Boolean? = null,
            downloadManager: DownloadManager? = null,
        ) {
            val dManager by lazy { downloadManager ?: Injekt.get() }
            downloadChannel.tryEmit(downloading ?: !dManager.isPaused())
        }

        fun isRunning(context: Context): Boolean =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
    }
}
