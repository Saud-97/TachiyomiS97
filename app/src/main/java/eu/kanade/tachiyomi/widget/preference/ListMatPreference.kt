package eu.kanade.tachiyomi.widget.preference

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class ListMatPreference
    @JvmOverloads
    constructor(
        activity: Activity?,
        context: Context,
        attrs: AttributeSet? =
            null,
    ) : MatPreference(activity, context, attrs) {
        var entryValues: List<String> = emptyList()
        var entriesRes: Array<Int>
            get() = emptyArray()
            set(value) {
                entries = value.map { context.getString(it) }
            }
        private var defValue: String = ""
        var tempEntry: String? = null
        var tempValue: Int? = null
        var entries: List<String> = emptyList()

        override fun onSetInitialValue(defaultValue: Any?) {
            super.onSetInitialValue(defaultValue)
            defValue = defaultValue as? String ?: defValue
        }

        override fun setDefaultValue(defaultValue: Any?) {
            super.setDefaultValue(defaultValue)
            defValue = defaultValue as? String ?: defValue
        }

        private val indexOfPref: Int
            get() =
                tempValue ?: entryValues.indexOf(
                    if (isPersistent || preferenceDataStore != null) {
                        preferenceDataStore?.getString(key, defValue)
                            ?: sharedPreferences?.getString(key, defValue)
                    } else {
                        tempEntry
                    } ?: defValue,
                )

        override var customSummaryProvider: SummaryProvider<MatPreference>? =
            SummaryProvider<MatPreference> {
                val index = indexOfPref
                if (entries.isEmpty() || index == -1) {
                    ""
                } else {
                    tempEntry ?: entries.getOrNull(index) ?: ""
                }
            }

        override fun dialog(): MaterialAlertDialogBuilder =
            super.dialog().apply {
                setListItems()
            }

        @SuppressLint("CheckResult")
        open fun MaterialAlertDialogBuilder.setListItems() {
            val default = indexOfPref
            setSingleChoiceItems(entries.toTypedArray(), default) { dialog, pos ->
                val value = entryValues[pos]
                if (callChangeListener(value)) {
                    if (isPersistent) {
                        if (preferenceDataStore != null) {
                            preferenceDataStore?.putString(key, value)
                            notifyChanged()
                        } else {
                            sharedPreferences?.edit { putString(key, value) }
                        }
                    } else {
                        tempValue = pos
                        tempEntry = entries.getOrNull(pos)
                        notifyChanged()
                    }
                }
                this@ListMatPreference.summary = this@ListMatPreference.summary
                dialog.dismiss()
            }
        }
    }
