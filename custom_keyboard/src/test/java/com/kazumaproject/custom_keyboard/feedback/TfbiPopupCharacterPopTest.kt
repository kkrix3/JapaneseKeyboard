package com.kazumaproject.custom_keyboard.feedback

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickPopupView
import com.kazumaproject.custom_keyboard.view.TfbiGuidePopupView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TfbiPopupCharacterPopTest {
    @Test fun legacyGridCopiesTheSelectedCellBeforeTheNextDrawAndRejectsMissingText() = harness { context, attach ->
        val popup = TfbiFlickPopupView(context)
        attach(popup)
        popup.setCharacters("あ", mapOf(TfbiFlickDirection.UP to "あ", TfbiFlickDirection.RIGHT to "あ"))
        popup.highlightDirection(TfbiFlickDirection.TAP)
        val center = popup.captureInputCharacterPop(popup.rootView, "あ")!!
        popup.highlightDirection(TfbiFlickDirection.UP)
        val up = popup.captureInputCharacterPop(popup.rootView, "あ")!!
        popup.highlightDirection(TfbiFlickDirection.RIGHT)
        val right = popup.captureInputCharacterPop(popup.rootView, "あ")!!
        assertEquals(center.centerX, up.centerX, .01f)
        assertEquals(center.centerY - 100f, up.centerY, .01f)
        assertEquals(center.centerX + 100f, right.centerX, .01f)
        assertEquals(center.centerY, right.centerY, .01f)
        assertNull(popup.captureInputCharacterPop(popup.rootView, "ん"))
    }

    @Test fun compactGuideCopiesCurrentOutputAtItsActualSlotWithoutRequiringADraw() = harness { context, attach ->
        val popup = TfbiGuidePopupView(context)
        attach(popup)
        popup.setState(TfbiGuidePopupState("う", TfbiFlickDirection.TAP))
        val center = popup.captureInputCharacterPop(popup.rootView, "う")!!
        popup.setState(TfbiGuidePopupState("う", TfbiFlickDirection.UP))
        val up = popup.captureInputCharacterPop(popup.rootView, "う")!!
        assertEquals(center.centerX, up.centerX, .01f)
        assertTrue(center.centerY - up.centerY > 90f)
        assertEquals(center.paint.textSize, up.paint.textSize, 0f)
        assertNull(popup.captureInputCharacterPop(popup.rootView, "未表示"))
    }

    private fun harness(block: (ContextThemeWrapper, (View) -> Unit) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val context = ContextThemeWrapper(activity.get(), com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val container = FrameLayout(context)
        activity.get().setContentView(container)
        try {
            block(context) { view ->
                container.addView(view, FrameLayout.LayoutParams(300, 300))
                container.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
                container.layout(0, 0, 600, 600)
            }
        } finally { activity.pause().stop().destroy() }
    }
}
