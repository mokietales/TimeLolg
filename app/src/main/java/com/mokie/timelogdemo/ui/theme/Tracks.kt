package com.mokie.timelogdemo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/**
 * Deterministic identity colour for a track, drawn from the categorical palette.
 *
 * Pure function of the track id (no schema column needed), so the same track is
 * always the same hue across the timeline, star map, mind map, chips, and
 * detail screen. id 0 / negative (e.g. theme-less "blank" sessions) fall back
 * to the neutral evergreen at index 0.
 */
@Composable
@ReadOnlyComposable
fun trackColor(id: Long): Color {
    val palette = if (isSystemInDarkTheme()) TrackPaletteDark else TrackPaletteLight
    if (id <= 0L) return palette[0]
    val index = (id.absoluteValue % palette.size).toInt()
    return palette[index]
}
