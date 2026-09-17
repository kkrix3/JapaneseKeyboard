package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorViewModel
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Uses the real IME and editor in an isolated disposable Feature testbench.
 * Run :app:connectedFullStandardDebugAndroidTest with -PfeatureDeviceTest=true
 * and -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.SmallTsuImeDeviceTest.
 */
@RunWith(AndroidJUnit4::class)
class SmallTsuImeDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private val keyWindowIds = mutableMapOf<Rect, Int>()
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
            }?.let { node ->
                return Rect().also { bounds ->
                    node.getBoundsInScreen(bounds)
                    keyWindowIds[Rect(bounds)] = node.windowId
                }
            }
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
    private data class StrokeTime(val down: Long, val up: Long)

    private fun keyboardRootAt(rect: Rect): View {
        fun containsKeyboard(view: View): Boolean {
            if (!view.isShown) return false
            if (view is FlickKeyboardView || view is TenKey) return true
            return view is ViewGroup && (0 until view.childCount).any { containsKeyboard(view.getChildAt(it)) }
        }
        // Floating mode can leave both the docked IME and popup roots attached.
        // Match the accessibility window that supplied the actual visible key,
        // rather than choosing an arbitrary overlapping keyboard root.
        val expectedWindow = checkNotNull(keyWindowIds[rect])
        val windows = WindowInspector.getGlobalWindowViews().map { root ->
            val node = root.createAccessibilityNodeInfo()
            val id = node?.windowId ?: -1
            node?.recycle()
            root to id
        }
        val roots = windows.filter { (root, id) ->
            id == expectedWindow && root.windowVisibility == View.VISIBLE && containsKeyboard(root)
        }.map { it.first }
        check(roots.size == 1) {
            "Expected visible IME accessibility window $expectedWindow at $rect; roots=" +
                windows.joinToString { (view, id) -> "$id/${view.windowVisibility}/${view.javaClass.simpleName}" }
        }
        return roots.single()
    }

    private fun tap(rect: Rect) {
        // Keep the OS path for independent immediate-input and editor-caret cases.
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val motion = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                rect.exactCenterX(), rect.exactCenterY(), 0)
            motion.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(motion, true)) } finally { motion.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(25)
        }
        SystemClock.sleep(35)
    }

    private fun tapPair(rect: Rect, flickSecond: Boolean = false, gapMillis: Long = 80) {
        var root: View? = null
        // Select the actual key's window before starting the gesture clock.
        ins.runOnMainSync { root = keyboardRootAt(rect) }
        val target = checkNotNull(root)
        val handler = Handler(Looper.getMainLooper())
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        fun enqueueStroke(flick: Boolean, last: Boolean): StrokeTime {
            val start = SystemClock.uptimeMillis()
            var up = start
            val actions = if (flick) listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)
                else listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)
            for (action in actions) {
                val time = SystemClock.uptimeMillis()
                if (action == MotionEvent.ACTION_UP) up = time
                val motion = MotionEvent.obtain(start, time, action,
                    rect.exactCenterX() - (if (flick && action != MotionEvent.ACTION_DOWN) rect.width().toFloat() else 0f),
                    rect.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                // Queue timestamped events as the OS does. Waiting for each main-thread
                // dispatch would turn test-runner overhead into an artificial long hold.
                check(handler.post {
                    try {
                        val position = IntArray(2); target.getLocationOnScreen(position)
                        motion.offsetLocation(-position[0].toFloat(), -position[1].toFloat())
                        check(target.dispatchTouchEvent(motion))
                    } catch (error: Throwable) { failure.compareAndSet(null, error) }
                    finally {
                        motion.recycle()
                        if (last && action == MotionEvent.ACTION_UP) finished.countDown()
                    }
                })
                if (action != MotionEvent.ACTION_UP) SystemClock.sleep(25)
            }
            return StrokeTime(start, up)
        }
        val first = enqueueStroke(flick = false, last = false)
        SystemClock.sleep(gapMillis)
        val second = enqueueStroke(flick = flickSecond, last = true)
        val interval = second.down - first.up
        android.util.Log.i("SmallTsuTest", "injected pair gap=${interval}ms firstHold=${first.up-first.down}ms")
        check(finished.await(5, TimeUnit.SECONDS)) { "Real IME did not process the queued gesture" }
        failure.get()?.let { throw it }
        assertTrue("Injected pair must be within the configured 500ms: $interval", interval in 0L..500L)
        assertTrue("First tap must not become a 300ms hold", first.up-first.down in 0L..299L)
        SystemClock.sleep(35)
    }

    private fun text(scenario: ActivityScenario<FastInputHostActivity>): String {
        var result = ""
        scenario.onActivity { result = it.editText.text.toString() }
        return result
    }

    private fun awaitLiveText(scenario: ActivityScenario<FastInputHostActivity>, expected:String, label:String) {
        val deadline=SystemClock.uptimeMillis()+3000
        while(SystemClock.uptimeMillis()<deadline) {
            if(text(scenario)==expected)return
            SystemClock.sleep(25)
        }
        assertEquals(label,expected,text(scenario))
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
            val failures=mutableListOf<String>()
            for((surface,style,floating) in cases) for(preview in listOf(false,true)) {
                try {
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
                // The existing IME returns early from onUpdateSelection while a composing
                // range exists. Measure its OFF behavior, rather than requiring this feature
                // to change the unrelated editor-caret implementation.
                check(prefs.edit().putBoolean("double_tap_small_tsu_enabled",false).commit())
                var offCursorResult=""
                ActivityScenario.launch<FastInputHostActivity>(Intent(context,FastInputHostActivity::class.java)).use { scenario ->
                    awaitStableKeyboard()
                    tap(key("あ"));val ka=key("か");tap(ka)
                    assertEquals("OFF precondition","あか",text(scenario))
                    scenario.onActivity { it.editText.setSelection(0) };SystemClock.sleep(80);tap(ka)
                    offCursorResult=text(scenario)
                    assertTrue("OFF must preserve the text and add one kana: $offCursorResult",
                        offCursorResult in setOf("かあか","あかか"))
                }
                check(prefs.edit().putBoolean("double_tap_small_tsu_enabled",true).commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context,FastInputHostActivity::class.java)).use { scenario ->
                    android.util.Log.i("SmallTsuTest","surface=$surface style=$style floating=$floating preview=$preview")
                    awaitStableKeyboard()
                    val ka=key("か");val ta=key("た");val a=key("あ")
                    tap(ka);assertEquals("first immediate $surface preview=$preview","か",text(scenario))
                    // Keep the immediate first-input assertion independent of the timed
                    // pair: ActivityScenario/UiAutomation round trips can exceed 500ms.
                    SystemClock.sleep(550)
                    tapPair(ka);assertEquals("pair after a pause $surface preview=$preview","かっか",text(scenario))
                    tapPair(ka);assertEquals("two nonoverlapping pairs $surface preview=$preview","かっかっか",text(scenario))
                    tap(a);tapPair(ta,flickSecond=true)
                    assertEquals("flick $surface preview=$preview","かっかっかあっち",text(scenario))
                    // Force an external caret movement: the pending first input may not be replaced.
                    SystemClock.sleep(550);tap(ka)
                    val beforeMove=text(scenario)
                    scenario.onActivity { it.editText.setSelection(0) }
                    SystemClock.sleep(80)
                    tap(ka)
                    val expected=if(offCursorResult=="かあか") "か$beforeMove" else "${beforeMove}か"
                    assertEquals("external cursor must preserve text and match feature OFF", expected,text(scenario))
                }
                // Allow a live display update between taps, then append a different key.
                // The existing live conversion display is asynchronous; wait for that
                // result, while the non-live cases above still assert immediate input.
                check(prefs.edit().putBoolean("live_conversion_preference",true).commit())
                ActivityScenario.launch<FastInputHostActivity>(Intent(context,FastInputHostActivity::class.java)).use { scenario ->
                    awaitStableKeyboard()
                    val ka=key("か")
                    tapPair(ka,gapMillis=200)
                    SystemClock.sleep(600)
                    tap(key("あ"))
                    awaitLiveText(scenario,"っかあ","live reading $surface/$style floating=$floating preview=$preview")
                    SystemClock.sleep(600)
                    assertEquals("late candidates must not restore first input","っかあ",text(scenario))
                }
                android.util.Log.i("SmallTsuTest","PASS $surface/$style floating=$floating preview=$preview")
                } catch(failure:AssertionError) {
                    val label="$surface/$style floating=$floating preview=$preview: ${failure.message}"
                    failures.add(label)
                    android.util.Log.e("SmallTsuTest",label,failure)
                }
            }
            assertTrue(failures.joinToString("\n"),failures.isEmpty())
        } finally {
            if(oldIme.isNotEmpty() && oldIme!="null") shell("ime set $oldIme")
            db.close()
        }
    }
}
