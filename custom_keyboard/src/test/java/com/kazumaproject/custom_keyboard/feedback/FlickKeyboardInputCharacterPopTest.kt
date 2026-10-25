package com.kazumaproject.custom_keyboard.feedback

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import com.kazumaproject.core.domain.flick.FlickTextPreviewEvent
import com.kazumaproject.core.domain.flick.FlickTextPreviewListener
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.haptics.InputHapticContext
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class FlickKeyboardInputCharacterPopTest {
    private class Harness(type: KeyType = KeyType.CROSS_FLICK, enabled: Boolean = true) : AutoCloseable {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val context = ContextThemeWrapper(activity.get(), com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val view = FlickKeyboardView(context)
        val actions = mutableListOf<KeyAction>()
        val previews = mutableListOf<FlickTextPreviewEvent>()
        var accept = true
        var defer = false
        var pending: (() -> Unit)? = null
        var atDispatch: (() -> Unit)? = null
        val effect: InputCharacterPop? get() = ReflectionHelpers.getField(view, "inputCharacterPop")
        val count: Int get() = effect?.activeCount ?: 0
        private var downTime = 0L
        init {
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
            view.setInputCharacterPopEnabled(enabled)
            view.setLongPressTimeout(500)
            view.setOnFlickTextPreviewListener(FlickTextPreviewListener { previews += it })
            view.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
                override fun onPress(action: KeyAction) = Unit
                override fun onAction(action: KeyAction, isFlick: Boolean) { actions += action; atDispatch?.invoke() }
                override fun onCommittedTextAction(action: KeyAction.Text, isFlick: Boolean,
                    hapticContext: InputHapticContext, onAccepted: () -> Unit) {
                    onCommittedAction(action, isFlick, hapticContext)
                    if (accept) { if (defer) pending = onAccepted else onAccepted() }
                }
                override fun onActionLongPress(action: KeyAction) = Unit
                override fun onActionUpAfterLongPress(action: KeyAction) = Unit
                override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
                override fun onFlickActionLongPress(action: KeyAction) = Unit
                override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
            })
            view.setKeyboard(layout(type))
            activity.get().setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 600, 300)
        }
        fun send(action: Int, dx: Float = 0f, dy: Float = 0f) {
            val child = view.getChildAt(0)
            val loc = IntArray(2).also(child::getLocationOnScreen)
            if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                loc[0] + child.width / 2f + dx, loc[1] + child.height / 2f + dy, 0)
            try { view.onTouchEvent(event) } finally { event.recycle() }
        }
        fun tap() { send(MotionEvent.ACTION_DOWN); send(MotionEvent.ACTION_UP) }
        override fun close() { activity.pause().stop().destroy() }
    }

    @Test fun downAndPreviewAreNotSuccessAndInputIsDispatchedBeforeThePop() = Harness().use { h ->
        h.atDispatch = { assertEquals(0, h.count) }
        h.send(MotionEvent.ACTION_DOWN)
        assertEquals(0, h.count)
        h.send(MotionEvent.ACTION_UP)
        assertEquals(listOf(KeyAction.Text("あ")), h.actions)
        assertEquals("あ", h.effect!!.activeGlyphs.single().text)
        assertTrue(h.previews.any { it is FlickTextPreviewEvent.Finished })
    }

    @Test fun rejectedInputDoesNotPopEvenThoughPreviewFinished() = Harness().use { h ->
        h.accept = false; h.tap()
        assertTrue(h.previews.any { it is FlickTextPreviewEvent.Finished })
        assertEquals(0, h.count)
    }

    @Test fun cancelDoesNotInputOrPop() = Harness().use { h ->
        h.send(MotionEvent.ACTION_DOWN)
        h.send(MotionEvent.ACTION_MOVE, dy = -110f)
        h.send(MotionEvent.ACTION_CANCEL)
        assertTrue(h.actions.isEmpty()); assertEquals(0, h.count)
    }

    @Test fun everyCrossDirectionPopsTheCommittedText() {
        for ((delta, text) in listOf((0f to -110f) to "う", (-110f to 0f) to "い",
            (110f to 0f) to "え", (0f to 110f) to "お")) Harness().use { h ->
            h.send(MotionEvent.ACTION_DOWN)
            h.send(MotionEvent.ACTION_MOVE, delta.first, delta.second)
            h.send(MotionEvent.ACTION_UP, delta.first, delta.second)
            assertEquals(listOf(KeyAction.Text(text)), h.actions)
            assertEquals(text, h.effect!!.activeGlyphs.single().text)
        }
    }

    @Test fun popupLongPressStillCommitsAndPops() = Harness().use { h ->
        h.send(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550))
        h.send(MotionEvent.ACTION_UP)
        assertEquals(listOf(KeyAction.Text("あ")), h.actions)
        assertEquals(1, h.count)
    }

    @Test fun commonCommitSupportsNormalStandardPetalCircularAndTwoStepKeys() {
        for (type in listOf(KeyType.NORMAL, KeyType.STANDARD_FLICK, KeyType.PETAL_FLICK,
            KeyType.CIRCULAR_FLICK, KeyType.CENTER_GUIDE_FLICK, KeyType.TWO_STEP_FLICK,
            KeyType.STICKY_TWO_STEP_FLICK, KeyType.HIERARCHICAL_FLICK)) Harness(type).use { h ->
            h.tap()
            assertEquals("type=$type", listOf(KeyAction.Text("あ")), h.actions)
            assertEquals("type=$type", 1, h.count)
        }
    }

    @Test fun fastNextKeyInputsAreNotBlockedAndOlderPopsOverlap() = Harness().use { h ->
        repeat(20) { h.tap() }
        assertEquals(20, h.actions.size)
        assertEquals(12, h.count)
    }

    @Test fun offUsesExistingDispatchAndDoesNotCreateRenderer() = Harness(enabled = false).use { h ->
        h.tap()
        assertEquals(listOf(KeyAction.Text("あ")), h.actions)
        assertNull(h.effect)
    }

    @Test fun deferredAcceptanceAfterKeyboardReplacementIsDropped() = Harness().use { h ->
        h.defer = true; h.tap()
        assertEquals(0, h.count)
        assertNotNull(h.pending)
        h.view.setKeyboard(layout(KeyType.CROSS_FLICK))
        h.pending!!()
        assertEquals(0, h.count)
    }

    @Test fun hidingAndReplacingKeyboardClearOverlappingPops() = Harness().use { h ->
        h.tap(); h.tap(); assertEquals(2, h.count)
        h.view.visibility = View.GONE
        assertEquals(0, h.count)
        h.view.visibility = View.VISIBLE
        h.tap(); assertEquals(1, h.count)
        h.view.setKeyboard(layout(KeyType.CROSS_FLICK))
        assertEquals(0, h.count)
    }

    companion object {
        private fun layout(type: KeyType): KeyboardLayout {
            val map = mapOf(FlickDirection.TAP to FlickAction.Input("あ"),
                FlickDirection.UP to FlickAction.Input("う"),
                FlickDirection.UP_LEFT_FAR to FlickAction.Input("い"),
                FlickDirection.UP_RIGHT_FAR to FlickAction.Input("え"),
                FlickDirection.DOWN to FlickAction.Input("お"))
            return KeyboardLayout(
                keys = listOf(KeyData("あ", 0, 0, type != KeyType.NORMAL, KeyAction.Text("あ"), keyId = "a", keyType = type)),
                flickKeyMaps = mapOf("a" to listOf(map)),
                columnCount = 1, rowCount = 1,
                hierarchicalFlickMaps = mapOf("a" to TfbiFlickNode.StatefulKey(
                    normalMap = mapOf(TfbiFlickDirection.TAP to TfbiFlickNode.Input("あ")), label = "あ")),
                twoStepFlickKeyMaps = mapOf("a" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.TAP to "あ"))))
        }
    }
}
