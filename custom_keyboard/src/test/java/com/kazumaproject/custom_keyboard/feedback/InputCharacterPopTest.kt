package com.kazumaproject.custom_keyboard.feedback

import android.app.Activity
import android.graphics.Paint
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class InputCharacterPopTest {
    @Test fun curvesHaveRequestedEndpointsAndNoOvershoot() {
        assertEquals(280L, InputCharacterPopSpec.DURATION_MS)
        assertEquals(1f, InputCharacterPopSpec.scale(0f), 0f)
        assertEquals(1.8f, InputCharacterPopSpec.scale(1f), .0001f)
        assertEquals(1f, InputCharacterPopSpec.alpha(.15f), 0f)
        assertEquals(0f, InputCharacterPopSpec.alpha(1f), 0f)
        var previousScale = 1f
        var previousAlpha = 1f
        for (i in 0..100) {
            val scale = InputCharacterPopSpec.scale(i / 100f)
            val alpha = InputCharacterPopSpec.alpha(i / 100f)
            assertTrue(scale in previousScale..1.8f)
            assertTrue(alpha in 0f..previousAlpha)
            previousScale = scale; previousAlpha = alpha
        }
        assertTrue(InputCharacterPopSpec.scale(.25f) - 1f >
            InputCharacterPopSpec.scale(1f) - InputCharacterPopSpec.scale(.75f))
    }

    @Test fun invisibleControlAndHugeStringsDoNotAllocateAnimations() {
        for (text in listOf("", " ", "\n", "a\tb", "あ".repeat(33)))
            assertFalse(InputCharacterPopSpec.canDisplay(text))
        assertTrue(InputCharacterPopSpec.canDisplay("😀".repeat(32)))
        assertTrue(InputCharacterPopSpec.canDisplay("きゃ"))
    }

    @Test fun offDoesNotEvenCaptureAndAcceptedCallbacksAreSingleUse() = harness { owner, effect ->
        var captured = 0
        assertNull(effect.prepare { captured++; glyph(owner, it) })
        assertEquals(0, captured)
        effect.enabled = true
        val accepted = effect.prepare { glyph(owner, it) }!!
        assertEquals(0, effect.activeCount)
        accepted(); accepted()
        assertEquals(1, effect.activeCount)
    }

    @Test fun overlappingGlyphsAreBoundedExpireAndKeepTheSameCenter() = harness { owner, effect ->
        effect.enabled = true
        repeat(20) { effect.prepare { glyph(owner, it) }!!.invoke() }
        assertEquals(InputCharacterPopSpec.MAX_ACTIVE, effect.activeCount)
        val first = effect.activeGlyphs.first()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(first.centerX, effect.activeGlyphs.first().centerX, 0f)
        assertEquals(first.centerY, effect.activeGlyphs.first().centerY, 0f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(0, effect.activeCount)
    }

    @Test fun disableInvalidatesOutstandingAcceptanceAndClearsActiveGlyphs() = harness { owner, effect ->
        effect.enabled = true
        val pending = effect.prepare { glyph(owner, it) }!!
        effect.prepare { glyph(owner, it) }!!.invoke()
        effect.enabled = false
        effect.enabled = true
        pending()
        assertEquals(0, effect.activeCount)
    }

    @Test fun hostResizeAndOwnerHideClearAnimations() = harness { owner, effect ->
        effect.enabled = true
        effect.prepare { glyph(owner, it) }!!.invoke()
        val root = owner.rootView
        root.layout(0, 0, 900, 700)
        assertEquals(0, effect.activeCount)
        effect.prepare { glyph(owner, it) }!!.invoke()
        owner.visibility = View.GONE
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertEquals(0, effect.activeCount)
    }

    @Test fun captureCopiesPaintAndRespectsScaledSource() = harness { owner, _ ->
        owner.scaleX = .5f; owner.scaleY = .75f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; color = 0xff123456.toInt() }
        val copy = InputCharacterPopGlyph.capture(owner, owner.rootView, "あ", paint, 40f, 50f)!!
        paint.color = 0xffabcdef.toInt()
        assertEquals(0xff123456.toInt(), copy.paint.color)
        assertEquals(.5f, copy.scaleX, 0f)
        assertEquals(.75f, copy.scaleY, 0f)
    }

    private fun glyph(owner: View, host: View) = InputCharacterPopGlyph.capture(
        owner, host, "あ", Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f }, 40f, 50f)

    private fun harness(block: (View, InputCharacterPop) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val owner = TextView(activity.get()).apply { text = "あ" }
        val container = FrameLayout(activity.get()).apply { addView(owner, FrameLayout.LayoutParams(200, 100)) }
        activity.get().setContentView(container)
        container.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, 600, 300)
        val effect = InputCharacterPop(owner)
        try { block(owner, effect) } finally { effect.clear(); activity.pause().stop().destroy() }
    }
}
