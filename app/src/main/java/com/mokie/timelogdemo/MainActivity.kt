package com.mokie.timelogdemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.mokie.timelogdemo.ui.TimeLogApp
import com.mokie.timelogdemo.ui.theme.TimeLogTheme

class MainActivity : ComponentActivity() {

    /** Set when the notification's Stop needs the in-app flow (theme pick or time split). */
    private val stopRequested = mutableStateOf(false)

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
                    stopRequested = stopRequested.value,
                    onStopRequestHandled = { stopRequested.value = false },
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
        if (intent?.action == ACTION_STOP_REQUEST) stopRequested.value = true
    }

    companion object {
        const val ACTION_STOP_REQUEST = "com.mokie.timelogdemo.action.STOP_REQUEST"
    }
}
