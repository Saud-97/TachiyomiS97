package eu.kanade.tachiyomi.ui.recents

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R

enum class RecentsViewType(
    val mainValue: Int,
    @StringRes val stringRes: Int,
) {
    GroupedAll(0, R.string.grouped),
    UngroupedAll(1, R.string.all),
    History(2, R.string.history),
    Updates(3, R.string.updates),
    ;

    val isAll get() = this == GroupedAll || this == UngroupedAll
    val isHistory get() = this == History
    val isUpdates get() = this == Updates

    companion object {
        fun valueOf(value: Int?) = entries.find { it.mainValue == value } ?: GroupedAll
    }
}
