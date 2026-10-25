package com.mokie.timelogdemo

import android.app.Application
import android.content.Context
import com.mokie.timelogdemo.data.TimeLogDatabase
import com.mokie.timelogdemo.data.TimeLogRepository
import com.mokie.timelogdemo.tracking.TimerController
import com.mokie.timelogdemo.tracking.TimerNotifications
import com.mokie.timelogdemo.tracking.TrackingSession
import com.mokie.timelogdemo.tracking.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons shared by the UI, the timer service and notification actions. */
class TimeLogApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var tracking: TrackingSession
        private set
    lateinit var repository: TimeLogRepository
        private set
    lateinit var timerController: TimerController
        private set
    lateinit var widgetUpdater: WidgetUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        tracking = TrackingSession(this)
        repository = TimeLogRepository(TimeLogDatabase.getInstance(this), tracking, appScope)
        TimerNotifications.createChannel(this)
        timerController = TimerController(this, tracking).also { it.start(appScope) }
        widgetUpdater = WidgetUpdater(this, tracking, repository).also { it.start(appScope) }
    }
}

val Context.timeLogApp: TimeLogApplication
    get() = applicationContext as TimeLogApplication
