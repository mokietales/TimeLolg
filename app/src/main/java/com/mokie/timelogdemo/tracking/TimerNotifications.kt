package com.mokie.timelogdemo.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
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
            .setContentIntent(TimerCommands.openAppIntent(context, REQ_OPEN))

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
            TimerCommands.broadcastIntent(context, REQ_TOGGLE, TimerActionReceiver.ACTION_TOGGLE_PAUSE)
        )
        val stopIntent = if (TimerCommands.needsInAppStop(session)) {
            TimerCommands.stopInAppIntent(context, REQ_STOP)
        } else {
            TimerCommands.broadcastIntent(context, REQ_STOP, TimerActionReceiver.ACTION_STOP)
        }
        builder.addAction(R.drawable.ic_stat_timer, "停止", stopIntent)

        return builder.build()
    }
}
