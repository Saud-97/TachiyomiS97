package eu.kanade.tachiyomi.ui.setting

import android.app.ActivityManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.content.getSystemService
import androidx.preference.PreferenceScreen
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.more.AboutController
import eu.kanade.tachiyomi.ui.setting.search.SettingsSearchController
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.fadeTransactionHandler
import eu.kanade.tachiyomi.util.view.openInBrowser
import eu.kanade.tachiyomi.util.view.withFadeTransaction

class SettingsMainController :
    SettingsController(),
    FloatingSearchInterface {
    init {
        setHasOptionsMenu(true)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        with(screen) {
            titleRes = R.string.settings

            val tintColor = context.getResourceColor(R.attr.colorSecondary)

            preference {
                iconRes = R.drawable.ic_tune_24dp
                iconTint = tintColor
                titleRes = R.string.general
                onClick { navigateTo(SettingsGeneralController()) }
            }
            preference {
                iconRes = R.drawable.ic_appearance_outline_24dp
                iconTint = tintColor
                titleRes = R.string.appearance
                onClick { navigateTo(SettingsAppearanceController()) }
            }
            preference {
                iconRes = R.drawable.ic_library_outline_24dp
                iconTint = tintColor
                titleRes = R.string.library
                onClick { navigateTo(SettingsLibraryController()) }
            }
            preference {
                iconRes = R.drawable.ic_read_outline_24dp
                iconTint = tintColor
                titleRes = R.string.reader
                onClick { navigateTo(SettingsReaderController()) }
            }
            preference {
                iconRes = R.drawable.ic_file_download_24dp
                iconTint = tintColor
                titleRes = R.string.downloads
                onClick { navigateTo(SettingsDownloadController()) }
            }
            preference {
                iconRes = R.drawable.ic_browse_outline_24dp
                iconTint = tintColor
                titleRes = R.string.browse
                onClick { navigateTo(SettingsBrowseController()) }
            }
            preference {
                iconRes = R.drawable.ic_sync_24dp
                iconTint = tintColor
                titleRes = R.string.tracking
                onClick { navigateTo(SettingsTrackingController()) }
            }
            preference {
                iconRes = R.drawable.ic_backup_restore_24dp
                iconTint = tintColor
                titleRes = R.string.backup_and_restore
                onClick { navigateTo(SettingsBackupController()) }
            }
            preference {
                iconRes = R.drawable.ic_security_24dp
                iconTint = tintColor
                titleRes = R.string.security
                onClick { navigateTo(SettingsSecurityController()) }
            }
            preference {
                iconRes = R.drawable.ic_code_24dp
                iconTint = tintColor
                titleRes = R.string.advanced
                onClick { navigateTo(SettingsAdvancedController()) }
            }
            preference {
                iconRes = R.drawable.ic_info_outline_24dp
                iconTint = tintColor
                titleRes = R.string.about
                onClick { navigateTo(AboutController()) }
            }
            this
        }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.settings_main, menu)
        // Change hint to show global search.
        activityBinding?.searchToolbar?.searchQueryHint = applicationContext?.getString(R.string.search_settings)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_help -> openInBrowser(URL_HELP)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActionViewExpand(item: MenuItem?) {
        val isLowRam = activity?.getSystemService<ActivityManager>()?.isLowRamDevice == true
        router.pushController(
            RouterTransaction
                .with(SettingsSearchController())
                .pushChangeHandler(SimpleSwapChangeHandler(removesFromViewOnPush = isLowRam))
                .popChangeHandler(fadeTransactionHandler()),
        )
    }

    private fun navigateTo(controller: Controller) {
        router.pushController(controller.withFadeTransaction())
    }

    private companion object {
        private const val URL_HELP = "https://tachiyomi.org/docs/guides/troubleshooting/"
    }
}
