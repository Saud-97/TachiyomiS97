package eu.kanade.tachiyomi.ui.reader.settings

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R

enum class ReaderBackgroundColor(
    val prefValue: Int,
    @StringRes val stringRes: Int,
    @StringRes val longStringRes: Int? = null,
) {
    WHITE(0, R.string.white),
    GRAY(4, R.string.gray_background),
    BLACK(1, R.string.black),
    SMART_PAGE(2, R.string.smart_by_page, R.string.smart_based_on_page),
    SMART_THEME(3, R.string.smart_by_theme, R.string.smart_based_on_page_and_theme),
    ;

    val isSmartColor get() = this == SMART_PAGE || this == SMART_THEME

    companion object {
        fun indexFromPref(preference: Int) = entries.indexOf(fromPreference(preference))

        fun fromPreference(preference: Int): ReaderBackgroundColor = entries.find { it.prefValue == preference } ?: SMART_PAGE
    }
}
