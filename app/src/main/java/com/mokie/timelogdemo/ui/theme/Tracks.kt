package com.mokie.timelogdemo.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.absoluteValue

/**
 * Deterministic identity colour for a track, drawn from the categorical palette.
 *
 * Pure function of the track id (no schema column needed), so the same track is
 * always the same hue across the timeline, star map, mind map, chips, widgets and
 * detail screen. id 0 / negative (e.g. theme-less "blank" sessions) fall back
 * to the neutral evergreen at index 0.
 */
@Composable
@ReadOnlyComposable
fun trackColor(id: Long): Color = pick(if (isSystemInDarkTheme()) TrackPaletteDark else TrackPaletteLight, id)

/** [trackColor] for code outside Compose, e.g. home-screen widgets. */
fun trackColorArgb(context: Context, id: Long): Int {
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    return pick(if (night) TrackPaletteDark else TrackPaletteLight, id).toArgb()
}

private fun pick(palette: List<Color>, id: Long): Color {
    if (id <= 0L) return palette[0]
    return palette[(id.absoluteValue % palette.size).toInt()]
}
