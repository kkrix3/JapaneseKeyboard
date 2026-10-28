package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.emoji.search.EmojiSearchEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.SymbolKeyboardState
import com.kazumaproject.markdownhelperkeyboard.repository.ClickedSymbolRepository
import com.kazumaproject.symbol_keyboard.CustomSymbolKeyboardView
import com.kazumaproject.symbol_keyboard.SymbolAdapter
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** Real IME/editor/OS touch test; run only in an isolated preview.testbench emulator. */
@RunWith(AndroidJUnit4::class)
class EmojiSearchImeDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        ins.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue(message, condition())
    }
    private fun collect(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { collect(root.getChildAt(it)) } else emptyList()
    private inline fun <reified T : View> allViews(): List<T> = main {
        WindowInspector.getGlobalWindowViews().flatMap(::collect).filterIsInstance<T>()
    }
    private fun touch(view: View, dx: Float = 0f, dy: Float = 0f, hold: Long = 25) {
        val label = main { (view as? TextView)?.text?.toString() ?: view.javaClass.simpleName }
        await("Visible and laid-out control: $label") { main { view.isShown && view.width > 0 && view.height > 0 } }
        fun screenBounds() = main {
            check(view.isShown && view.width > 0 && view.height > 0) { "Search control is not visible: ${view.javaClass.name}" }
            val screen = IntArray(2).also(view::getLocationOnScreen)
            Rect(screen[0], screen[1], screen[0] + view.width, screen[1] + view.height)
        }
        // IME window coordinates can differ from display coordinates. Wait for its
        // show/layout animation, then inject into the same space as MotionEvent.
        var previous = screenBounds()
        var stable = 0
        repeat(30) {
            if (stable < 3) {
                SystemClock.sleep(40)
                val next = screenBounds()
                stable = if (next == previous) stable + 1 else 0
                previous = next
            }
        }
        check(stable >= 3) { "IME control bounds did not settle: $previous" }
        val bounds = previous
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
            val moved = action != MotionEvent.ACTION_DOWN
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX() + if (moved) dx else 0f,
                bounds.exactCenterY() + if (moved) dy else 0f, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            SystemClock.sleep(if (action == MotionEvent.ACTION_DOWN) hold else 20)
        }
    }
    private fun saveProbeScreenshot(name: String) {
        val bitmap = automation.takeScreenshot() ?: return
        try {
            val path = File(ins.targetContext.getExternalFilesDir(null), "$name.png")
            FileOutputStream(path).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(obj: Any, name: String): T = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj) as T

    @Test fun searchKeepsTheRealEditorPrivateCommitsOnlySelectedEmojiAndSurvivesRecreation() {
        val context = ins.targetContext
        check(context.packageName.endsWith(".preview.testbench")) { "Use -PpreviewDeviceTest=true" }
        val component = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        var activity: FastInputHostActivity? = null
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        try {
            check(prefs.edit().putString("keyboard_order_preference", "[\"SUMIRE\"]")
                .putBoolean("save_last_used_keyboard", false).putBoolean("keyboard_floating_preference", false)
                .putString("sumire_keyboard_style_preference", "sumire").putString("sumire_input_method_preference", "flick")
                .putBoolean("live_conversion_preference", false).putBoolean("double_tap_small_tsu_enabled", false).commit())
            shell("ime enable $component"); shell("ime set $component")
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            await("Host activity resumed") {
                activity = main { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().firstOrNull() }
                activity != null
            }
            await("Real IME attached") { allViews<CustomSymbolKeyboardView>().any { it.isAttachedToWindow } }
            val view = allViews<CustomSymbolKeyboardView>().first { it.isAttachedToWindow }
            val service = main {
                var ctx: android.content.Context = view.context
                while (ctx !is IMEService && ctx is ContextWrapper) ctx = ctx.baseContext
                check(ctx is IMEService); ctx
            }
            main { field<MutableStateFlow<SymbolKeyboardState>>(service, "_keyboardSymbolViewState").value = SymbolKeyboardState(true) }
            val recycler = view.findViewById<RecyclerView>(com.kazumaproject.symbol_keyboard.R.id.symbol_candidate_recycler_view)
            val adapter = main { recycler.adapter as SymbolAdapter }
            await("Real emoji inventory loaded") { main { view.isShown && adapter.itemCount > 0 } }
            val editor = activity!!.editText
            // Seed a composing span after entering the existing symbol pane, to isolate search behavior.
            main {
                // TextView.setText invalidates/restarts the served IME on Android 14.
                // Seed through the ordinary connection so setup cannot restart search mid-tap.
                editor.onCreateInputConnection(EditorInfo())!!.apply {
                    beginBatchEdit()
                    commitText("前後", 1)
                    setSelection(1, 1)
                    setComposingText("未確定", 1)
                    endBatchEdit()
                }
            }
            android.util.Log.i("EmojiSearchProbe", "stage=editor-composition-seeded")
            data class Snapshot(val text: String, val start: Int, val end: Int, val compStart: Int, val compEnd: Int)
            fun snapshot() = main { Snapshot(editor.text.toString(), editor.selectionStart, editor.selectionEnd,
                BaseInputConnection.getComposingSpanStart(editor.text), BaseInputConnection.getComposingSpanEnd(editor.text)) }
            val before = snapshot()
            val pssBefore = Debug.getPss()
            val opened = System.nanoTime()
            touch(view.findViewById(com.kazumaproject.symbol_keyboard.R.id.emoji_search_query))
            await("Search toolbar OS tap opens private query") { main { view.isEmojiSearchOpen } }
            android.util.Log.i("EmojiSearchProbe", "stage=search-open")
            fun key(prefix: String) = allViews<TextView>().first { main { it.isShown && it.text.toString().startsWith(prefix) } }
            // OS-injected kana pad gestures: は + な + ひ + dakuten => はなび.
            touch(key("は ひ")); touch(key("な に"))
            touch(key("は ひ"), dx = -24 * context.resources.displayMetrics.density)
            touch(key(context.getString(com.kazumaproject.symbol_keyboard.R.string.emoji_search_modify)))
            await("Fireworks result for kana query") { main { adapter.snapshot().items.contains("🎆") } }
            android.util.Log.i("EmojiSearchProbe", "stage=kana-results")
            val firstResultMs = (System.nanoTime() - opened) / 1e6
            assertEquals(before, snapshot())
            assertEquals("はなび", main { view.findViewById<TextView>(com.kazumaproject.symbol_keyboard.R.id.emoji_search_query).text.toString() })
            touch(view.findViewById(com.kazumaproject.symbol_keyboard.R.id.symbol_keyboard_delete_key))
            assertEquals(before, snapshot())
            shell("input keyevent ${KeyEvent.KEYCODE_DEL}")
            assertEquals(before, snapshot())
            shell("input keyevent ${KeyEvent.KEYCODE_BACK}")
            await("First Back hides local pad") { main { view.findViewById<View>(com.kazumaproject.symbol_keyboard.R.id.emoji_search_pad).visibility == View.GONE } }
            assertTrue(main { view.isEmojiSearchOpen })
            shell("input keyevent ${KeyEvent.KEYCODE_BACK}")
            await("Second Back closes search") { main { !view.isEmojiSearchOpen } }
            assertEquals(before, snapshot())

            // A selected skin-tone variant follows the ordinary emoji commit/history path.
            android.util.Log.i("EmojiSearchProbe", "stage=private-delete-back-passed")
            main {
                editor.onCreateInputConnection(EditorInfo())!!.apply {
                    beginBatchEdit()
                    finishComposingText()
                    setSelection(0, editor.length())
                    commitText("前後", 1)
                    setSelection(1, 1)
                    endBatchEdit()
                }
            }
            touch(view.findViewById(com.kazumaproject.symbol_keyboard.R.id.emoji_search_query))
            main { view.appendEmojiSearchText("かんしゃ"); view.handleEmojiSearchKeyDown(KeyEvent.KEYCODE_ENTER, null) }
            await("Gratitude results") { main { adapter.snapshot().items.contains("🙏") } }
            await("Visible prayer result") { main { recycler.findViewHolderForAdapterPosition(adapter.snapshot().items.indexOf("🙏")) != null } }
            val prayer = main { recycler.findViewHolderForAdapterPosition(adapter.snapshot().items.indexOf("🙏"))!!.itemView }
            touch(prayer, hold = 700)
            await("Skin-tone chooser") { allViews<TextView>().any { main { it.isShown && it.text.toString() == "🙏🏽" } } }
            touch(allViews<TextView>().first { main { it.isShown && it.text.toString() == "🙏🏽" } })
            await("Only selected emoji committed at original caret") { main { editor.text.toString() == "前🙏🏽後" } }
            val history = main { field<ClickedSymbolRepository>(service, "clickedSymbolRepository") }
            await("Selected emoji persisted in history") { runBlocking { history.getAll().any { it.symbol == "🙏🏽" } } }
            assertFalse(runBlocking { history.getAll().any { it.symbol.contains("かんしゃ") || it.symbol.contains("はなび") } })

            android.util.Log.i("EmojiSearchProbe", "stage=emoji-skin-history-passed")
            val engine = runBlocking { field<Deferred<EmojiSearchEngine>>(view, "searchEngineLoad").await() }
            val queries = listOf("お祝い", "かんしゃ", "きまずい", "はなび", "電車で帰る", "unknown-token")
            repeat(4) { queries.forEach { engine.search(it, 100) } }
            val times = (0 until 40).flatMap { queries.map { q -> val t = System.nanoTime(); engine.search(q, 100); (System.nanoTime() - t) / 1e6 } }.sorted()
            android.util.Log.i("EmojiSearchProbe", "API=${android.os.Build.VERSION.SDK_INT} n=${times.size} engine_p50_ms=${times[times.size/2]} engine_p95_ms=${times[(times.size*.95).toInt()]} first_interactive_sequence_ms=$firstResultMs pss_before_kb=$pssBefore pss_after_kb=${Debug.getPss()}")
            saveProbeScreenshot("emoji-search-results")
            // Configuration change must dispose the query and its Back registration.
            automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
            await("Rotation closes private search") { main { !view.isAttachedToWindow || !view.isEmojiSearchOpen } }
            assertTrue(allViews<CustomSymbolKeyboardView>().all { main { !it.isEmojiSearchOpen } })
            android.util.Log.i("EmojiSearchProbe", "stage=rotation-passed")
        } catch (failure: Throwable) {
            android.util.Log.e("EmojiSearchProbe", "Real IME search probe failed", failure)
            runCatching { saveProbeScreenshot("emoji-search-failure") }
            throw failure
        } finally {
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            main { activity?.finish() }
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
        }
    }
}
