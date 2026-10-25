package com.mokie.timelogdemo.tracking

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.mokie.timelogdemo.MainActivity
import com.mokie.timelogdemo.TimeLogApplication

/** Timer actions triggered from outside the app UI: notification, widget, Quick Settings tile. */
object TimerCommands {

    /**
     * Stopping a theme-less or multi-theme session needs a choice (pick a theme,
     * split the time), so it has to go through the app.
     */
    fun needsInAppStop(tracking: TrackingSession): Boolean =
        tracking.isActive && tracking.tracks.size != 1

    /** Commit a single-theme session. No-op if [needsInAppStop] or nothing is running. */
    suspend fun stopSingleTrack(app: TimeLogApplication) {
        val snap = app.tracking.snapshotForStop() ?: return
        if (snap.tracks.size != 1) return
        val ok = app.repository.commitTimer(
            snap,
            mapOf(snap.tracks.single().id to snap.effectiveDurationMs)
        )
        if (!ok) Toast.makeText(app, "保存失败，计时仍在继续", Toast.LENGTH_LONG).show()
    }

    /** Opens the app straight into the stop flow. */
    fun stopInAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_STOP_REQUEST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun broadcastIntent(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TimerActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
