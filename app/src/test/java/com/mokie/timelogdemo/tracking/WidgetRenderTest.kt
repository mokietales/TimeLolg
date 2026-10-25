package com.mokie.timelogdemo.tracking

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.mokie.timelogdemo.R
import com.mokie.timelogdemo.tracking.QuickWidgetProvider.Shape
import com.mokie.timelogdemo.tracking.TimerCommands.trackExtra
import com.mokie.timelogdemo.tracking.TrackingSession.TrackRef
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
    private val gym = TrackRef(4, "gym")
    private val code = TrackRef(5, "code")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tracking = TrackingSession(context).also { it.clear() }
    }

    private fun tracksWidget(
        recent: List<TrackRef> = listOf(work, play, read),
        rows: Int = 2,
        columns: Int = 2
    ): View = TimerWidgetProvider.buildViews(context, tracking, recent, rows, columns)
        .apply(context, FrameLayout(context))

    private fun quickWidget(shape: Shape): View =
        QuickWidgetProvider.buildViews(context, tracking, shape).apply(context, FrameLayout(context))

    private fun View.visibility(id: Int) = findViewById<View>(id).visibility
    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    // ----- Theme widget -----

    @Test
    fun tracks_idleShowsQuickStartAndThemes() {
        val v = tracksWidget()
        assertEquals("点主题开始", v.text(R.id.hdr_sub))
        assertEquals(View.VISIBLE, v.visibility(R.id.hdr_quick))
        assertEquals(View.GONE, v.visibility(R.id.hdr_pause))
        assertEquals(View.GONE, v.visibility(R.id.hdr_stop))
        assertEquals("work", v.text(R.id.slot0_name))
        assertEquals("read", v.text(R.id.slot2_name))
        assertEquals(View.VISIBLE, v.visibility(R.id.row1))
        assertEquals(View.INVISIBLE, v.visibility(R.id.slot3))
        assertEquals(View.GONE, v.visibility(R.id.row2))
    }

    @Test
    fun tracks_rowsFollowWidgetHeightButNeverExceedThemes() {
        val five = listOf(work, play, read, gym, code)
        assertEquals(View.GONE, tracksWidget(five, rows = 1).visibility(R.id.row1))
        val three = tracksWidget(five, rows = 3)
        assertEquals(View.VISIBLE, three.visibility(R.id.row2))
        assertEquals("code", three.text(R.id.slot4_name))
        // Two themes in a tall widget stack full-width instead of one short row.
        val two = tracksWidget(listOf(work, play), rows = 4)
        assertEquals("work", two.text(R.id.slot0_name))
        assertEquals("play", two.text(R.id.slot2_name))
        assertEquals(View.GONE, two.visibility(R.id.slot1))
        assertEquals(View.VISIBLE, two.visibility(R.id.row1))
        assertEquals(View.GONE, two.visibility(R.id.row2))
    }

    @Test
    fun tracks_singleColumnFillsTheLeftSlotOfEachRow() {
        val v = tracksWidget(listOf(work, play, read, gym), rows = 3, columns = 1)
        assertEquals("work", v.text(R.id.slot0_name))
        assertEquals("play", v.text(R.id.slot2_name))
        assertEquals("read", v.text(R.id.slot4_name))
        assertEquals(View.GONE, v.visibility(R.id.slot1))
        assertEquals(View.GONE, v.visibility(R.id.slot3))
        assertEquals(View.GONE, v.visibility(R.id.row3))
    }

    @Test
    fun tracks_sizeVariantsDoNotShareThemeTapTargets() {
        val recent = listOf(work, play, read, gym)
        // Slot 2 shows "play" in one column but "read" in two columns.
        val oneColumn = TimerWidgetProvider.buildViews(context, tracking, recent, rows = 2, columns = 1)
        TimerWidgetProvider.buildViews(context, tracking, recent, rows = 2, columns = 2)
        oneColumn.apply(context, FrameLayout(context)).findViewById<View>(R.id.slot2).performClick()
        val sent = shadowOf(context as Application).broadcastIntents.last()
        assertEquals(play, sent.trackExtra())
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
        assertEquals(View.GONE, v.visibility(R.id.hint))
    }

    @Test
    fun tracks_pausedSaysSo() {
        tracking.start(play)
        tracking.togglePause()
        val v = tracksWidget()
        assertEquals("play", v.text(R.id.hdr_title))
        assertEquals(View.GONE, v.visibility(R.id.hdr_chrono))
        assertEquals("已暂停", v.text(R.id.hdr_sub))
    }

    @Test
    fun tracks_blankSessionShowsHintAndGivesUpARow() {
        tracking.startBlank()
        val v = tracksWidget(listOf(work, play, read, gym), rows = 2)
        assertEquals("未选主题", v.text(R.id.hdr_title))
        assertEquals(View.VISIBLE, v.visibility(R.id.hint))
        assertEquals(View.GONE, v.visibility(R.id.row1))
    }

    @Test
    fun tracks_noThemesShowsHint() {
        val v = tracksWidget(recent = emptyList())
        assertEquals(View.GONE, v.visibility(R.id.grid))
        assertEquals(View.VISIBLE, v.visibility(R.id.empty))
    }

    // ----- Quick widget -----

    @Test
    fun quickCompact_hasStopWhileTiming() {
        var v = quickWidget(Shape.Compact)
        assertEquals("开始", v.text(R.id.quick_label))
        assertEquals(View.GONE, v.visibility(R.id.quick_chrono))
        assertEquals(View.GONE, v.visibility(R.id.quick_frozen))
        assertEquals(View.GONE, v.visibility(R.id.quick_stop))

        tracking.startBlank()
        v = quickWidget(Shape.Compact)
        assertEquals(View.GONE, v.visibility(R.id.quick_label))
        assertEquals(View.VISIBLE, v.visibility(R.id.quick_chrono))
        assertEquals(View.GONE, v.visibility(R.id.quick_frozen))
        assertEquals(View.VISIBLE, v.visibility(R.id.quick_stop))

        tracking.togglePause()
        v = quickWidget(Shape.Compact)
        assertEquals(View.GONE, v.visibility(R.id.quick_label))
        assertEquals(View.GONE, v.visibility(R.id.quick_chrono))
        assertEquals(View.VISIBLE, v.visibility(R.id.quick_frozen))
        assertEquals(View.VISIBLE, v.visibility(R.id.quick_stop))
    }

    @Test
    fun quickTallAndWide_offerStopOnlyWhileTiming() {
        for (shape in listOf(Shape.Tall, Shape.Wide)) {
            tracking.clear()
            var v = quickWidget(shape)
            assertEquals(shape.name, View.GONE, v.visibility(R.id.quick_stop))
            assertEquals(shape.name, "开始", v.text(R.id.quick_label))

            tracking.start(play)
            v = quickWidget(shape)
            assertEquals(shape.name, View.VISIBLE, v.visibility(R.id.quick_stop))
            assertEquals(shape.name, View.VISIBLE, v.visibility(R.id.quick_chrono))
            assertEquals(shape.name, View.GONE, v.visibility(R.id.quick_frozen))
            assertEquals(shape.name, View.GONE, v.visibility(R.id.quick_label))

            tracking.togglePause()
            v = quickWidget(shape)
            if (shape == Shape.Tall) assertEquals("已暂停", v.text(R.id.quick_label))
            else assertEquals(shape.name, View.GONE, v.visibility(R.id.quick_label))
            assertEquals(shape.name, View.GONE, v.visibility(R.id.quick_chrono))
            assertEquals(shape.name, View.VISIBLE, v.visibility(R.id.quick_frozen))
            assertEquals(shape.name, View.VISIBLE, v.visibility(R.id.quick_stop))
        }
    }
}
