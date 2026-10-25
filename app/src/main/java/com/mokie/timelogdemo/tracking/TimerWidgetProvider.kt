package com.mokie.timelogdemo.tracking

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.mokie.timelogdemo.R
import com.mokie.timelogdemo.timeLogApp
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef
import com.mokie.timelogdemo.ui.theme.trackColorArgb

/**
 * Theme widget: the running timer on top (pause / stop, or a blank quick start when
 * idle) and the most recent themes below as fixed-height buttons, each one tap away
 * from timing. The number of theme rows follows the widget's height.
 * Pushed by WidgetUpdater; the Chronometer ticks on its own.
 */
class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        context.timeLogApp.widgetUpdater.refresh()
    }

    companion object {
        const val MAX_ROWS = 4
        const val MAX_COLUMNS = 2
        const val MAX_SLOTS = MAX_ROWS * MAX_COLUMNS

        /** Below this width (dp) two columns truncate names to a couple of letters. */
        private const val TWO_COLUMN_MIN_WIDTH = 180f

        private const val REQ_OPEN = 20
        private const val REQ_START = 21
        private const val REQ_TOGGLE = 22
        private const val REQ_STOP = 23
        private const val REQ_PICK_BASE = 1_000

        private val rowIds = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3)
        private val slotIds = intArrayOf(
            R.id.slot0, R.id.slot1, R.id.slot2, R.id.slot3,
            R.id.slot4, R.id.slot5, R.id.slot6, R.id.slot7
        )
        private val slotDotIds = intArrayOf(
            R.id.slot0_dot, R.id.slot1_dot, R.id.slot2_dot, R.id.slot3_dot,
            R.id.slot4_dot, R.id.slot5_dot, R.id.slot6_dot, R.id.slot7_dot
        )
        private val slotNameIds = intArrayOf(
            R.id.slot0_name, R.id.slot1_name, R.id.slot2_name, R.id.slot3_name,
            R.id.slot4_name, R.id.slot5_name, R.id.slot6_name, R.id.slot7_name
        )

        /** Minimum widget height (dp) that fits [rows] theme rows: padding + header + 46dp per row. */
        internal fun heightForRows(rows: Int): Float = 24f + 58f + 46f * rows

        fun updateAll(context: Context, tracking: TrackingSession, recent: List<TrackRef>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildResponsive(context, tracking, recent))
        }

        /**
         * One layout per (columns, rows); the launcher picks the largest that fits, so
         * a narrow widget gets a single readable column and a tall one more rows.
         */
        private fun buildResponsive(context: Context, tracking: TrackingSession, recent: List<TrackRef>): RemoteViews {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return buildViews(context, tracking, recent, rows = 3, columns = 1)
            }
            val variants = buildMap {
                for (rows in 1..MAX_ROWS) {
                    put(SizeF(110f, heightForRows(rows)), buildViews(context, tracking, recent, rows, 1))
                    put(
                        SizeF(TWO_COLUMN_MIN_WIDTH, heightForRows(rows)),
                        buildViews(context, tracking, recent, rows, 2)
                    )
                }
            }
            return RemoteViews(variants)
        }

        internal fun buildViews(
            context: Context,
            tracking: TrackingSession,
            recent: List<TrackRef>,
            rows: Int,
            columns: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tracks)
            bindHeader(context, views, tracking)
            val blank = tracking.isActive && tracking.tracks.isEmpty()
            views.setViewVisibility(R.id.hint, if (blank) View.VISIBLE else View.GONE)
            // The hint line takes about a row's height; drop a row rather than clip one.
            val rowCount = if (blank) (rows - 1).coerceAtLeast(1) else rows
            val shown = WidgetUpdater.shownTracks(recent, tracking.tracks, rowCount * columns)
            bindSlots(context, views, tracking, shown, rowCount, columns)
            return views
        }

        private fun bindHeader(context: Context, views: RemoteViews, tracking: TrackingSession) {
            val onBg = ContextCompat.getColor(context, R.color.widget_on_bg)
            val onAccent = ContextCompat.getColor(context, R.color.widget_on_accent)
            views.setOnClickPendingIntent(R.id.hdr, TimerCommands.openAppIntent(context, REQ_OPEN))

            if (!tracking.isActive) {
                views.setTextViewText(R.id.hdr_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.hdr_sub, "点主题开始")
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
                tracks.isEmpty() -> "未选主题"
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
                // At 2x2 the clock slot is ~45dp: say "paused" rather than squeeze
                // in the frozen time (the quick widget and the app show it).
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
            views.setOnClickPendingIntent(R.id.hdr_stop, TimerCommands.stopIntent(context, REQ_STOP, tracking))
        }

        private fun bindSlots(
            context: Context,
            views: RemoteViews,
            tracking: TrackingSession,
            shown: List<TrackRef>,
            rowCount: Int,
            columns: Int
        ) {
            if (shown.isEmpty()) {
                views.setViewVisibility(R.id.grid, View.GONE)
                views.setViewVisibility(R.id.empty, View.VISIBLE)
                return
            }
            views.setViewVisibility(R.id.grid, View.VISIBLE)
            views.setViewVisibility(R.id.empty, View.GONE)
            val usedRows = minOf(rowCount, (shown.size + columns - 1) / columns)
            rowIds.forEachIndexed { r, id ->
                views.setViewVisibility(id, if (r < usedRows) View.VISIBLE else View.GONE)
            }

            val runningIds = if (tracking.isActive) tracking.tracks.map { it.id }.toSet() else emptySet()
            for (i in 0 until MAX_SLOTS) {
                // Single column: themes fill the left slot of each row, right slots go away.
                val itemIndex = if (columns == 1) {
                    if (i % 2 == 1) {
                        views.setViewVisibility(slotIds[i], View.GONE)
                        continue
                    }
                    i / 2
                } else {
                    i
                }
                val ref = shown.getOrNull(itemIndex)
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
                // Keyed by theme, not slot: the size variants put different themes in
                // the same slot, and PendingIntents sharing a request code share extras.
                views.setOnClickPendingIntent(
                    slotIds[i],
                    TimerCommands.pickTrackIntent(context, REQ_PICK_BASE + ref.id.toInt(), ref, tracking)
                )
            }
        }
    }
}
