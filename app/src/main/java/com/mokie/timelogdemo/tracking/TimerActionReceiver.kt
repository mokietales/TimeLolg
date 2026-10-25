package com.mokie.timelogdemo.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.mokie.timelogdemo.timeLogApp
import kotlinx.coroutines.launch

/** Handles the pause/resume and stop buttons on the ongoing timer notification. */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.timeLogApp
        val tracking = app.tracking
        when (intent.action) {
            ACTION_TOGGLE_PAUSE -> tracking.togglePause()
            ACTION_STOP -> {
                val snap = tracking.snapshotForStop() ?: return
                if (snap.tracks.size != 1) return
                val pending = goAsync()
                app.appScope.launch {
                    try {
                        val ok = app.repository.commitTimer(
                            snap,
                            mapOf(snap.tracks.single().id to snap.effectiveDurationMs)
                        )
                        if (!ok) {
                            Toast.makeText(app, "保存失败，计时仍在继续", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_PAUSE = "com.mokie.timelogdemo.action.TOGGLE_PAUSE"
        const val ACTION_STOP = "com.mokie.timelogdemo.action.STOP"
    }
}
