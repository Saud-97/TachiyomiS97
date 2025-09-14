package eu.kanade.tachiyomi.ui.migration.manga.design

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.bluelinelabs.conductor.Controller
import com.fredporciuncula.flow.preferences.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MigrationBottomSheetBinding
import eu.kanade.tachiyomi.ui.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.toInt
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setBottomEdge
import eu.kanade.tachiyomi.widget.E2EBottomSheetDialog
import uy.kohesive.injekt.injectLazy

class MigrationBottomSheetDialog(
    activity: Activity,
    private val listener: StartMigrationListener,
) : E2EBottomSheetDialog<MigrationBottomSheetBinding>(activity) {
    /**
     * Preferences helper.
     */
    private val preferences by injectLazy<PreferencesHelper>()

    override fun createBinding(inflater: LayoutInflater) = MigrationBottomSheetBinding.inflate(inflater)

    init {
        if (activity.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.sourceGroup.orientation = LinearLayout.HORIZONTAL
            val params = binding.skipStep.layoutParams as ConstraintLayout.LayoutParams
            params.apply {
                topToBottom = -1
                startToStart = -1
                bottomToBottom = binding.extraSearchParam.id
                startToEnd = binding.extraSearchParam.id
                endToEnd = binding.sourceGroup.id
                topToTop = binding.extraSearchParam.id
                marginStart = 16.dpToPx
            }
            binding.skipStep.layoutParams = params

            val params2 = binding.extraSearchParamText.layoutParams as ConstraintLayout.LayoutParams
            params2.bottomToBottom = binding.optionsLayout.id
            binding.extraSearchParamText.layoutParams = params2

            val params3 = binding.extraSearchParam.layoutParams as ConstraintLayout.LayoutParams
            params3.endToEnd = -1
            binding.extraSearchParam.layoutParams = params3
        }
        setBottomEdge(
            if (activity.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                binding.extraSearchParamText
            } else {
                binding.skipStep
            },
            activity,
        )
        val contentView = binding.root
        (contentView.parent as View).background = ContextCompat.getDrawable(context, R.drawable.bg_sheet_gradient)
        contentView.post {
            (contentView.parent as View).background = ContextCompat.getDrawable(context, R.drawable.bg_sheet_gradient)
        }
    }

    /**
     * Called when the sheet is created. It initializes the listeners and values of the preferences.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initPreferences()

        // window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        binding.fab.setOnClickListener {
            preferences.skipPreMigration().set(binding.skipStep.isChecked)
            listener.startMigration(
                binding.extraSearchParamText.text?.toString()?.takeIf {
                    it.isNotBlank() && binding.extraSearchParam.isChecked
                },
            )
            dismiss()
        }
    }

    /**
     * Init general reader preferences.
     */
    private fun initPreferences() {
        val flags = preferences.migrateFlags().get()

        val enabledFlags = MigrationFlags.getEnabledFlags(flags)
        MigrationFlags.titles.forEachIndexed { index, title ->
            val checkbox = CheckBox(context)
            checkbox.id = title.hashCode()
            checkbox.text = context.getString(title)
            checkbox.isChecked = enabledFlags[index]
            binding.gridFlagsLayout.addView(checkbox)
            checkbox.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = 8.dpToPx
                topMargin = 8.dpToPx
            }
            checkbox.setOnCheckedChangeListener { _, _ -> setFlags() }
        }

        binding.extraSearchParamText.isVisible = false
        binding.extraSearchParam.setOnCheckedChangeListener { _, isChecked ->
            binding.extraSearchParamText.isVisible = isChecked
        }
        binding.sourceGroup.bindToPreference(preferences.useSourceWithMost())

        binding.skipStep.isChecked = preferences.skipPreMigration().get()
        binding.skipStep.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                (listener as? Controller)?.activity?.toast(
                    R.string.to_show_again_setting_sources,
                    Toast.LENGTH_LONG,
                )
            }
        }
    }

    private fun setFlags() {
        val enabledBoxes =
            binding.gridFlagsLayout.children
                .toList()
                .filterIsInstance<CheckBox>()
                .map { it.isChecked }
        val flags = MigrationFlags.getFlagsFromPositions(enabledBoxes.toTypedArray())
        preferences.migrateFlags().set(flags)
    }

    /**
     * Binds a checkbox or switch view with a boolean preference.
     */
    private fun CompoundButton.bindToPreference(pref: Preference<Boolean>) {
        isChecked = pref.get()
        setOnCheckedChangeListener { _, isChecked -> pref.set(isChecked) }
    }

    /**
     * Binds a radio group with a boolean preference.
     */
    private fun RadioGroup.bindToPreference(pref: Preference<Boolean>) {
        (getChildAt(pref.get().toInt()) as RadioButton).isChecked = true
        setOnCheckedChangeListener { _, value ->
            val index = indexOfChild(findViewById(value))
            pref.set(index == 1)
        }
    }
}

interface StartMigrationListener {
    fun startMigration(extraParam: String?)
}
