package com.kazumaproject.custom_keyboard.haptics

import android.os.Bundle
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HapticPatternEditorDialogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit() }
    class TestActivity : FragmentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
            super.onCreate(savedInstanceState)
        }
    }
    private fun activity() = Robolectric.buildActivity(TestActivity::class.java).setup()
    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun click(dialog: AlertDialog, label: String) = buttons(dialog.window!!.decorView)
        .single { it.text.toString() == label }.performClick()

    @Test fun presetInsertsNumbersAndSavePersists() {
        val controller = activity()
        val editor = HapticPatternEditorDialog.newInstance(HapticPatternKind.NORMAL_FLICK)
        editor.showNow(controller.get().supportFragmentManager, "editor")
        val dialog = editor.requireDialog() as AlertDialog
        click(dialog, "Click-like")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(HapticStep(5, 32), HapticStep(5, 96)),
            CustomHapticPreferences(context).pattern(HapticPatternKind.NORMAL_FLICK).steps)
        controller.pause().stop().destroy()
    }

    @Test fun clearAndCancelDoNotOverwriteSavedPattern() {
        val controller = activity()
        val editor = HapticPatternEditorDialog.newInstance(HapticPatternKind.TWO_STEP_FLICK)
        editor.showNow(controller.get().supportFragmentManager, "editor")
        val dialog = editor.requireDialog() as AlertDialog
        click(dialog, controller.get().getString(R.string.haptic_clear))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(HapticPattern.TwoStepFlickDefault,
            CustomHapticPreferences(context).pattern(HapticPatternKind.TWO_STEP_FLICK))
        controller.pause().stop().destroy()
    }

    @Test fun additionsStopAtTenAndDraftSurvivesRecreation() {
        val controller = activity()
        val editor = HapticPatternEditorDialog.newInstance(HapticPatternKind.NORMAL_FLICK)
        editor.showNow(controller.get().supportFragmentManager, "editor")
        val dialog = editor.requireDialog() as AlertDialog
        repeat(9) { click(dialog, "Tick-like") }
        assertFalse(buttons(dialog.window!!.decorView).single { it.text == "Tick-like" }.isEnabled)
        controller.recreate()
        val restored = controller.get().supportFragmentManager.findFragmentByTag("editor") as HapticPatternEditorDialog
        (restored.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(10, CustomHapticPreferences(context).pattern(HapticPatternKind.NORMAL_FLICK).steps.size)
        controller.pause().stop().destroy()
    }
}
