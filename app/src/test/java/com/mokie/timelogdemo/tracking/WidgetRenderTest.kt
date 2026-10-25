package com.mokie.timelogdemo.tracking

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.mokie.timelogdemo.R
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Inflates the widget RemoteViews for each timer state and checks what the user would see. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WidgetRenderTest {

    private lateinit var context: Context
    private lateinit var tracking: TrackingSession
    private val work = TrackRef(1, "work")
    private val play = TrackRef(2, "play")
    private val read = TrackRef(3, "read")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tracking = TrackingSession(context).also { it.clear() }
    }

    private fun tracksWidget(shown: List<TrackRef> = listOf(work, play, read)): View =
        TimerWidgetProvider.buildViews(context, tracking, shown).apply(context, FrameLayout(context))

    private fun quickWidget(): View =
        QuickWidgetProvider.buildViews(context, tracking).apply(context, FrameLayout(context))

    private fun View.visibility(id: Int) = findViewById<View>(id).visibility
    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test
    fun tracks_idleShowsQuickStartAndThemes() {
        val v = tracksWidget()
        assertEquals("点主题开始计时", v.text(R.id.hdr_sub))
        assertEquals(View.VISIBLE, v.visibility(R.id.hdr_quick))
        assertEquals(View.GONE, v.visibility(R.id.hdr_pause))
        assertEquals(View.GONE, v.visibility(R.id.hdr_stop))
        assertEquals("work", v.text(R.id.slot0_name))
        assertEquals("read", v.text(R.id.slot2_name))
        assertEquals(View.VISIBLE, v.visibility(R.id.row1))
        assertEquals(View.INVISIBLE, v.visibility(R.id.slot3))
    }

    @Test
    fun tracks_runningShowsThemeTimerAndControls() {
        tracking.start(play)
        val v = tracksWidget()
        assertEquals("play", v.text(R.id.hdr_title))
        assertEquals(View.VISIBLE, v.visibility(R.id.hdr_chrono))
        assertEquals(View.VISIBLE, v.visibility(R.id.hdr_pause))
        assertEquals(View.VISIBLE, v.visibility(R.id.hdr_stop))
        assertEquals(View.GONE, v.visibility(R.id.hdr_quick))
    }

    @Test
    fun tracks_pausedAndBlankStatesAreLabelled() {
        tracking.start(play)
        tracking.togglePause()
        val paused = tracksWidget()
        assertEquals("play", paused.text(R.id.hdr_title))
        assertEquals(View.GONE, paused.visibility(R.id.hdr_chrono))
        assertEquals("已暂停", paused.text(R.id.hdr_sub))

        tracking.clear()
        tracking.startBlank()
        assertEquals("未选主题 · 点下方指定", tracksWidget().text(R.id.hdr_title))
    }

    @Test
    fun tracks_noThemesShowsHint() {
        val v = tracksWidget(shown = emptyList())
        assertEquals(View.GONE, v.visibility(R.id.grid))
        assertEquals(View.VISIBLE, v.visibility(R.id.empty))
    }

    @Test
    fun tracks_twoThemesCollapseTheSecondRow() {
        assertEquals(View.GONE, tracksWidget(shown = listOf(work, play)).visibility(R.id.row1))
    }

    @Test
    fun quick_cyclesStartPauseResume() {
        var v = quickWidget()
        assertEquals(View.VISIBLE, v.visibility(R.id.quick_label))
        assertEquals(View.GONE, v.visibility(R.id.quick_chrono))

        tracking.startBlank()
        v = quickWidget()
        assertEquals(View.GONE, v.visibility(R.id.quick_label))
        assertEquals(View.VISIBLE, v.visibility(R.id.quick_chrono))

        tracking.togglePause()
        v = quickWidget()
        assertEquals(View.VISIBLE, v.visibility(R.id.quick_chrono))
    }
}
