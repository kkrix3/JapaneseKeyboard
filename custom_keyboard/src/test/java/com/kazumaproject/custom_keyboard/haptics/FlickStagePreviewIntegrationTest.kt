package com.kazumaproject.custom_keyboard.haptics

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/** Exercises view routing as well as the recognizers: a coordinator-only test cannot
 * catch cancellation placed after the independent multi-touch early return. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class FlickStagePreviewIntegrationTest {
    private class Harness(independent: Boolean, stage: Boolean = true, master: Boolean = true) : AutoCloseable {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val context = ContextThemeWrapper(activity.get(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val raw = PreferenceManager.getDefaultSharedPreferences(context)
        val stages = mutableListOf<HapticPatternKind>()
        val commits = mutableListOf<KeyAction>()
        val releaseContexts = mutableListOf<InputHapticContext>()
        val feedback = FlickStageHapticFeedback(context, play = { stages += it })
        val view = FlickKeyboardView(context)
        val downTime = SystemClock.uptimeMillis()
        init {
            raw.edit().clear().putBoolean(CustomHapticPreferences.KEY_STAGE_ENABLED, stage)
                .putBoolean(CustomHapticPreferences.KEY_VIBRATION_ENABLED, master)
                .putBoolean(CustomHapticPreferences.KEY_STAGE_SUPPRESSION, false)
                .putInt(CustomHapticPreferences.KEY_STAGE_DELAY_MS, 200).commit()
            // Replace only the output sink. All routing and recognizer code is real.
            FlickKeyboardView::class.java.getDeclaredField("stageHaptics\$delegate").apply {
                isAccessible = true
            }.set(view, lazyOf(feedback))
            view.setIndependentMultiTouchEnabled(independent)
            view.setLongPressTimeout(1000)
            view.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
                override fun onPress(action: KeyAction) = Unit
                override fun onAction(action: KeyAction, isFlick: Boolean) { commits += action }
                override fun onCommittedAction(action: KeyAction, isFlick: Boolean, hapticContext: InputHapticContext) {
                    commits += action; releaseContexts += hapticContext
                }
                override fun onActionLongPress(action: KeyAction) = Unit
                override fun onActionUpAfterLongPress(action: KeyAction) = Unit
                override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
                override fun onFlickActionLongPress(action: KeyAction) = Unit
                override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
            })
            view.setKeyboard(KeyboardLayout(
                keys = listOf("a", "b").mapIndexed { index, id ->
                    KeyData(id, 0, index, true, KeyAction.Text(id), keyId = id, keyType = KeyType.CROSS_FLICK)
                },
                flickKeyMaps = listOf("a", "b").associateWith { id -> listOf(mapOf(
                    FlickDirection.TAP to FlickAction.Input(id),
                    FlickDirection.UP to FlickAction.Input("$id-up"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("$id-right"))) },
                columnCount = 2, rowCount = 1))
            activity.get().setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 600, 300)
        }
        fun point(key: Int = 0, dx: Float = 0f, dy: Float = 0f): Pair<Float, Float> {
            val child = view.getChildAt(key)
            return child.left + child.width / 2f + dx to child.top + child.height / 2f + dy
        }
        fun send(action: Int, points: List<Pair<Float, Float>> = listOf(point()), index: Int = 0) {
            val props = points.indices.map { id -> MotionEvent.PointerProperties().apply {
                this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }.toTypedArray()
            val coords = points.map { (px, py) -> MotionEvent.PointerCoords().apply {
                x = px; y = py; pressure = 1f; size = 1f
            } }.toTypedArray()
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), points.size,
                props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { view.onTouchEvent(event) } finally { event.recycle() }
        }
        fun reserve() {
            send(MotionEvent.ACTION_DOWN)
            send(MotionEvent.ACTION_MOVE, listOf(point(dy = -120f)))
            send(MotionEvent.ACTION_MOVE, listOf(point(dx = 120f)))
            assertEquals(listOf(HapticPatternKind.STAGE_ADVANCE, HapticPatternKind.STAGE_RETREAT), stages)
        }
        override fun close() { feedback.cancel(); activity.pause().stop().destroy() }
    }
    private fun tick() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(201))

    @Test fun secondPointerOutsideKeysCancelsReservationBeforeIndependentEarlyReturn() {
        for (independent in listOf(false, true)) Harness(independent).use { h ->
            h.reserve()
            h.send(MotionEvent.ACTION_POINTER_DOWN, listOf(h.point(dx = 120f), -100f to -100f), 1)
            tick()
            assertEquals("independent=$independent", 2, h.stages.size)
            h.send(MotionEvent.ACTION_CANCEL)
        }
    }

    @Test fun secondPointerOnAlreadyOwnedKeyAlsoCancelsReservation() {
        Harness(true).use { h ->
            h.reserve()
            h.send(MotionEvent.ACTION_POINTER_DOWN, listOf(h.point(dx = 120f), h.point()), 1)
            tick()
            assertEquals(2, h.stages.size)
            assertTrue(h.commits.isEmpty())
            h.send(MotionEvent.ACTION_CANCEL)
        }
    }

    @Test fun olderPointerReleaseDoesNotCancelNewKeysDelayedAdvance() {
        Harness(true).use { h ->
            h.reserve()
            val first = h.point(dx = 120f)
            h.send(MotionEvent.ACTION_POINTER_DOWN, listOf(first, h.point(1)), 1)
            h.send(MotionEvent.ACTION_MOVE, listOf(first, h.point(1, dy = -120f)))
            h.send(MotionEvent.ACTION_MOVE, listOf(first, h.point(1, dx = 120f)))
            assertEquals(4, h.stages.size)
            h.send(MotionEvent.ACTION_POINTER_UP, listOf(first, h.point(1, dx = 120f)), 0)
            tick()
            assertEquals(HapticPatternKind.STAGE_ADVANCE, h.stages.last())
            assertEquals(5, h.stages.size)
            assertEquals(listOf(KeyAction.Text("a-right")), h.commits)
            h.send(MotionEvent.ACTION_CANCEL)
        }
    }

    @Test fun releaseAndCancelDiscardDelayedAdvanceInBothRoutingModes() {
        for (independent in listOf(false, true)) for (end in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) {
            Harness(independent).use { h ->
                h.reserve()
                h.send(end, listOf(h.point(dx = 120f)))
                tick()
                assertEquals(2, h.stages.size)
                assertEquals(if (end == MotionEvent.ACTION_UP) listOf(KeyAction.Text("a-right")) else emptyList<KeyAction>(), h.commits)
            }
        }
    }

    @Test fun hideAndDetachCancelPendingPlaybackAndRebindNextGesture() {
        for (detach in listOf(false, true)) Harness(true).use { h ->
            h.reserve()
            if (detach) (h.view.parent as android.view.ViewGroup).removeView(h.view)
            else h.view.visibility = View.GONE
            tick()
            assertEquals(2, h.stages.size)
            assertTrue(h.commits.isEmpty())
            if (detach) h.activity.get().setContentView(h.view) else h.view.visibility = View.VISIBLE
            h.send(MotionEvent.ACTION_DOWN)
            h.send(MotionEvent.ACTION_MOVE, listOf(h.point(dy = -120f)))
            assertEquals(3, h.stages.size)
            h.send(MotionEvent.ACTION_UP, listOf(h.point(dy = -120f)))
            assertEquals(listOf(KeyAction.Text("a-up")), h.commits)
        }
    }

    @Test fun disabledStageOrMasterPreservesInputAndReleaseContext() {
        for (independent in listOf(false, true)) for ((stage, master) in listOf(false to true, true to false, true to true)) {
            Harness(independent, stage, master).use { h ->
                h.send(MotionEvent.ACTION_DOWN)
                h.send(MotionEvent.ACTION_MOVE, listOf(h.point(dy = -120f)))
                assertTrue(h.commits.isEmpty())
                h.send(MotionEvent.ACTION_UP, listOf(h.point(dy = -120f)))
                assertEquals(listOf(KeyAction.Text("a-up")), h.commits)
                assertEquals(1, h.releaseContexts.size)
                assertFalse(h.releaseContexts.single().isTwoStepFlick)
                assertEquals(if (stage && master) 1 else 0, h.stages.size)
            }
        }
    }
}
