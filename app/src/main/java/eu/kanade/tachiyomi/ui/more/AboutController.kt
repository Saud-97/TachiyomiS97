package eu.kanade.tachiyomi.ui.more

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateNotifier
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.add
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.titleRes
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.openInBrowser
import eu.kanade.tachiyomi.util.view.snack
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.DateFormat

class AboutController : SettingsController() {
    /**
     * Checks for new releases
     */
    private val updateChecker by lazy { AppUpdateChecker() }

    private val dateFormat: DateFormat by lazy {
        preferences.dateFormat()
    }

    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.about

            preference {
                key = "pref_whats_new"
                titleRes = R.string.whats_new_this_release
                onClick {
                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            if (BuildConfig.DEBUG) {
                                "https://github.com/saud-97/TachiyomiS97/commits"
                            } else {
                                RELEASE_URL
                            }.toUri(),
                        )
                    startActivity(intent)
                }
            }
            if (isUpdaterEnabled) {
                preference {
                    key = "pref_check_for_updates"
                    titleRes = R.string.check_for_updates
                    onClick {
                        if (activity!!.isOnline()) {
                            checkVersion()
                        } else {
                            activity!!.toast(R.string.no_network_connection)
                        }
                    }
                }
            }
            preference {
                key = "pref_version"
                titleRes = R.string.version
                summary =
                    if (BuildConfig.DEBUG) {
                        "r" + BuildConfig.COMMIT_COUNT
                    } else {
                        BuildConfig.VERSION_NAME
                    }

                onClick {
                    activity?.let {
                        val deviceInfo = CrashLogUtil(it.localeContext).getDebugInfo()
                        val clipboard = it.getSystemService<ClipboardManager>()!!
                        val appInfo = it.getString(R.string.app_info)
                        clipboard.setPrimaryClip(ClipData.newPlainText(appInfo, deviceInfo))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            view?.snack(context.getString(R.string._copied_to_clipboard, appInfo))
                        }
                    }
                }
            }
            preference {
                key = "pref_build_time"
                titleRes = R.string.build_time
                summary = getFormattedBuildTime(dateFormat)
            }

            preferenceCategory {
                preference {
                    key = "pref_about_help_translate"
                    titleRes = R.string.help_translate

                    onClick {
                        openInBrowser("https://hosted.weblate.org/projects/tachiyomi/tachiyomi-j2k/")
                    }
                }
                preference {
                    key = "pref_about_helpful_translation_links"
                    titleRes = R.string.helpful_translation_links

                    onClick {
                        openInBrowser("https://tachiyomi.org/help/contribution/#translation")
                    }
                }
                preference {
                    key = "pref_oss"
                    titleRes = R.string.open_source_licenses

                    onClick {
                        startActivity(Intent(activity, OssLicensesMenuActivity::class.java))
                    }
                }
            }
            add(AboutLinksPreference(context))
        }

    /**
     * Checks version and shows a user prompt if an update is available.
     */
    private fun checkVersion() {
        val activity = activity ?: return

        activity.toast(R.string.searching_for_updates)
        viewScope.launch {
            val result =
                try {
                    updateChecker.checkForUpdate(activity, true)
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) {
                        activity.toast(error.message)
                        Timber.e(error)
                    }
                }
            when (result) {
                is AppUpdateResult.NewUpdate -> {
                    val body = result.release.info
                    val url = result.release.downloadLink
                    val isBeta = result.release.preRelease == true

                    // Create confirmation window
                    withContext(Dispatchers.Main) {
                        AppUpdateNotifier.releasePageUrl = result.release.releaseLink
                        NewUpdateDialogController(body, url, isBeta).showDialog(router)
                    }
                }
                is AppUpdateResult.NoNewUpdate -> {
                    withContext(Dispatchers.Main) {
                        activity.toast(R.string.no_new_updates_available)
                    }
                }
            }
        }
    }

    class NewUpdateDialogController(
        bundle: Bundle? = null,
    ) : DialogController(bundle) {
        constructor(body: String, url: String, isBeta: Boolean?) : this(
            Bundle().apply {
                putString(BODY_KEY, body)
                putString(URL_KEY, url)
                putBoolean(IS_BETA, isBeta == true)
            },
        )

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val releaseBody =
                (args.getString(BODY_KEY) ?: "")
                    .replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
            val info = Markwon.create(activity!!).toMarkdown(releaseBody)

            val isOnA12 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val isBeta = args.getBoolean(IS_BETA, false)
            return activity!!
                .materialAlertDialog()
                .setTitle(
                    if (isBeta) {
                        R.string.new_beta_version_available
                    } else {
                        R.string.new_version_available
                    },
                ).setMessage(info)
                .setPositiveButton(if (isOnA12) R.string.update else R.string.download) { _, _ ->
                    val appContext = applicationContext
                    if (appContext != null) {
                        // Start download
                        val url = args.getString(URL_KEY) ?: ""
                        AppDownloadInstallJob.start(appContext, url, true)
                    }
                }.setNegativeButton(R.string.ignore, null)
                .create()
        }

        override fun onAttach(view: View) {
            super.onAttach(view)
            (dialog?.findViewById(android.R.id.message) as? TextView)?.movementMethod =
                LinkMovementMethod.getInstance()
        }

        companion object {
            const val BODY_KEY = "NewUpdateDialogController.body"
            const val URL_KEY = "NewUpdateDialogController.key"
            const val IS_BETA = "NewUpdateDialogController.is_beta"
        }
    }

    companion object {
        fun getFormattedBuildTime(dateFormat: DateFormat): String {
            return ""
//            try {
//                val inputDf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
//                inputDf.timeZone = TimeZone.getTimeZone("UTC")
//                val buildTime =
//                    inputDf.parse(BuildConfig.BUILD_TIME) ?: return BuildConfig.BUILD_TIME
//
//                return buildTime.toTimestampString(dateFormat)
//            } catch (e: ParseException) {
//                return BuildConfig.BUILD_TIME
//            }
        }
    }
}
