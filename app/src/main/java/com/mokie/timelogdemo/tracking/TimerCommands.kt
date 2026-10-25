package com.mokie.timelogdemo.tracking

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.mokie.timelogdemo.MainActivity
import com.mokie.timelogdemo.TimeLogApplication
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef

/** Timer actions triggered from outside the app UI: notification, widgets, Quick Settings tile. */
object TimerCommands {

    /** What tapping a theme on a widget does, given what is running. */
    enum class PickAction {
        /** Nothing running: start timing this theme. */
        Start,
        /** A theme-less session is running: give it this theme, keep timing. */
        Assign,
        /** Already timing exactly this theme. */
        Ignore,
        /** Timing one other theme: save it, then start this one. */
        Switch,
        /** Timing several themes: the split needs the app's allocation dialog. */
        NeedsApp
    }

    fun pickAction(isActive: Boolean, runningTrackIds: List<Long>, pickedId: Long): PickAction = when {
        !isActive -> PickAction.Start
        runningTrackIds.isEmpty() -> PickAction.Assign
        runningTrackIds.size == 1 && runningTrackIds.single() == pickedId -> PickAction.Ignore
        runningTrackIds.size == 1 -> PickAction.Switch
        else -> PickAction.NeedsApp
    }

    private fun pickAction(tracking: TrackingSession, pickedId: Long) =
        pickAction(tracking.isActive, tracking.tracks.map { it.id }, pickedId)

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
        commit(app, snap, thenStart = null)
    }

    /** Apply a theme tap from a widget. [PickAction.NeedsApp] is routed by [pickTrackIntent] instead. */
    suspend fun pickTrack(app: TimeLogApplication, ref: TrackRef) {
        val tracking = app.tracking
        when (pickAction(tracking, ref.id)) {
            PickAction.Start -> tracking.start(ref)
            PickAction.Assign -> tracking.replaceTracks(listOf(ref))
            PickAction.Switch -> tracking.snapshotForStop()?.let { commit(app, it, thenStart = ref) }
            PickAction.Ignore, PickAction.NeedsApp -> Unit
        }
    }

    private suspend fun commit(app: TimeLogApplication, snap: TrackingSession.StopSnapshot, thenStart: TrackRef?) {
        val ok = app.repository.commitTimer(
            snap,
            mapOf(snap.tracks.single().id to snap.effectiveDurationMs),
            thenStart = thenStart
        )
        if (!ok) Toast.makeText(app, "保存失败，计时仍在继续", Toast.LENGTH_LONG).show()
    }

    /** Tap target for a theme on a widget, decided by what's running when the widget is drawn. */
    fun pickTrackIntent(
        context: Context,
        requestCode: Int,
        ref: TrackRef,
        tracking: TrackingSession
    ): PendingIntent =
        if (pickAction(tracking, ref.id) == PickAction.NeedsApp) {
            stopInAppIntent(context, requestCode, thenStart = ref)
        } else {
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, TimerActionReceiver::class.java)
                    .setAction(TimerActionReceiver.ACTION_PICK_TRACK)
                    .putTrack(ref),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

    /** Stop button target: commit in the background, or open the app when a choice is needed. */
    fun stopIntent(context: Context, requestCode: Int, tracking: TrackingSession): PendingIntent =
        if (needsInAppStop(tracking)) {
            stopInAppIntent(context, requestCode)
        } else {
            broadcastIntent(context, requestCode, TimerActionReceiver.ACTION_STOP)
        }

    /** Opens the app straight into the stop flow, optionally switching to [thenStart] afterwards. */
    fun stopInAppIntent(context: Context, requestCode: Int, thenStart: TrackRef? = null): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_STOP_REQUEST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .also { if (thenStart != null) it.putTrack(thenStart) },
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

    private const val EXTRA_TRACK_ID = "com.mokie.timelogdemo.extra.TRACK_ID"
    private const val EXTRA_TRACK_NAME = "com.mokie.timelogdemo.extra.TRACK_NAME"

    private fun Intent.putTrack(ref: TrackRef): Intent =
        putExtra(EXTRA_TRACK_ID, ref.id).putExtra(EXTRA_TRACK_NAME, ref.name)

    fun Intent.trackExtra(): TrackRef? {
        val id = getLongExtra(EXTRA_TRACK_ID, -1L).takeIf { it > 0L } ?: return null
        return TrackRef(id, getStringExtra(EXTRA_TRACK_NAME).orEmpty())
    }
}
