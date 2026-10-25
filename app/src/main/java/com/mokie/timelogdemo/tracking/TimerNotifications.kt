package com.mokie.timelogdemo.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mokie.timelogdemo.MainActivity
import com.mokie.timelogdemo.R
import com.mokie.timelogdemo.ui.util.TimeFormat

object TimerNotifications {
    const val CHANNEL_ID = "timer"
    const val NOTIFICATION_ID = 1001

    private const val REQ_OPEN = 1
    private const val REQ_TOGGLE = 2
    private const val REQ_STOP = 3

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "计时",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "正在进行的计时"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, session: TrackingSession): Notification {
        val now = System.currentTimeMillis()
        val elapsed = session.elapsedMs(now)
        val tracks = session.tracks
        val title = when {
            tracks.isEmpty() -> "计时中 · 未选主题"
            tracks.size == 1 -> tracks.single().name
            else -> "${tracks.first().name} +${tracks.size - 1}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(activityIntent(context, REQ_OPEN, action = null))

        if (session.isPaused) {
            builder
                .setShowWhen(false)
                .setContentText("已暂停 · 已计 ${TimeFormat.duration(elapsed)}")
        } else {
            val startMs = session.startMs ?: now
            builder
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(now - elapsed)
                .setContentText("${TimeFormat.hhmm(startMs)} 开始")
        }

        builder.addAction(
            R.drawable.ic_stat_timer,
            if (session.isPaused) "继续" else "暂停",
            broadcastIntent(context, REQ_TOGGLE, TimerActionReceiver.ACTION_TOGGLE_PAUSE)
        )
        // A single-track session can be committed straight from the notification;
        // anything that needs a choice (no theme yet, or splitting time across
        // several) opens the app into the stop flow instead.
        val stopIntent = if (tracks.size == 1) {
            broadcastIntent(context, REQ_STOP, TimerActionReceiver.ACTION_STOP)
        } else {
            activityIntent(context, REQ_STOP, action = MainActivity.ACTION_STOP_REQUEST)
        }
        builder.addAction(R.drawable.ic_stat_timer, "停止", stopIntent)

        return builder.build()
    }

    private fun broadcastIntent(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TimerActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun activityIntent(context: Context, requestCode: Int, action: String?): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(action ?: Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
