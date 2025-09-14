package eu.kanade.tachiyomi.appwidget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager

class TachiyomiWidgetManager {
    suspend fun Context.init() {
        val manager = GlanceAppWidgetManager(this)
        if (manager.getGlanceIds(UpdatesGridGlanceWidget::class.java).isNotEmpty()) {
            UpdatesGridGlanceWidget().loadData()
        }
    }
}
