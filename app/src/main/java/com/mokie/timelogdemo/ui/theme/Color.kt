package com.mokie.timelogdemo.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Warm-minimal palette (redesign 2026-06).
//
// Direction: keep the Linear/Apple restraint, but trade the clinical pure
// white/black + indigo for a warmer paper-and-ink neutral with a single
// evergreen accent that reads as "focus / go". Categorical track colours
// (see Tracks.kt) carry data identity so the monochrome dots stop being
// indistinguishable.
// ─────────────────────────────────────────────────────────────────────────────

// Light — warm paper, warm ink.
val LightBackground = Color(0xFFFBFAF7)        // warm paper
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0EEE8)    // grouped background (warm)
val LightSurfaceContainer = Color(0xFFF5F3EE)  // inset card / subtle row
val LightSurfaceContainerHigh = Color(0xFFEBE8E1)
val LightOnBackground = Color(0xFF1A1A17)      // warm near-black
val LightOnSurface = Color(0xFF1A1A17)
val LightOnSurfaceVariant = Color(0xFF6B6A63)  // warm gray 1
val LightOnSurfaceMuted = Color(0xFF8E8C84)    // warm gray 2
val LightOutline = Color(0xFFE6E3DB)           // hairline separator
val LightOutlineVariant = Color(0xFFF0EEE8)

// Dark — warm near-black for OLED, warm grays.
val DarkBackground = Color(0xFF0C0C0B)
val DarkSurface = Color(0xFF0C0C0B)
val DarkSurfaceVariant = Color(0xFF1B1B19)
val DarkSurfaceContainer = Color(0xFF151513)
val DarkSurfaceContainerHigh = Color(0xFF232320)
val DarkOnBackground = Color(0xFFF5F3EE)
val DarkOnSurface = Color(0xFFF5F3EE)
val DarkOnSurfaceVariant = Color(0xFF9C9A92)
val DarkOnSurfaceMuted = Color(0xFF6F6D66)
val DarkOutline = Color(0xFF2B2B27)
val DarkOutlineVariant = Color(0xFF1B1B19)

// Accent — evergreen, the single highlight colour.
val AccentLight = Color(0xFF1C8C5A)
val AccentDark = Color(0xFF3DD68C)
val AccentContainerLight = Color(0xFFD7F0E2)
val OnAccentContainerLight = Color(0xFF0C5436)
val AccentContainerDark = Color(0xFF143A2A)
val OnAccentContainerDark = Color(0xFF7DEBB4)

// Secondary semantic (rarely used).
val WarnLight = Color(0xFFB25E00)
val WarnDark = Color(0xFFE0A878)

// ─────────────────────────────────────────────────────────────────────────────
// Categorical track palette — muted, paper-friendly hues. Indexed by track id.
// Order is tuned so adjacent ids stay visually distinct.
// ─────────────────────────────────────────────────────────────────────────────
val TrackPaletteLight = listOf(
    Color(0xFF1C8C5A), // evergreen
    Color(0xFF3A6FF0), // blue
    Color(0xFF8B5CF6), // violet
    Color(0xFFD98A00), // amber
    Color(0xFFE05A78), // rose
    Color(0xFF0E9AA8), // teal
    Color(0xFFDB6A3A), // orange
    Color(0xFF5C6BC0), // indigo
)

val TrackPaletteDark = listOf(
    Color(0xFF3DD68C), // evergreen
    Color(0xFF6E9BFF), // blue
    Color(0xFFB69CFF), // violet
    Color(0xFFF0B23A), // amber
    Color(0xFFF286A0), // rose
    Color(0xFF49C7D4), // teal
    Color(0xFFF09267), // orange
    Color(0xFF93A0EC), // indigo
)
