package com.mokie.timelogdemo.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/**
 * Start of the current local day, updating when the date rolls over.
 * Re-checks once a minute rather than sleeping until midnight, because coroutine
 * delays don't advance while the device is in deep sleep.
 */
@Composable
fun rememberDayStart(): Long {
    val state = produceState(TimeFormat.startOfDayMs(System.currentTimeMillis())) {
        while (true) {
            delay(60_000L)
            val today = TimeFormat.startOfDayMs(System.currentTimeMillis())
            if (today != value) value = today
        }
    }
    return state.value
}
