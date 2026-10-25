package com.mokie.timelogdemo.tracking

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.mokie.timelogdemo.R
import com.mokie.timelogdemo.timeLogApp

/**
 * Home-screen timer widget. Pushed by TimerController on every timer state change
 * (no periodic updates); the Chronometer view ticks on its own.
 */
class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, buildViews(context, context.timeLogApp.tracking))
    }

    companion object {
        private const val REQ_OPEN = 20
        private const val REQ_START = 21
        private const val REQ_TOGGLE = 22
        private const val REQ_STOP = 23

        fun updateAll(context: Context, tracking: TrackingSession) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, buildViews(context, tracking))
        }

        private fun buildViews(context: Context, tracking: TrackingSession): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_timer)
            views.setOnClickPendingIntent(R.id.widget_root, TimerCommands.openAppIntent(context, REQ_OPEN))

            if (!tracking.isActive) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                views.setViewVisibility(R.id.widget_idle, View.VISIBLE)
                views.setViewVisibility(R.id.widget_chrono, View.GONE)
                views.setChronometer(R.id.widget_chrono, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.widget_start, View.VISIBLE)
                views.setViewVisibility(R.id.widget_pause, View.GONE)
                views.setViewVisibility(R.id.widget_stop, View.GONE)
                views.setOnClickPendingIntent(
                    R.id.widget_start,
                    TimerCommands.broadcastIntent(context, REQ_START, TimerActionReceiver.ACTION_START_BLANK)
                )
                return views
            }

            val tracks = tracking.tracks
            val title = when {
                tracks.isEmpty() -> "未选主题"
                tracks.size == 1 -> tracks.single().name
                else -> "${tracks.first().name} +${tracks.size - 1}"
            }
            views.setTextViewText(
                R.id.widget_title,
                if (tracking.isPaused) "$title · 已暂停" else title
            )
            val elapsed = tracking.elapsedMs(System.currentTimeMillis())
            views.setChronometer(
                R.id.widget_chrono,
                SystemClock.elapsedRealtime() - elapsed,
                null,
                !tracking.isPaused
            )
            views.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
            views.setViewVisibility(R.id.widget_idle, View.GONE)
            views.setViewVisibility(R.id.widget_start, View.GONE)
            views.setViewVisibility(R.id.widget_pause, View.VISIBLE)
            views.setViewVisibility(R.id.widget_stop, View.VISIBLE)
            views.setTextViewText(R.id.widget_pause, if (tracking.isPaused) "继续" else "暂停")
            views.setOnClickPendingIntent(
                R.id.widget_pause,
                TimerCommands.broadcastIntent(context, REQ_TOGGLE, TimerActionReceiver.ACTION_TOGGLE_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_stop,
                if (TimerCommands.needsInAppStop(tracking)) {
                    TimerCommands.stopInAppIntent(context, REQ_STOP)
                } else {
                    TimerCommands.broadcastIntent(context, REQ_STOP, TimerActionReceiver.ACTION_STOP)
                }
            )
            return views
        }
    }
}
