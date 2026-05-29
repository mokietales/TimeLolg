package com.mokie.timelogdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mokie.timelogdemo.data.TimeLogDatabase
import com.mokie.timelogdemo.ui.TimeLogApp
import com.mokie.timelogdemo.ui.theme.TimeLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val db = TimeLogDatabase.getInstance(this)
        val trackDao = db.trackDao()
        val sessionDao = db.sessionDao()

        setContent {
            TimeLogTheme {
                TimeLogApp(trackDao = trackDao, sessionDao = sessionDao)
            }
        }
    }
}
