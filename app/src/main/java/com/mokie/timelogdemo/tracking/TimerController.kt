package com.mokie.timelogdemo.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Mirrors TrackingSession into every surface outside the app: the ongoing
 * notification (starting [TimerService] when a session begins and tearing it down
 * when it ends), the home-screen widget, and the Quick Settings tile.
 */
class TimerController(
    private val context: Context,
    private val tracking: TrackingSession
) {
    fun start(scope: CoroutineScope) {
        scope.launch { tracking.changes.collect { sync() } }
    }

    /** Re-post now, e.g. right after the notification permission is granted. */
    fun sync() {
        TimerWidgetProvider.updateAll(context, tracking)
        TimerTileService.requestRefresh(context)
        val manager = NotificationManagerCompat.from(context)
        if (!tracking.isActive) {
            context.stopService(Intent(context, TimerService::class.java))
            manager.cancel(TimerNotifications.NOTIFICATION_ID)
            return
        }
        if (TimerService.running) {
            post(manager)
            return
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
        } catch (e: Exception) {
            // Starting a foreground service from the background is restricted on
            // Android 12+. Fall back to a plain ongoing notification.
            Log.w("TimerController", "foreground service not allowed, posting plain notification", e)
            post(manager)
        }
    }

    /** Fallback when the service couldn't enter the foreground. */
    fun postWithoutService() {
        if (tracking.isActive) post(NotificationManagerCompat.from(context))
    }

    @SuppressLint("MissingPermission")
    private fun post(manager: NotificationManagerCompat) {
        if (!hasNotificationPermission(context)) return
        manager.notify(
            TimerNotifications.NOTIFICATION_ID,
            TimerNotifications.build(context, tracking)
        )
    }

    companion object {
        fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }
}
