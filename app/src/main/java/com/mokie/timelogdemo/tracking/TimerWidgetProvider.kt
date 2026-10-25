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
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef
import com.mokie.timelogdemo.ui.theme.trackColorArgb

/**
 * 2x2 theme widget: the running timer on top (pause / stop, or a blank quick start
 * when idle) and the four most recent themes below, each one tap away from timing.
 * Pushed by WidgetUpdater; the Chronometer views tick on their own.
 */
class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        context.timeLogApp.widgetUpdater.refresh()
    }

    companion object {
        const val SLOTS = 4

        private const val REQ_OPEN = 20
        private const val REQ_START = 21
        private const val REQ_TOGGLE = 22
        private const val REQ_STOP = 23
        private const val REQ_PICK_BASE = 40

        private val slotIds = intArrayOf(R.id.slot0, R.id.slot1, R.id.slot2, R.id.slot3)
        private val slotDotIds = intArrayOf(R.id.slot0_dot, R.id.slot1_dot, R.id.slot2_dot, R.id.slot3_dot)
        private val slotNameIds = intArrayOf(R.id.slot0_name, R.id.slot1_name, R.id.slot2_name, R.id.slot3_name)

        fun updateAll(context: Context, tracking: TrackingSession, shown: List<TrackRef>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context, tracking, shown))
        }

        internal fun buildViews(context: Context, tracking: TrackingSession, shown: List<TrackRef>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tracks)
            bindHeader(context, views, tracking)
            bindSlots(context, views, tracking, shown)
            return views
        }

        private fun bindHeader(context: Context, views: RemoteViews, tracking: TrackingSession) {
            val onBg = ContextCompat.getColor(context, R.color.widget_on_bg)
            val onAccent = ContextCompat.getColor(context, R.color.widget_on_accent)
            views.setOnClickPendingIntent(R.id.hdr, TimerCommands.openAppIntent(context, REQ_OPEN))

            if (!tracking.isActive) {
                views.setTextViewText(R.id.hdr_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.hdr_sub, "点主题开始计时")
                views.setViewVisibility(R.id.hdr_sub, View.VISIBLE)
                views.setViewVisibility(R.id.hdr_dot, View.GONE)
                views.setChronometer(R.id.hdr_chrono, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.hdr_chrono, View.GONE)
                views.setViewVisibility(R.id.hdr_pause, View.GONE)
                views.setViewVisibility(R.id.hdr_stop, View.GONE)
                views.setViewVisibility(R.id.hdr_quick, View.VISIBLE)
                views.setInt(R.id.hdr_quick, "setColorFilter", onAccent)
                views.setOnClickPendingIntent(
                    R.id.hdr_quick,
                    TimerCommands.broadcastIntent(context, REQ_START, TimerActionReceiver.ACTION_START_BLANK)
                )
                return
            }

            val tracks = tracking.tracks
            val name = when {
                tracks.isEmpty() -> "未选主题 · 点下方指定"
                tracks.size == 1 -> tracks.single().name
                else -> "${tracks.first().name} +${tracks.size - 1}"
            }
            views.setTextViewText(R.id.hdr_title, name)
            views.setViewVisibility(R.id.hdr_dot, View.VISIBLE)
            views.setInt(
                R.id.hdr_dot,
                "setColorFilter",
                trackColorArgb(context, tracking.primaryTrack?.id ?: -1L)
            )
            val elapsed = tracking.elapsedMs(System.currentTimeMillis())
            if (tracking.isPaused) {
                // Two buttons leave ~50dp of text at 2x2: say "paused" rather than
                // squeeze in the frozen time (the 1x1 widget and the app show it).
                views.setChronometer(R.id.hdr_chrono, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.hdr_chrono, View.GONE)
                views.setTextViewText(R.id.hdr_sub, "已暂停")
                views.setViewVisibility(R.id.hdr_sub, View.VISIBLE)
                views.setInt(R.id.hdr_dot, "setColorFilter", ContextCompat.getColor(context, R.color.widget_on_bg_variant))
            } else {
                views.setChronometer(R.id.hdr_chrono, SystemClock.elapsedRealtime() - elapsed, null, true)
                views.setViewVisibility(R.id.hdr_chrono, View.VISIBLE)
                views.setViewVisibility(R.id.hdr_sub, View.GONE)
            }

            views.setViewVisibility(R.id.hdr_quick, View.GONE)
            views.setViewVisibility(R.id.hdr_pause, View.VISIBLE)
            views.setImageViewResource(
                R.id.hdr_pause,
                if (tracking.isPaused) R.drawable.ic_widget_play else R.drawable.ic_widget_pause
            )
            views.setInt(R.id.hdr_pause, "setColorFilter", onBg)
            views.setOnClickPendingIntent(
                R.id.hdr_pause,
                TimerCommands.broadcastIntent(context, REQ_TOGGLE, TimerActionReceiver.ACTION_TOGGLE_PAUSE)
            )
            views.setViewVisibility(R.id.hdr_stop, View.VISIBLE)
            views.setInt(R.id.hdr_stop, "setColorFilter", onAccent)
            views.setOnClickPendingIntent(
                R.id.hdr_stop,
                if (TimerCommands.needsInAppStop(tracking)) {
                    TimerCommands.stopInAppIntent(context, REQ_STOP)
                } else {
                    TimerCommands.broadcastIntent(context, REQ_STOP, TimerActionReceiver.ACTION_STOP)
                }
            )
        }

        private fun bindSlots(
            context: Context,
            views: RemoteViews,
            tracking: TrackingSession,
            shown: List<TrackRef>
        ) {
            if (shown.isEmpty()) {
                views.setViewVisibility(R.id.grid, View.GONE)
                views.setViewVisibility(R.id.empty, View.VISIBLE)
                return
            }
            views.setViewVisibility(R.id.grid, View.VISIBLE)
            views.setViewVisibility(R.id.empty, View.GONE)
            views.setViewVisibility(R.id.row1, if (shown.size > 2) View.VISIBLE else View.GONE)

            val runningIds = if (tracking.isActive) tracking.tracks.map { it.id }.toSet() else emptySet()
            for (i in 0 until SLOTS) {
                val ref = shown.getOrNull(i)
                if (ref == null) {
                    views.setViewVisibility(slotIds[i], View.INVISIBLE)
                    continue
                }
                views.setViewVisibility(slotIds[i], View.VISIBLE)
                views.setTextViewText(slotNameIds[i], ref.name)
                views.setInt(slotDotIds[i], "setColorFilter", trackColorArgb(context, ref.id))
                views.setInt(
                    slotIds[i],
                    "setBackgroundResource",
                    if (ref.id in runningIds) R.drawable.widget_chip_active else R.drawable.widget_chip
                )
                views.setOnClickPendingIntent(
                    slotIds[i],
                    TimerCommands.pickTrackIntent(context, REQ_PICK_BASE + i, ref, tracking)
                )
            }
        }
    }
}
