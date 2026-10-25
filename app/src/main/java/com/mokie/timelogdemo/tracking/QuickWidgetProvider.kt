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
import java.util.Locale
import androidx.core.content.ContextCompat
import com.mokie.timelogdemo.R
import com.mokie.timelogdemo.timeLogApp

/**
 * Quick timer widget. Default 1x2: start / pause / resume, the clock, and stop.
 * Resized to 2x1 it lays the buttons side by side. A cell that is still only one
 * row tall keeps the same three controls, packed tighter — stop is always there.
 */
class QuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        context.timeLogApp.widgetUpdater.refresh()
    }

    enum class Shape(val layout: Int) {
        Compact(R.layout.widget_quick),
        Tall(R.layout.widget_quick_tall),
        Wide(R.layout.widget_quick_wide)
    }

    companion object {
        private const val REQ_TOGGLE = 60
        private const val REQ_STOP = 61

        /** Same shape as a Chronometer: 00:09, 02:42, 1:05:03. */
        internal fun frozenClock(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s)
            else "%02d:%02d".format(Locale.ROOT, m, s)
        }

        fun updateAll(context: Context, tracking: TrackingSession) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildResponsive(context, tracking))
        }

        /** The launcher picks the largest variant that fits the widget's current size. */
        private fun buildResponsive(context: Context, tracking: TrackingSession): RemoteViews {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return buildViews(context, tracking, Shape.Tall)
            }
            return RemoteViews(
                mapOf(
                    SizeF(40f, 40f) to buildViews(context, tracking, Shape.Compact),
                    SizeF(40f, 150f) to buildViews(context, tracking, Shape.Tall),
                    SizeF(120f, 90f) to buildViews(context, tracking, Shape.Wide)
                )
            )
        }

        internal fun buildViews(context: Context, tracking: TrackingSession, shape: Shape): RemoteViews {
            val views = RemoteViews(context.packageName, shape.layout)
            val onAccent = ContextCompat.getColor(context, R.color.widget_on_accent)
            val onBg = ContextCompat.getColor(context, R.color.widget_on_bg)
            val onBgVariant = ContextCompat.getColor(context, R.color.widget_on_bg_variant)
            val active = tracking.isActive
            val running = active && !tracking.isPaused
            val toggleIntent =
                TimerCommands.broadcastIntent(context, REQ_TOGGLE, TimerActionReceiver.ACTION_QUICK_TOGGLE)

            if (running) {
                val elapsed = tracking.elapsedMs(System.currentTimeMillis())
                views.setChronometer(R.id.quick_chrono, SystemClock.elapsedRealtime() - elapsed, null, true)
                views.setViewVisibility(R.id.quick_chrono, View.VISIBLE)
                views.setViewVisibility(R.id.quick_frozen, View.GONE)
            } else if (active) {
                // A stopped Chronometer draws (now - base) when the host inflates it.
                // A size picked later — the 1x1 becoming 1x2 — would count the pause.
                views.setViewVisibility(R.id.quick_chrono, View.GONE)
                views.setTextViewText(R.id.quick_frozen, frozenClock(tracking.elapsedMs(System.currentTimeMillis())))
                views.setTextColor(R.id.quick_frozen, onBgVariant)
                views.setViewVisibility(R.id.quick_frozen, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.quick_chrono, View.GONE)
                views.setViewVisibility(R.id.quick_frozen, View.GONE)
            }

            views.setImageViewResource(
                R.id.quick_toggle,
                if (running) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            views.setInt(
                R.id.quick_toggle,
                "setBackgroundResource",
                if (running) R.drawable.widget_round_secondary else R.drawable.widget_round_primary
            )
            views.setInt(R.id.quick_toggle, "setColorFilter", if (running) onBg else onAccent)
            views.setOnClickPendingIntent(R.id.quick_toggle, toggleIntent)

            views.setTextColor(R.id.quick_chrono, if (running) onBg else onBgVariant)
            when {
                !active -> {
                    views.setTextViewText(R.id.quick_label, "开始")
                    views.setViewVisibility(R.id.quick_label, View.VISIBLE)
                }
                // Only the tall 1x2 has a spare line for "已暂停". Compact and wide
                // already show a dimmed clock and a play icon.
                tracking.isPaused && shape == Shape.Tall -> {
                    views.setTextViewText(R.id.quick_label, "已暂停")
                    views.setViewVisibility(R.id.quick_label, View.VISIBLE)
                }
                else -> views.setViewVisibility(R.id.quick_label, View.GONE)
            }

            views.setViewVisibility(R.id.quick_stop, if (active) View.VISIBLE else View.GONE)
            views.setInt(R.id.quick_stop, "setColorFilter", onBg)
            views.setOnClickPendingIntent(R.id.quick_stop, TimerCommands.stopIntent(context, REQ_STOP, tracking))
            return views
        }
    }
}
