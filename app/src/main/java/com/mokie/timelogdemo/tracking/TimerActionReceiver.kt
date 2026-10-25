package com.mokie.timelogdemo.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mokie.timelogdemo.timeLogApp
import com.mokie.timelogdemo.tracking.TimerCommands.trackExtra
import kotlinx.coroutines.launch

/** Handles timer buttons on the ongoing notification and the home-screen widgets. */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.timeLogApp
        val tracking = app.tracking
        when (intent.action) {
            ACTION_START_BLANK -> if (!tracking.isActive) tracking.startBlank()
            ACTION_TOGGLE_PAUSE -> tracking.togglePause()
            ACTION_QUICK_TOGGLE -> if (tracking.isActive) tracking.togglePause() else tracking.startBlank()
            ACTION_STOP -> runAsync(app) { TimerCommands.stopSingleTrack(app) }
            ACTION_PICK_TRACK -> {
                val ref = intent.trackExtra() ?: return
                runAsync(app) { TimerCommands.pickTrack(app, ref) }
            }
        }
    }

    private fun runAsync(app: com.mokie.timelogdemo.TimeLogApplication, block: suspend () -> Unit) {
        val pending = goAsync()
        app.appScope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_START_BLANK = "com.mokie.timelogdemo.action.START_BLANK"
        const val ACTION_TOGGLE_PAUSE = "com.mokie.timelogdemo.action.TOGGLE_PAUSE"
        const val ACTION_QUICK_TOGGLE = "com.mokie.timelogdemo.action.QUICK_TOGGLE"
        const val ACTION_STOP = "com.mokie.timelogdemo.action.STOP"
        const val ACTION_PICK_TRACK = "com.mokie.timelogdemo.action.PICK_TRACK"
    }
}
