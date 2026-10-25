package com.mokie.timelogdemo.tracking

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mokie.timelogdemo.MainActivity
import com.mokie.timelogdemo.timeLogApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: tap to start a blank timer, tap again to stop. Stopping a
 * theme-less or multi-theme session opens the app's stop flow.
 */
class TimerTileService : TileService() {

    private var listening: Job? = null

    // While the panel is open the tile is already listening, so requestListeningState
    // won't re-deliver onStartListening; follow timer changes directly instead.
    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = timeLogApp.appScope.launch {
            timeLogApp.tracking.changes.collect { refresh() }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val app = timeLogApp
        val tracking = app.tracking
        when {
            !tracking.isActive -> tracking.startBlank()
            TimerCommands.needsInAppStop(tracking) -> openStopFlow()
            else -> app.appScope.launch { TimerCommands.stopSingleTrack(app) }
        }
        refresh()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openStopFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(TimerCommands.stopInAppIntent(this, REQ_STOP))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_STOP_REQUEST)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val tracking = timeLogApp.tracking
        tile.state = if (tracking.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !tracking.isActive -> "点按开始"
                tracking.isPaused -> "已暂停"
                tracking.tracks.isEmpty() -> "计时中"
                else -> tracking.tracks.first().name
            }
        }
        tile.updateTile()
    }

    companion object {
        private const val REQ_STOP = 30

        /** Ask the system to rebind so the tile reflects the latest timer state. */
        fun requestRefresh(context: Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, TimerTileService::class.java)
            )
        }
    }
}
