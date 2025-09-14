package eu.kanade.tachiyomi.widget.preference

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.preference.Preference.SummaryProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R

class EditTextResetPreference
    @JvmOverloads
    constructor(
        activity: Activity?,
        context: Context,
        attrs: AttributeSet? = null,
    ) : MatPreference(activity, context, attrs) {
        private var defValue: String = ""
        var dialogSummary: CharSequence? = null

        override fun onSetInitialValue(defaultValue: Any?) {
            super.onSetInitialValue(defaultValue)
            defValue = defaultValue as? String ?: defValue
        }

        override fun didShow(dialog: DialogInterface) {
            val textView = (dialog as? AlertDialog)?.findViewById<EditText>(android.R.id.edit) ?: return
            textView.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }

        override fun setDefaultValue(defaultValue: Any?) {
            super.setDefaultValue(defaultValue)
            defValue = defaultValue as? String ?: defValue
        }

        override var customSummaryProvider: SummaryProvider<MatPreference>? =
            SummaryProvider<MatPreference> {
                sharedPreferences?.getString(key, defValue)
            }

        override fun dialog(): MaterialAlertDialogBuilder =
            super.dialog().apply {
                val attrs = intArrayOf(android.R.attr.dialogLayout)
                val a = context.obtainStyledAttributes(R.style.Preference_DialogPreference_EditTextPreference_Material, attrs)
                val resourceId = a.getResourceId(0, 0)
                val view = LayoutInflater.from(context).inflate(resourceId, null)
                val textView = view.findViewById<EditText>(android.R.id.edit)
                val message = view.findViewById<TextView>(android.R.id.message)
                message?.isVisible = dialogSummary != null
                message?.text = dialogSummary
                textView?.append(
                    preferenceDataStore?.getString(key, defValue)
                        ?: sharedPreferences?.getString(key, defValue),
                )
                val inputMethodManager: InputMethodManager =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(textView, WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
                // Place cursor at the end
                textView.setSelection(textView.text.length)
                this.setView(view)
                this.setNeutralButton(R.string.reset) { _, _ ->
                    if (callChangeListener(defValue)) {
                        if (preferenceDataStore != null) {
                            preferenceDataStore?.putString(key, null)
                        } else {
                            sharedPreferences?.edit { remove(key) }
                        }
                        notifyChanged()
                    }
                }
                this.setPositiveButton(android.R.string.ok) { _, _ ->
                    if (callChangeListener(textView.text.toString())) {
                        if (preferenceDataStore != null) {
                            if (textView.text.isNullOrBlank()) {
                                preferenceDataStore?.putString(key, null)
                            } else {
                                preferenceDataStore?.putString(key, textView.text.toString())
                            }
                        } else {
                            sharedPreferences?.edit {
                                if (textView.text.isNullOrBlank()) {
                                    remove(key)
                                } else {
                                    putString(key, textView.text.toString())
                                }
                            }
                        }
                        notifyChanged()
                    }
                }
                a.recycle()
            }
    }
