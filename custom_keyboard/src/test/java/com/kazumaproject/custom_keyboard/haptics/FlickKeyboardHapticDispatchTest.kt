package com.kazumaproject.custom_keyboard.haptics

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardHapticDispatchTest {
    private val played = mutableListOf<HapticPatternKind>()
    private val committed = mutableListOf<KeyAction>()
    private val feedback = CommittedInputHaptics { played += it }
    private fun keyboard(layout: KeyboardLayout): FlickKeyboardView {
        val view = FlickKeyboardView(ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar))
        view.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
            override fun onPress(action: KeyAction) = Unit
            override fun onAction(action: KeyAction, isFlick: Boolean) { fail("Lost gesture context") }
            override fun onCommittedAction(action: KeyAction, isFlick: Boolean, hapticContext: InputHapticContext) {
                feedback.dispatch(action, hapticContext) {
                    committed += action
                    true
                }
            }
            override fun onActionLongPress(action: KeyAction) = Unit
            override fun onActionUpAfterLongPress(action: KeyAction) = Unit
            override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
            override fun onFlickActionLongPress(action: KeyAction) = Unit
            override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
        })
        view.setKeyboard(layout)
        view.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 240, 120)
        return view
    }
    private fun touch(view: FlickKeyboardView, action: Int, x: Float = 0.5f, y: Float = 0.5f) {
        val key = view.getChildAt(0)
        val event = MotionEvent.obtain(100, 120, action, key.left + key.width * x, key.top + key.height * y, 0)
        view.onTouchEvent(event)
        event.recycle()
    }
    private fun layout(action: KeyAction, type: KeyType = KeyType.CROSS_FLICK): KeyboardLayout {
        val key = KeyData("key", 0, 0, true, action = action, keyId = "key", keyType = type)
        return KeyboardLayout(keys = listOf(key), columnCount = 1, rowCount = 1,
            flickKeyMaps = mapOf("key" to listOf(mapOf(FlickDirection.TAP to FlickAction.Action(action)))),
            twoStepFlickKeyMaps = mapOf("key" to mapOf(
                TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.TAP to "あ"),
                TfbiFlickDirection.LEFT to mapOf(
                    TfbiFlickDirection.TAP to "い",
                    TfbiFlickDirection.LEFT to "い",
                    TfbiFlickDirection.RIGHT to "こう"))))
    }
    @Test fun normalInputPlaysOnceOnlyAtRelease() {
        val view = keyboard(layout(KeyAction.Text("あ")))
        touch(view, MotionEvent.ACTION_DOWN)
        assertTrue(played.isEmpty())
        touch(view, MotionEvent.ACTION_UP)
        assertEquals(listOf(HapticPatternKind.NORMAL_FLICK), played)
    }
    @Test fun specialInputPlaysOnceOnlyAtRelease() {
        val view = keyboard(layout(KeyAction.Delete))
        touch(view, MotionEvent.ACTION_DOWN)
        assertTrue(played.isEmpty())
        touch(view, MotionEvent.ACTION_UP)
        assertEquals(listOf(HapticPatternKind.SPECIAL_KEY), played)
    }
    @Test fun cancelledInputIsSilent() {
        val view = keyboard(layout(KeyAction.Text("あ")))
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_CANCEL)
        assertTrue(played.isEmpty())
    }
    @Test fun tfbiControllerSelectionIsSilentAndCommitRetainsTwoStepCategory() {
        val view = keyboard(layout(KeyAction.Text("あ"), KeyType.TWO_STEP_FLICK))
        val field = FlickKeyboardView::class.java.getDeclaredField("tfbiControllers").apply { isAccessible = true }
        val controller = (field.get(view) as List<*>).single() as TfbiInputController
        val listener = controller.listener!!
        listener.onPress(TfbiFlickDirection.TAP, TfbiFlickDirection.TAP)
        listener.onSelectionChanged(TfbiFlickDirection.LEFT, TfbiFlickDirection.RIGHT, false)
        assertTrue(played.isEmpty())
        listener.onFlick(TfbiFlickDirection.LEFT, TfbiFlickDirection.RIGHT)
        assertEquals(listOf(HapticPatternKind.TWO_STEP_FLICK), played)
        played.clear()
        listener.onFlick(TfbiFlickDirection.LEFT, TfbiFlickDirection.TAP)
        assertEquals(listOf(HapticPatternKind.NORMAL_FLICK), played)
    }

    private fun hierarchicalKeyboard(): FlickKeyboardView {
        val root = TfbiFlickNode.StatefulKey(
            label = "key",
            normalMap = mapOf(
                TfbiFlickDirection.TAP to TfbiFlickNode.Input("あ"),
                TfbiFlickDirection.LEFT to TfbiFlickNode.SubMenu(
                    cancelOnTap = true,
                    nextMap = mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input("い"),
                        // A one-character output must still use the two-step waveform.
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("う"),
                        TfbiFlickDirection.DOWN to TfbiFlickNode.SubMenu(
                            nextMap = mapOf(
                                TfbiFlickDirection.DOWN to TfbiFlickNode.Input("え"),
                                TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("お"),
                            )
                        ),
                    )
                ),
            )
        )
        return keyboard(layout(KeyAction.Text("あ"), KeyType.HIERARCHICAL_FLICK).copy(
            hierarchicalFlickMaps = mapOf("key" to root)
        )).apply {
            measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
            layout(0, 0, 480, 480)
        }
    }

    @Test fun hierarchicalTapAndFirstStageUseNormalWaveform() {
        val view = hierarchicalKeyboard()
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_UP)
        assertEquals(listOf(KeyAction.Text("あ")), committed)
        assertEquals(listOf(HapticPatternKind.NORMAL_FLICK), played)
        played.clear()
        committed.clear()
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_MOVE, 0f, 0.5f)
        assertTrue(played.isEmpty())
        touch(view, MotionEvent.ACTION_UP, 0f, 0.5f)
        assertEquals(listOf(KeyAction.Text("い")), committed)
        assertEquals(listOf(HapticPatternKind.NORMAL_FLICK), played)
    }

    @Test fun hierarchicalSecondStagePlaysTwoStepOnceAfterActualTouchRelease() {
        val view = hierarchicalKeyboard()
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_MOVE, 0f, 0.5f)
        touch(view, MotionEvent.ACTION_MOVE, 0.5f, 0f)
        assertTrue(played.isEmpty())
        assertTrue(committed.isEmpty())
        touch(view, MotionEvent.ACTION_UP, 0.5f, 0f)
        assertEquals(listOf(KeyAction.Text("う")), committed)
        assertEquals(listOf(HapticPatternKind.TWO_STEP_FLICK), played)
    }

    @Test fun deeperHierarchicalSelectionStillPlaysOneWholeTwoStepPattern() {
        val view = hierarchicalKeyboard()
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_MOVE, 0f, 0.5f)
        touch(view, MotionEvent.ACTION_MOVE, 0.5f, 1f)
        touch(view, MotionEvent.ACTION_MOVE, 1f, 0.5f)
        assertTrue(played.isEmpty())
        touch(view, MotionEvent.ACTION_UP, 1f, 0.5f)
        assertEquals(listOf(KeyAction.Text("お")), committed)
        assertEquals(listOf(HapticPatternKind.TWO_STEP_FLICK), played)
    }

    @Test fun returningFromSecondStageClassifiesTheFinalRootSelection() {
        val view = hierarchicalKeyboard()
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_MOVE, 0f, 0.5f)
        touch(view, MotionEvent.ACTION_MOVE, 0.5f, 0f)
        touch(view, MotionEvent.ACTION_MOVE)
        assertTrue(played.isEmpty())
        touch(view, MotionEvent.ACTION_UP)
        assertEquals(listOf(KeyAction.Text("あ")), committed)
        assertEquals(listOf(HapticPatternKind.NORMAL_FLICK), played)
    }

    @Test fun cancellingHierarchicalSecondStageIsSilent() {
        val view = hierarchicalKeyboard()
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_MOVE, 0f, 0.5f)
        touch(view, MotionEvent.ACTION_MOVE, 0.5f, 0f)
        touch(view, MotionEvent.ACTION_CANCEL)
        assertTrue(played.isEmpty())
        assertTrue(committed.isEmpty())
    }

    @Test fun regularAndStickyTwoStepTouchesRetainTheirCategory() {
        for (type in listOf(KeyType.TWO_STEP_FLICK, KeyType.STICKY_TWO_STEP_FLICK)) {
            played.clear()
            committed.clear()
            val view = keyboard(layout(KeyAction.Text("あ"), type))
            touch(view, MotionEvent.ACTION_DOWN)
            touch(view, MotionEvent.ACTION_MOVE, 0f, 0.5f)
            touch(view, MotionEvent.ACTION_MOVE, 1f, 0.5f)
            assertTrue(played.isEmpty())
            touch(view, MotionEvent.ACTION_UP, 1f, 0.5f)
            assertEquals(type.name, listOf(KeyAction.Text("こう")), committed)
            assertEquals(type.name, listOf(HapticPatternKind.TWO_STEP_FLICK), played)
        }
    }

    @Test fun twoStepKeysUseNormalWaveformForTapAndOneDirectionRelease() {
        for (type in listOf(KeyType.TWO_STEP_FLICK, KeyType.STICKY_TWO_STEP_FLICK)) {
            for (sendMove in listOf(true, false)) {
                val view = keyboard(layout(KeyAction.Text("あ"), type))
                played.clear()
                committed.clear()
                touch(view, MotionEvent.ACTION_DOWN)
                assertTrue(played.isEmpty())
                touch(view, MotionEvent.ACTION_UP)
                assertEquals(type.name, listOf(KeyAction.Text("あ")), committed)
                assertEquals(type.name, listOf(HapticPatternKind.NORMAL_FLICK), played)

                played.clear()
                committed.clear()
                touch(view, MotionEvent.ACTION_DOWN)
                if (sendMove) touch(view, MotionEvent.ACTION_MOVE, 0f, 0.5f)
                assertTrue(played.isEmpty())
                touch(view, MotionEvent.ACTION_UP, 0f, 0.5f)
                assertEquals("$type / move=$sendMove", listOf(KeyAction.Text("い")), committed)
                assertEquals("$type / move=$sendMove", listOf(HapticPatternKind.NORMAL_FLICK), played)
            }
        }
    }

    @Test fun returningToFirstDirectionUsesNormalWaveformAtRelease() {
        val view = keyboard(layout(KeyAction.Text("あ"), KeyType.TWO_STEP_FLICK))
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_MOVE, 0f, 0.5f)
        touch(view, MotionEvent.ACTION_MOVE, 1f, 0.5f)
        touch(view, MotionEvent.ACTION_MOVE, 0f, 0.5f)
        assertTrue(played.isEmpty())
        touch(view, MotionEvent.ACTION_UP, 0f, 0.5f)
        assertEquals(listOf(KeyAction.Text("い")), committed)
        assertEquals(listOf(HapticPatternKind.NORMAL_FLICK), played)
    }

    @Test fun longPressOutputUsesTheSameFirstStageClassification() {
        val view = keyboard(layout(KeyAction.Text("あ"), KeyType.TWO_STEP_FLICK).copy(
            twoStepLongPressKeyMaps = mapOf("key" to mapOf(
                TfbiFlickDirection.LEFT to mapOf(
                    TfbiFlickDirection.LEFT to "い",
                    TfbiFlickDirection.RIGHT to "こう"
                )
            ))
        ))
        val field = FlickKeyboardView::class.java.getDeclaredField("tfbiControllers").apply { isAccessible = true }
        val listener = ((field.get(view) as List<*>).single() as TfbiInputController).listener!!
        assertTrue(listener.onLongPressFlick(TfbiFlickDirection.LEFT, TfbiFlickDirection.LEFT))
        assertEquals(listOf(KeyAction.Text("い")), committed)
        assertEquals(listOf(HapticPatternKind.NORMAL_FLICK), played)
        played.clear()
        committed.clear()
        assertTrue(listener.onLongPressFlick(TfbiFlickDirection.LEFT, TfbiFlickDirection.RIGHT))
        assertEquals(listOf(KeyAction.Text("こう")), committed)
        assertEquals(listOf(HapticPatternKind.TWO_STEP_FLICK), played)
    }
}
