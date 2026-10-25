package com.mokie.timelogdemo.tracking

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.mokie.timelogdemo.R
import com.mokie.timelogdemo.timeLogApp

/** 1x1 widget: one button. Idle → start a theme-less timer; running → pause / resume. */
class QuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        context.timeLogApp.widgetUpdater.refresh()
    }

    companion object {
        private const val REQ_TOGGLE = 60

        fun updateAll(context: Context, tracking: TrackingSession) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context, tracking))
        }

        internal fun buildViews(context: Context, tracking: TrackingSession): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quick)
            val accent = ContextCompat.getColor(context, R.color.widget_accent)
            val onAccent = ContextCompat.getColor(context, R.color.widget_on_accent)
            val onBgVariant = ContextCompat.getColor(context, R.color.widget_on_bg_variant)
            val running = tracking.isActive && !tracking.isPaused

            views.setInt(
                R.id.quick_root,
                "setBackgroundResource",
                if (running) R.drawable.widget_quick_running else R.drawable.widget_quick_idle
            )
            views.setImageViewResource(
                R.id.quick_icon,
                if (running) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            views.setInt(R.id.quick_icon, "setColorFilter", if (running) onAccent else accent)

            if (tracking.isActive) {
                val elapsed = tracking.elapsedMs(System.currentTimeMillis())
                views.setChronometer(R.id.quick_chrono, SystemClock.elapsedRealtime() - elapsed, null, running)
                views.setTextColor(R.id.quick_chrono, if (running) onAccent else onBgVariant)
                views.setViewVisibility(R.id.quick_chrono, View.VISIBLE)
                views.setViewVisibility(R.id.quick_label, View.GONE)
            } else {
                views.setChronometer(R.id.quick_chrono, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.quick_chrono, View.GONE)
                views.setViewVisibility(R.id.quick_label, View.VISIBLE)
            }

            views.setOnClickPendingIntent(
                R.id.quick_root,
                TimerCommands.broadcastIntent(context, REQ_TOGGLE, TimerActionReceiver.ACTION_QUICK_TOGGLE)
            )
            return views
        }
    }
}
