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
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorViewModel
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.Test

/**
 * Smoke test for the actual IME in the isolated Feature testbench.
 * Kept on the verification branch; the PR feature branch does not include this file.
 */
@RunWith(AndroidJUnit4::class)
class SpecialFlickFeatureImeSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }

    private fun visibleNodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        automation.windows.forEach { window -> window.root?.let(::visit) }
        return result
    }

    private fun key(label: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            visibleNodes().firstOrNull { node ->
                node.packageName?.toString() == instrumentation.targetContext.packageName &&
                    node.isVisibleToUser && node.isClickable &&
                    (node.text?.toString()?.lineSequence()?.firstOrNull()?.trim() == label ||
                        node.contentDescription?.toString()?.trim() == label)
            }?.let { node ->
                return Rect().also(node::getBoundsInScreen)
            }
            SystemClock.sleep(100)
        }
        error("Visible IME key missing: $label; nodes=" +
            visibleNodes().joinToString { "${it.text}/${it.contentDescription}" })
    }

    private fun tap(rect: Rect) {
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(
                start, SystemClock.uptimeMillis(), action,
                rect.exactCenterX(), rect.exactCenterY(), 0
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                check(automation.injectInputEvent(event, true)) { "Could not inject IME touch" }
            } finally {
                event.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(25)
        }
    }

    private fun flickUpAfterHold(rect: Rect) {
        val downTime = SystemClock.uptimeMillis()
        val x = rect.exactCenterX()
        val startY = rect.exactCenterY()
        val upY = startY - maxOf(rect.height() * 1.5f, 120f)
        fun inject(action: Int, y: Float) {
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, x, y, 0
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                check(automation.injectInputEvent(event, true)) { "Could not inject held flick" }
            } finally {
                event.recycle()
            }
        }
        inject(MotionEvent.ACTION_DOWN, startY)
        SystemClock.sleep(700)
        inject(MotionEvent.ACTION_MOVE, upY)
        SystemClock.sleep(50)
        inject(MotionEvent.ACTION_UP, upY)
    }

    @Test(timeout = 120_000)
    fun savedCustomKeyboardInputsKanaInRealIme() = runBlocking {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".feature.testbench")) { "Use isolated Feature testbench" }
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val database = AppModule.providesLearnDatabase(context)
        try {
            val repository = KeyboardRepository(database.keyboardLayoutDao())
            val editor = KeyboardEditorViewModel(repository)
            editor.applyTemplate(KeyboardDefaultLayouts.createFlickKanaTemplateLayout(isDefaultKey = true))
            val base = editor.uiState.value.layout
            val special = base.keys.first { it.label == "さ" }.copy(
                label = "試験",
                keyId = "pr4_special",
                keyType = KeyType.CROSS_FLICK,
                action = KeyAction.ToggleDakuten,
                isSpecialKey = true
            )
            val layout = base.copyWithKeys(
                base.keys.map { if (it.label == "さ") special else it }
            ).copy(
                flickKeyMaps = base.flickKeyMaps + (
                    "pr4_special" to listOf(mapOf(
                        FlickDirection.TAP to FlickAction.Action(KeyAction.ToggleDakuten),
                        FlickDirection.UP to FlickAction.Input("っ")
                    ))
                )
            )
            repository.saveLayout(layout, "PR 4 IME held flick test", null)
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            check(preferences.edit()
                .putString("keyboard_order_preference", """["CUSTOM"]""")
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("flick_input_only_preference", true)
                .commit())
            shell("ime enable $target")
            shell("ime set $target")
            ActivityScenario.launch<FastInputHostActivity>(
                Intent(context, FastInputHostActivity::class.java)
            ).use { scenario ->
                val a = key("あ")
                val ka = key("か")
                tap(a)
                tap(ka)
                var actual = ""
                scenario.onActivity { activity -> actual = activity.editText.text.toString() }
                assertEquals("Saved custom keyboard should reach the editor", "あか", actual)
                flickUpAfterHold(key("試験"))
                scenario.onActivity { activity -> actual = activity.editText.text.toString() }
                assertEquals("Held Tap action runs before Up text is committed", "あがっ", actual)
            }
        } finally {
            if (oldIme.isNotEmpty() && oldIme != "null") shell("ime set $oldIme")
            database.close()
        }
    }
}
