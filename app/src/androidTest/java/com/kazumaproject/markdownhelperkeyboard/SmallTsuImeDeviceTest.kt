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
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorViewModel
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses the real IME and editor in an isolated disposable Feature testbench.
 * Run :app:connectedFullStandardDebugAndroidTest with -PfeatureDeviceTest=true
 * and -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.SmallTsuImeDeviceTest.
 */
@RunWith(AndroidJUnit4::class)
class SmallTsuImeDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
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
        error("Missing key $label: " + nodes().joinToString { "${it.text}/${it.contentDescription}" })
    }
    private fun awaitStableKeyboard() {
        var previous: Rect? = null
        var unchanged = 0
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            val bounds = key("あ")
            unchanged = if (bounds == previous) unchanged + 1 else 0
            if (unchanged >= 3) return
            previous = bounds
            SystemClock.sleep(100)
        }
        error("Custom keyboard geometry did not settle")
    }
    private fun tap(rect: Rect) {
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(25)
        }
        SystemClock.sleep(35)
    }
    private fun cell(column: Int, row: Int): Rect {
        val a = key("あ")
        val ka = key("か")
        val ta = key("た")
        return Rect(a).apply {
            offset((column - 1) * (ka.centerX() - a.centerX()), row * (ta.centerY() - a.centerY()))
        }
    }
    private fun flickLeft(rect: Rect) {
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                rect.exactCenterX() - (if (action == MotionEvent.ACTION_DOWN) 0f else rect.width().toFloat()), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
            SystemClock.sleep(30)
        }
        SystemClock.sleep(35)
    }
    private fun text(scenario: ActivityScenario<FastInputHostActivity>): String {
        var result = ""
        scenario.onActivity { result = it.editText.text.toString() }
        return result
    }

    @Test fun realImeAndEditor_doubleTapAcrossKanaSurfaces() = runBlocking {
        val context=ins.targetContext
        check(context.packageName.endsWith(".feature.testbench")) { "Use isolated disposable emulator testbench" }
        val target="${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme=shell("settings get secure default_input_method").trim()
        val prefs=PreferenceManager.getDefaultSharedPreferences(context)
        val db=AppModule.providesLearnDatabase(context)
        val repository=KeyboardRepository(db.keyboardLayoutDao())
        automation.serviceInfo=automation.serviceInfo.apply {
            flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val editor=KeyboardEditorViewModel(repository)
        editor.applyTemplate(KeyboardDefaultLayouts.createFlickKanaTemplateLayout(isDefaultKey=true))
        repository.saveLayout(editor.uiState.value.layout,"Small tsu device test",null)
        try {
            shell("ime enable $target");shell("ime set $target")
            val cases=listOf(Triple("TENKEY","default",false),Triple("CUSTOM","default",false),
                Triple("TENKEY","default",true),Triple("CUSTOM","default",true)) +
                listOf("default","circle","sumire","second-flick","third-flick","center-guide-flick")
                    .map { Triple("SUMIRE",it,false) }
            for((surface,style,floating) in cases) for(preview in listOf(false,true)) {
                check(prefs.edit().putString("keyboard_order_preference","[\"$surface\"]")
                    .putBoolean("save_last_used_keyboard",false)
                    .putBoolean("keyboard_floating_preference",floating)
                    .putBoolean("flick_input_only_preference",true)
                    .putString("sumire_keyboard_style_preference",style)
                    .putString("sumire_input_method_preference","flick")
                    .putBoolean("live_conversion_preference",false)
                    .putBoolean("flick_editor_preview_preference",preview)
                    .putBoolean("double_tap_small_tsu_enabled",true)
                    .putInt("double_tap_small_tsu_interval",500).commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context,FastInputHostActivity::class.java)).use { scenario ->
                    android.util.Log.i("SmallTsuTest","surface=$surface style=$style floating=$floating preview=$preview")
                    awaitStableKeyboard()
                    val ka=key("か");val ta=key("た");val a=key("あ")
                    tap(ka);assertEquals("first immediate $surface preview=$preview","か",text(scenario))
                    tap(ka);assertEquals("pair $surface preview=$preview","っか",text(scenario))
                    tap(ka);tap(ka);assertEquals("four $surface preview=$preview","っかっか",text(scenario))
                    tap(a);tap(ta);flickLeft(ta)
                    assertEquals("flick $surface preview=$preview","っかっかあっち",text(scenario))
                    // Force an external caret movement: the pending first input may not be replaced.
                    SystemClock.sleep(550);tap(ka)
                    val beforeMove=text(scenario)
                    scenario.onActivity { it.editText.setSelection(0) }
                    SystemClock.sleep(80)
                    tap(ka)
                    assertEquals("external cursor must preserve the old text", "か$beforeMove",text(scenario))
                }
                // Allow a live conversion display update between taps, then check the
                // canonical reading by appending with live display turned off.
                check(prefs.edit().putBoolean("live_conversion_preference",true).commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context,FastInputHostActivity::class.java)).use { scenario ->
                    awaitStableKeyboard()
                    val ka=key("か");val a=key("あ")
                    tap(ka);SystemClock.sleep(200);tap(ka)
                    SystemClock.sleep(600)
                    check(prefs.edit().putBoolean("live_conversion_preference",false).commit())
                    SystemClock.sleep(80);tap(a)
                    assertEquals("live reading $surface/$style floating=$floating preview=$preview","っかあ",text(scenario))
                    SystemClock.sleep(600)
                    assertEquals("late candidates must not restore first input","っかあ",text(scenario))
                }
            }
        } finally {
            if(oldIme.isNotEmpty() && oldIme!="null") shell("ime set $oldIme")
            db.close()
        }
    }
}
