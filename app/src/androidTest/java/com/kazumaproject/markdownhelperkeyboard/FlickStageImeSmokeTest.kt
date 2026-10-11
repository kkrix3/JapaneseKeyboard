package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.haptics.CustomHapticPreferences
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorViewModel
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Verification harness only. The emulator checks input continuity, not physical vibration. */
@RunWith(AndroidJUnit4::class)
class FlickStageImeSmokeTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        automation.windows.forEach { it.root?.let(::visit) }
        return result
    }

    private fun key(label: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            nodes().firstOrNull { it.packageName?.toString() == ins.targetContext.packageName &&
                it.isVisibleToUser && it.isClickable &&
                it.text?.toString()?.lineSequence()?.firstOrNull()?.trim() == label
            }?.let { return Rect().also(it::getBoundsInScreen) }
            SystemClock.sleep(100)
        }
        error("Missing key $label")
    }

    private fun stableKey(): Rect {
        var previous: Rect? = null
        var stable = 0
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            val bounds = key("あ")
            stable = if (bounds == previous) stable + 1 else 0
            if (stable >= 3) return bounds
            previous = bounds
            SystemClock.sleep(100)
        }
        error("Keyboard did not settle")
    }

    private fun gesture(bounds: Rect, changeDirection: Boolean) {
        val start = SystemClock.uptimeMillis()
        val points = if (changeDirection) listOf(
            Triple(MotionEvent.ACTION_DOWN, 0f, 0f),
            Triple(MotionEvent.ACTION_MOVE, 0f, -bounds.height().toFloat()),
            Triple(MotionEvent.ACTION_MOVE, bounds.width().toFloat(), 0f),
            Triple(MotionEvent.ACTION_UP, bounds.width().toFloat(), 0f)
        ) else listOf(Triple(MotionEvent.ACTION_DOWN, 0f, 0f), Triple(MotionEvent.ACTION_UP, 0f, 0f))
        for ((action, dx, dy) in points) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX() + dx, bounds.exactCenterY() + dy, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
            SystemClock.sleep(20)
        }
        SystemClock.sleep(100)
    }

    private fun text(scenario: ActivityScenario<FastInputHostActivity>): String {
        var text = ""
        scenario.onActivity { text = it.editText.text.toString() }
        return text
    }

    @Test fun realImeKeepsInputAcrossAllSixStageFeedbackConditions() = runBlocking {
        val context = ins.targetContext
        check(context.packageName.endsWith(".feature.testbench")) { "Requires isolated Feature testbench" }
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val previousIme = shell("settings get secure default_input_method").trim()
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == ime }
        val db = AppModule.providesLearnDatabase(context)
        val repository = KeyboardRepository(db.keyboardLayoutDao())
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            // Templates use label-indexed maps. The editor converts those maps to the
            // stable key IDs required by persistence, just as the real template UI does.
            val editor = KeyboardEditorViewModel(repository)
            editor.applyTemplate(KeyboardDefaultLayouts.createFlickKanaTemplateLayout(false))
            val layout = editor.uiState.value.layout.copy(isDirectMode = true)
            val aKey = layout.keys.single { it.label == "あ" }
            check(layout.flickKeyMaps[aKey.keyId].orEmpty().isNotEmpty()) {
                "The saved test layout must contain actions for the あ key ID"
            }
            repository.saveLayout(layout, "Stage feedback smoke", null)
            shell("ime enable $ime")
            shell("ime set $ime")
            for (suppress in listOf(false, true)) for (mode in listOf("application", "composite", "off")) {
                check(prefs.edit()
                    .putString("keyboard_order_preference", "[\"CUSTOM\"]")
                    .putBoolean("save_last_used_keyboard", false)
                    .putBoolean("keyboard_floating_preference", false)
                    .putBoolean("flick_input_only_preference", false)
                    .putBoolean("live_conversion_preference", false)
                    .putBoolean(CustomHapticPreferences.KEY_VIBRATION_ENABLED, true)
                    .putBoolean(CustomHapticPreferences.KEY_STAGE_ENABLED, true)
                    .putBoolean(CustomHapticPreferences.KEY_STAGE_SUPPRESSION, suppress)
                    .putString(CustomHapticPreferences.KEY_STAGE_CHANGE_MODE, mode)
                    .putInt(CustomHapticPreferences.KEY_STAGE_DELAY_MS, 200).commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                    val bounds = stableKey()
                    gesture(bounds, false)
                    assertEquals("$suppress/$mode tap", "あ", text(scenario))
                    gesture(bounds, true)
                    assertEquals("$suppress/$mode direction change", "あえ", text(scenario))
                    SystemClock.sleep(250)
                    assertEquals("$suppress/$mode after reservation cancellation", "あえ", text(scenario))
                }
            }
        } finally {
            if (previousIme.isNotEmpty() && previousIme != "null") shell("ime set $previousIme")
            if (!wasEnabled) shell("ime disable $ime")
            db.close()
        }
    }
}
