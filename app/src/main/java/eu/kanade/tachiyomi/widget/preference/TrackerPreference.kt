package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.card.MaterialCardView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPx

class TrackerPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs) {
        init {
            layoutResource = R.layout.pref_tracker_item
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)

            val logoContainer = holder.findViewById(R.id.logo_container) as MaterialCardView
            val checkedIcon = holder.findViewById(R.id.checked_icon) as ImageView

            logoContainer.setCardBackgroundColor(iconColor)
            val padding = if (Color.alpha(iconColor) == 0) 0 else 4.dpToPx
            holder.findViewById(android.R.id.icon).setPadding(padding)
            checkedIcon.isVisible = !getPersistedString("").isNullOrEmpty()
        }

        @ColorInt
        var iconColor: Int = Color.TRANSPARENT
            set(value) {
                field = value
                notifyChanged()
            }

        public override fun notifyChanged() {
            super.notifyChanged()
        }
    }
