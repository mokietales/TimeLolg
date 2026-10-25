package com.mokie.timelogdemo.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mokie.timelogdemo.timeLogApp
import kotlinx.coroutines.launch

/** Handles timer buttons on the ongoing notification and the home-screen widget. */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.timeLogApp
        when (intent.action) {
            ACTION_START_BLANK -> if (!app.tracking.isActive) app.tracking.startBlank()
            ACTION_TOGGLE_PAUSE -> app.tracking.togglePause()
            ACTION_STOP -> {
                val pending = goAsync()
                app.appScope.launch {
                    try {
                        TimerCommands.stopSingleTrack(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_START_BLANK = "com.mokie.timelogdemo.action.START_BLANK"
        const val ACTION_TOGGLE_PAUSE = "com.mokie.timelogdemo.action.TOGGLE_PAUSE"
        const val ACTION_STOP = "com.mokie.timelogdemo.action.STOP"
    }
}
