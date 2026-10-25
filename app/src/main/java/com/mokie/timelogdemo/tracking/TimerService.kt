package com.mokie.timelogdemo.tracking

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.mokie.timelogdemo.timeLogApp

/**
 * Foreground service whose only job is to own the ongoing timer notification, so it
 * can't be swiped away and the process stays prioritised while a session runs.
 * Timer state itself lives in TrackingSession (persisted), not here.
 */
class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tracking = timeLogApp.tracking
        // startForeground must be called for every startForegroundService, even if
        // the session ended in the meantime.
        val notification = TimerNotifications.build(this, tracking)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    TimerNotifications.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(TimerNotifications.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // e.g. a sticky restart while the app is in the background on Android 12+.
            Log.w("TimerService", "startForeground refused", e)
            running = false
            stopSelf()
            timeLogApp.timerController.postWithoutService()
            return START_NOT_STICKY
        }
        running = true
        if (!tracking.isActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running: Boolean = false
            private set
    }
}
