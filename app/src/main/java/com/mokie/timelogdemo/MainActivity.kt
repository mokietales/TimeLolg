package com.mokie.timelogdemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.mokie.timelogdemo.tracking.TimerCommands.trackExtra
import com.mokie.timelogdemo.ui.TimeLogApp
import com.mokie.timelogdemo.ui.now.StopRequest
import com.mokie.timelogdemo.ui.theme.TimeLogTheme

class MainActivity : ComponentActivity() {

    /**
     * Set when Stop (or a theme tap that switches) from the notification, a widget or
     * the tile needs the in-app flow: picking a theme or splitting time.
     */
    private val externalStopRequest = mutableStateOf<StopRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        val app = timeLogApp
        setContent {
            TimeLogTheme {
                TimeLogApp(
                    repository = app.repository,
                    session = app.tracking,
                    externalStopRequest = externalStopRequest.value,
                    onStopRequestHandled = { externalStopRequest.value = null },
                    onNotificationPermissionGranted = { app.timerController.sync() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Since Android 13 the user can swipe the timer notification away; bring it
        // back whenever the app is opened.
        timeLogApp.timerController.sync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_STOP_REQUEST) {
            externalStopRequest.value = StopRequest(thenStart = intent.trackExtra())
        }
    }

    companion object {
        const val ACTION_STOP_REQUEST = "com.mokie.timelogdemo.action.STOP_REQUEST"
    }
}
