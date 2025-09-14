package eu.kanade.tachiyomi.ui.setting

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceController
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.BackHandlerControllerInterface
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlinx.coroutines.MainScope
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

abstract class SettingsController :
    PreferenceController(),
    BackHandlerControllerInterface {
    var preferenceKey: String? = null
    val preferences: PreferencesHelper = Injekt.get()
    val viewScope = MainScope()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        listView.layoutManager = LinearLayoutManagerAccurateOffset(view.context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        view.backgroundColor = view.context.getResourceColor(R.attr.background)
        scrollViewWith(listView, padBottom = true)
        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        preferenceKey?.let { prefKey ->
            val adapter = listView.adapter
            scrollToPreference(prefKey)

            listView.post {
                if (adapter is PreferenceGroup.PreferencePositionCallback) {
                    val pos = adapter.getPreferenceAdapterPosition(prefKey)
                    listView.findViewHolderForAdapterPosition(pos)?.let {
                        animatePreferenceHighlight(it.itemView)
                    }
                    preferenceKey = null
                }
            }
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val screen = preferenceManager.createPreferenceScreen(getThemedContext())
        preferenceScreen = screen
        setupPreferenceScreen(screen)
    }

    abstract fun setupPreferenceScreen(screen: PreferenceScreen): PreferenceScreen

    open fun onActionViewExpand(item: MenuItem?) { }

    open fun onActionViewCollapse(item: MenuItem?) { }

    private fun getThemedContext(): Context {
        val tv = TypedValue()
        activity!!.theme.resolveAttribute(R.attr.preferenceTheme, tv, true)
        return ContextThemeWrapper(activity, tv.resourceId)
    }

    private fun animatePreferenceHighlight(view: View) {
        ValueAnimator
            .ofObject(ArgbEvaluator(), Color.TRANSPARENT, view.context.getResourceColor(R.attr.colorControlHighlight))
            .apply {
                duration = 500L
                repeatCount = 2
                addUpdateListener { animator -> view.setBackgroundColor(animator.animatedValue as Int) }
                reverse()
            }
    }

    open fun getTitle(): String? = preferenceScreen?.title?.toString()

    open fun getSearchTitle(): String? =
        if (this is FloatingSearchInterface) {
            searchTitle(preferenceScreen?.title?.toString()?.lowercase(Locale.ROOT))
        } else {
            null
        }

    fun setTitle() {
        var parentController = parentController
        while (parentController != null) {
            if (parentController is BaseController<*> && parentController.getTitle() != null) {
                return
            }
            parentController = parentController.parentController
        }

        (activity as? AppCompatActivity)?.title = getTitle()
        (activity as? MainActivity)?.searchTitle = getSearchTitle()
        activityBinding?.bigIconLayout?.isVisible = false
    }

    override fun onChangeStarted(
        handler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        if (type.isEnter && isControllerVisible) {
            setTitle()
        } else if (type.isEnter) {
            view?.alpha = 0f
        }
        setHasOptionsMenu(type.isEnter && isControllerVisible)
        super.onChangeStarted(handler, type)
    }

    inline fun <T> Preference.visibleIf(
        preference: com.fredporciuncula.flow.preferences.Preference<T>,
        crossinline block: (T) -> Boolean,
    ) {
        preference.asImmediateFlowIn(viewScope) { isVisible = block(it) }
    }
}
