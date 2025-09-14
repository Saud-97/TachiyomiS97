package eu.kanade.tachiyomi.ui.extension

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.minusAssign
import eu.kanade.tachiyomi.data.preference.plusAssign
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.onChange
import eu.kanade.tachiyomi.ui.setting.titleRes
import eu.kanade.tachiyomi.util.system.LocaleHelper
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class ExtensionFilterController : SettingsController() {
    private val extensionManager: ExtensionManager by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.extensions

            val activeLangs = preferences.enabledLanguages().get()

            val availableLangs =
                extensionManager.availableExtensionsFlow.value
                    .groupBy { it.lang }
                    .keys
                    .sortedWith(compareBy({ it !in activeLangs }, { LocaleHelper.getSourceDisplayName(it, context) }))

            availableLangs.forEach {
                SwitchPreferenceCompat(context).apply {
                    preferenceScreen.addPreference(this)
                    title = LocaleHelper.getSourceDisplayName(it, context)
                    isPersistent = false
                    isChecked = it in activeLangs

                    onChange { newValue ->
                        if (newValue as Boolean) {
                            preferences.enabledLanguages() += it
                        } else {
                            preferences.enabledLanguages() -= it
                        }
                        true
                    }
                }
            }
        }
}
