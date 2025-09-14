package eu.kanade.tachiyomi.data.preference

import androidx.preference.PreferenceDataStore

class EmptyPreferenceDataStore : PreferenceDataStore() {
    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = false

    override fun putBoolean(
        key: String?,
        value: Boolean,
    ) {
    }

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = 0

    override fun putInt(
        key: String?,
        value: Int,
    ) {
    }

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = 0

    override fun putLong(
        key: String?,
        value: Long,
    ) {
    }

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = 0f

    override fun putFloat(
        key: String?,
        value: Float,
    ) {
    }

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = null

    override fun putString(
        key: String?,
        value: String?,
    ) {
    }

    override fun getStringSet(
        key: String?,
        defValues: Set<String>?,
    ): Set<String>? = null

    override fun putStringSet(
        key: String?,
        values: Set<String>?,
    ) {
    }
}
