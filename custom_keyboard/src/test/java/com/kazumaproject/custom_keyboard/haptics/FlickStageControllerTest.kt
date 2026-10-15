package com.kazumaproject.custom_keyboard.haptics

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.widget.Button
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.custom_keyboard.controller.*
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiInputController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class FlickStageControllerTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
    private val config = GestureSessionConfigSource {
        GestureSessionConfig(0, 100, 30f, longPressTimeoutMillis = 500)
    }
    private val played = mutableListOf<HapticPatternKind>()
    private val feedback = FlickStageHapticFeedback(context, play = { played += it })
    private val raw = PreferenceManager.getDefaultSharedPreferences(context)
    private data class Target(val view: Button, val cancel: () -> Unit)

    private fun target(kind: String, attachToWindow: Boolean = false): Target {
        raw.edit().clear().putBoolean(CustomHapticPreferences.KEY_STAGE_ENABLED, true)
            .putBoolean(CustomHapticPreferences.KEY_STAGE_SUPPRESSION, false).commit()
        val button = Button(context).apply { layout(0, 0, 100, 100) }
        val activity = if (attachToWindow) Robolectric.buildActivity(Activity::class.java).setup() else null
        activity?.get()?.setContentView(button)
        val map = mapOf(FlickDirection.TAP to "tap", FlickDirection.UP to "first",
            FlickDirection.UP_RIGHT_FAR to "right")
        val provider = { first: TfbiFlickDirection, second: TfbiFlickDirection ->
            when {
                first == TfbiFlickDirection.TAP -> if (second == TfbiFlickDirection.TAP) "tap" else ""
                second == TfbiFlickDirection.TAP || first == second -> "first"
                else -> "second"
            }
        }
        val cancel: () -> Unit = when (kind) {
            "standard" -> StandardFlickInputController(context, config).run {
                stageHaptics = feedback
                attach(button, map, SegmentedBackgroundDrawable("tap", Color.WHITE, Color.GRAY, Color.BLACK, 0f))
                this::cancel
            }
            "cross-text" -> CrossFlickInputController(context, config).run {
                stageHaptics = feedback
                attachText(button, map)
                this::cancel
            }
            "special" -> CrossFlickInputController(context, config).run {
                stageHaptics = feedback
                attach(button, mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Delete),
                    FlickDirection.UP to FlickAction.Action(KeyAction.MoveCursorUp)))
                this::cancel
            }
            "center" -> CenterGuideFlickInputController(context, config).run {
                stageHaptics = feedback
                attach(button, map)
                this::cancel
            }
            "two-step" -> TfbiInputController(context, config).run {
                stageHaptics = feedback
                attach(button, provider)
                this::cancel
            }
            "sticky" -> TfbiStickyFlickController(context, config).run {
                stageHaptics = feedback
                attach(button, provider)
                this::cancel
            }
            "hierarchical" -> TfbiHierarchicalFlickController(context, config).run {
                stageHaptics = feedback
                attach(button, TfbiFlickNode.StatefulKey(label = "tap", normalMap = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("tap"),
                    TfbiFlickDirection.UP to TfbiFlickNode.SubMenu(cancelOnTap = true,
                        nextMap = mapOf(TfbiFlickDirection.UP to TfbiFlickNode.Input("first"),
                            TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("second"))))))
                this::cancel
            }
            "circular" -> CustomAngleFlickController(context, config).run {
                stageHaptics = feedback
                attach(button, listOf(mapOf(CircularFlickDirection.TAP to FlickAction.Input("tap")) +
                    CircularFlickDirection.slots(4).associateWith { FlickAction.Input("first") }), listOf(null))
                this::cancel
            }
            else -> error(kind)
        }
        return Target(button) {
            cancel()
            activity?.pause()?.stop()?.destroy()
        }
    }

    private fun touch(view: Button, action: Int, x: Float = 50f, y: Float = 50f) {
        val event = MotionEvent.obtain(0, 10, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    @Test fun allRecognizersEmitFirstStageOnlyDuringMoveAndCancelCleanly() {
        for (kind in listOf("standard", "cross-text", "special", "center", "two-step", "sticky", "hierarchical", "circular")) {
            val target = target(kind)
            played.clear()
            touch(target.view, MotionEvent.ACTION_DOWN)
            assertTrue("Unexpected DOWN feedback: $kind", played.isEmpty())
            touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
            touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
            assertEquals(kind, listOf(HapticPatternKind.STAGE_ADVANCE), played)
            touch(target.view, MotionEvent.ACTION_CANCEL, y = -50f)
            target.cancel()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals(kind, 1, played.size)
        }
    }

    @Test fun twoStepReturnAndStickyRetentionFollowRecognizedPaths() {
        for (kind in listOf("two-step", "sticky")) {
            val target = target(kind)
            played.clear()
            touch(target.view, MotionEvent.ACTION_DOWN)
            touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
            touch(target.view, MotionEvent.ACTION_MOVE, x = 150f, y = -50f)
            assertEquals(kind, listOf(HapticPatternKind.STAGE_ADVANCE, HapticPatternKind.STAGE_ADVANCE), played)
            touch(target.view, MotionEvent.ACTION_MOVE)
            if (kind == "two-step") {
                assertEquals(listOf(HapticPatternKind.STAGE_ADVANCE, HapticPatternKind.STAGE_ADVANCE,
                    HapticPatternKind.STAGE_RETREAT, HapticPatternKind.STAGE_RETREAT), played)
            }
            touch(target.view, MotionEvent.ACTION_CANCEL)
            target.cancel()
        }
    }

    @Test fun hierarchicalEntryEchoIsOneStageThenSecondStageAndRootReturn() {
        val target = target("hierarchical")
        touch(target.view, MotionEvent.ACTION_DOWN)
        touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
        touch(target.view, MotionEvent.ACTION_MOVE, x = 150f)
        assertEquals(listOf(HapticPatternKind.STAGE_ADVANCE, HapticPatternKind.STAGE_ADVANCE), played)
        touch(target.view, MotionEvent.ACTION_MOVE)
        touch(target.view, MotionEvent.ACTION_MOVE)
        assertEquals(listOf(HapticPatternKind.STAGE_ADVANCE, HapticPatternKind.STAGE_ADVANCE,
            HapticPatternKind.STAGE_RETREAT, HapticPatternKind.STAGE_RETREAT), played)
        target.cancel()
    }

    @Test fun specialLongPressStopsStageFeedbackAndReleaseAddsNoStageCommand() {
        val target = target("special")
        touch(target.view, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(501))
        touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
        touch(target.view, MotionEvent.ACTION_UP, y = -50f)
        assertTrue(played.isEmpty())
        target.cancel()
    }

    @Test fun suppressedHapticsAndPendingDelayNeverDelayPreviewOrCommit() {
        raw.edit().clear().putBoolean(CustomHapticPreferences.KEY_STAGE_ENABLED, true)
            .putBoolean(CustomHapticPreferences.KEY_STAGE_SUPPRESSION, true)
            .putInt(CustomHapticPreferences.KEY_STAGE_DELAY_MS, 200).commit()
        val previews = mutableListOf<String?>()
        val commits = mutableListOf<KeyAction>()
        val button = Button(context).apply { layout(0, 0, 100, 100) }
        val controller = CrossFlickInputController(context, config).apply {
            stageHaptics = feedback
            listener = object : CrossFlickInputController.CrossFlickListener {
                override fun onPress(action: KeyAction) = Unit
                override fun onFlick(action: KeyAction, isFlick: Boolean) { commits += action }
                override fun onFlickLongPress(action: KeyAction) = Unit
                override fun onFlickUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
                override fun onTextSelectionChanged(text: String?, isFlick: Boolean) { previews += text }
            }
            attachText(button, mapOf(FlickDirection.TAP to "tap", FlickDirection.UP to "up",
                FlickDirection.UP_RIGHT_FAR to "right"))
        }
        touch(button, MotionEvent.ACTION_DOWN)
        touch(button, MotionEvent.ACTION_MOVE, y = -50f)
        touch(button, MotionEvent.ACTION_MOVE, x = 150f)
        touch(button, MotionEvent.ACTION_MOVE, y = -50f)
        touch(button, MotionEvent.ACTION_MOVE, x = 150f)
        assertEquals(listOf("up", "right", "up", "right"), previews)
        touch(button, MotionEvent.ACTION_UP, x = 150f)
        assertEquals(listOf(KeyAction.Text("right")), commits)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(listOf(HapticPatternKind.STAGE_ADVANCE, HapticPatternKind.STAGE_RETREAT), played)
        controller.cancel()
    }

    @Test fun circularGuideLongPressKeepsOrdinaryFlickFeedback() {
        val target = target("circular")
        touch(target.view, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(501))
        touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
        assertEquals(listOf(HapticPatternKind.STAGE_ADVANCE), played)
        target.cancel()
    }

    @Test fun textGuideLongPressKeepsStageFeedbackAndReleaseAddsNoStageCommand() {
        for (kind in listOf("cross-text", "two-step")) {
            val target = target(kind, attachToWindow = true)
            played.clear()
            touch(target.view, MotionEvent.ACTION_DOWN)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(501))
            assertTrue(kind, played.isEmpty())
            touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
            touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
            assertEquals(kind, listOf(HapticPatternKind.STAGE_ADVANCE), played)
            touch(target.view, MotionEvent.ACTION_UP, y = -50f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            assertEquals(kind, listOf(HapticPatternKind.STAGE_ADVANCE), played)
            target.cancel()
        }
    }

    @Test fun petalLongPressTextKeepsFeedbackPreviewAndCommit() {
        val target = target("cross-text", attachToWindow = true)
        val previews = mutableListOf<String?>()
        val commits = mutableListOf<KeyAction>()
        val controller = CrossFlickInputController(context, config).apply {
            stageHaptics = feedback
            listener = object : CrossFlickInputController.CrossFlickListener {
                override fun onPress(action: KeyAction) = Unit
                override fun onFlick(action: KeyAction, isFlick: Boolean) { commits += action }
                override fun onFlickLongPress(action: KeyAction) = Unit
                override fun onFlickUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
                override fun onTextSelectionChanged(text: String?, isFlick: Boolean) { previews += text }
            }
            attachText(target.view, mapOf(FlickDirection.TAP to "tap", FlickDirection.UP to "up"),
                mapOf(FlickDirection.UP to "held-up"))
        }
        touch(target.view, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(501))
        touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
        assertEquals("held-up", previews.last())
        assertEquals(listOf(HapticPatternKind.STAGE_ADVANCE), played)
        touch(target.view, MotionEvent.ACTION_UP, y = -50f)
        assertEquals(listOf(KeyAction.Text("held-up")), commits)
        assertEquals(1, played.size)
        controller.cancel()
        target.cancel()
    }

    @Test fun twoStepLongPressKeepsBothStagesAndExistingCommitFallback() {
        for (hasLongPressText in listOf(false, true)) {
            val target = target("two-step", attachToWindow = true)
            played.clear()
            val previews = mutableListOf<Pair<TfbiFlickDirection, TfbiFlickDirection>>()
            val commits = mutableListOf<String>()
            val controller = TfbiInputController(context, config).apply {
                stageHaptics = feedback
                listener = object : TfbiInputController.TfbiListener {
                    override fun onPress(first: TfbiFlickDirection, second: TfbiFlickDirection) = Unit
                    override fun onFlick(first: TfbiFlickDirection, second: TfbiFlickDirection) {
                        commits += "normal:$first:$second"
                    }
                    override fun onLongPressFlick(first: TfbiFlickDirection, second: TfbiFlickDirection): Boolean {
                        if (hasLongPressText) commits += "held:$first:$second"
                        return hasLongPressText
                    }
                    override fun onSelectionChanged(first: TfbiFlickDirection, second: TfbiFlickDirection,
                        isLongPress: Boolean) {
                        assertTrue(isLongPress)
                        previews += first to second
                    }
                }
                attach(target.view, { _, _ -> "normal" },
                    { _, _ -> if (hasLongPressText) "held" else "" })
            }
            touch(target.view, MotionEvent.ACTION_DOWN)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(501))
            touch(target.view, MotionEvent.ACTION_MOVE, y = -50f)
            touch(target.view, MotionEvent.ACTION_MOVE, x = 150f, y = -50f)
            assertEquals(TfbiFlickDirection.UP to TfbiFlickDirection.RIGHT, previews.last())
            assertEquals(listOf(HapticPatternKind.STAGE_ADVANCE, HapticPatternKind.STAGE_ADVANCE), played)
            touch(target.view, MotionEvent.ACTION_UP, x = 150f, y = -50f)
            assertEquals(listOf(if (hasLongPressText) "held:UP:RIGHT" else "normal:UP:RIGHT"), commits)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            assertEquals(2, played.size)
            controller.cancel()
            target.cancel()
        }
    }
}
