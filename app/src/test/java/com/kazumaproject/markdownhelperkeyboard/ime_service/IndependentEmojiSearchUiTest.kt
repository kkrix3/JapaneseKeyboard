package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import org.robolectric.Robolectric
import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.emoji.search.EmojiSearchEngine
import com.kazumaproject.emoji.search.EmojiSearchResult
import com.kazumaproject.symbol_keyboard.CustomSymbolKeyboardView
import com.kazumaproject.symbol_keyboard.SymbolAdapter
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class IndependentEmojiSearchUiTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
        com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        init { registry.currentState = Lifecycle.State.RESUMED }
    }
    private fun view(engine: EmojiSearchEngine = EmojiSearchEngine { _, _ -> emptyList() }): Pair<CustomSymbolKeyboardView, Owner> {
        val owner = Owner()
        val view = CustomSymbolKeyboardView(context)
        view.setLifecycleOwner(owner)
        view.setSymbolLists(listOf(Emoji("🙏", EmojiCategory.PEOPLE_BODY)), emptyList(), emptyList(), emptyList(), emptyList())
        ReflectionHelpers.setField(view, "searchEngineLoad", CompletableDeferred(engine))
        return view to owner
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        do {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
            if (condition()) return
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        assertTrue("Timed out waiting for search", condition())
    }
    private fun text(view: CustomSymbolKeyboardView) = view.findViewById<TextView>(com.kazumaproject.symbol_keyboard.R.id.emoji_search_query).text.toString()

    @Test fun privateSearchConsumesPhysicalKeysWithoutTouchingEditorCompositionOrCursor() {
        val (view, owner) = view()
        val appEditor = EditText(context).apply { setText("前後"); setSelection(1) }
        val connection = spy(appEditor.onCreateInputConnection(EditorInfo())!!)
        connection.setComposingText("みかん", 1)
        val before = appEditor.text.toString()
        val caret = appEditor.selectionStart
        val composingStart = BaseInputConnection.getComposingSpanStart(appEditor.text)
        val composingEnd = BaseInputConnection.getComposingSpanEnd(appEditor.text)
        clearInvocations(connection)
        val service = spy(IMEService())
        doReturn(connection).whenever(service).getCurrentInputConnection()
        ReflectionHelpers.setField(service, "emojiSearchView", view)
        val composition = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString")
        composition.value = "みかん"
        view.openEmojiSearch()
        view.appendEmojiSearchText("かんしゃ")
        for (code in listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK)) {
            assertTrue(service.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code)))
            assertTrue(service.onKeyUp(code, KeyEvent(KeyEvent.ACTION_UP, code)))
        }
        assertFalse(view.isEmojiSearchOpen)
        assertEquals(before, appEditor.text.toString())
        assertEquals(caret, appEditor.selectionStart)
        assertEquals(composingStart, BaseInputConnection.getComposingSpanStart(appEditor.text))
        assertEquals(composingEnd, BaseInputConnection.getComposingSpanEnd(appEditor.text))
        assertEquals("みかん", composition.value)
        verifyNoInteractions(connection)
        view.release(); owner.registry.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun deletionReturnAndCancelStayLocalAndViewContainsNoEditableInput() {
        val (view, owner) = view()
        var editorDeletes = 0; var editorReturns = 0
        view.setOnDeleteButtonSymbolViewClickListener(object : DeleteButtonSymbolViewClickListener { override fun onClick() { editorDeletes++ } })
        view.setOnReturnToTenKeyButtonClickListener(object : ReturnToTenKeyButtonClickListener { override fun onClick() { editorReturns++ } })
        fun containsEditor(v: View): Boolean = v is EditText || v is ViewGroup && (0 until v.childCount).any { containsEditor(v.getChildAt(it)) }
        assertFalse(containsEditor(view))
        view.openEmojiSearch(); view.appendEmojiSearchText("ありがとう")
        val delete = view.findViewById<View>(com.kazumaproject.symbol_keyboard.R.id.symbol_keyboard_delete_key)
        fun touch(action: Int) { MotionEvent.obtain(0, 1, action, 1f, 1f, 0).let { delete.dispatchTouchEvent(it); it.recycle() } }
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        assertEquals("ありがと", text(view))
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_CANCEL)
        assertEquals("ありがと", text(view))
        touch(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        touch(MotionEvent.ACTION_UP)
        assertEquals(context.getString(com.kazumaproject.symbol_keyboard.R.string.emoji_search_hint), text(view))
        view.findViewById<View>(com.kazumaproject.symbol_keyboard.R.id.return_jp_keyboard_button).performClick()
        assertFalse(view.isEmojiSearchOpen)
        assertEquals(0, editorDeletes); assertEquals(0, editorReturns)
        view.release(); owner.registry.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun aHiddenSymbolSurfaceDropsItsQueryAndReleasesTheSearchTarget() {
        val (view, owner) = view()
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        activity.get().setContentView(view)
        val targets = mutableListOf<Boolean>()
        view.setOnEmojiSearchStateChanged { targets.add(it) }
        view.openEmojiSearch(); view.appendEmojiSearchText("かんしゃ")
        view.visibility = View.GONE
        assertFalse(view.isEmojiSearchOpen)
        assertEquals(listOf(true, false), targets)
        view.visibility = View.VISIBLE
        view.openEmojiSearch()
        assertEquals(context.getString(com.kazumaproject.symbol_keyboard.R.string.emoji_search_hint), text(view))
        view.release(); owner.registry.currentState = Lifecycle.State.DESTROYED
        activity.pause().stop().destroy()
    }

    @Test fun openingSearchCancelsAnAlreadyHeldEditorDeleteAndConsumesItsRelease() {
        val (view, owner) = view()
        var clicks = 0; var repeats = 0; var stops = 0
        view.setOnDeleteButtonSymbolViewClickListener(object : DeleteButtonSymbolViewClickListener { override fun onClick() { clicks++ } })
        view.setOnDeleteButtonSymbolViewLongClickListener(object : DeleteButtonSymbolViewLongClickListener { override fun onLongClickListener() { repeats++ } })
        view.setOnDeleteButtonFingerUpListener { stops++ }
        val delete = view.findViewById<View>(com.kazumaproject.symbol_keyboard.R.id.symbol_keyboard_delete_key)
        fun touch(action: Int) { MotionEvent.obtain(0, 1, action, 1f, 1f, 0).let { delete.dispatchTouchEvent(it); it.recycle() } }
        // Search opens before the old long-press callback runs.
        touch(MotionEvent.ACTION_DOWN); view.openEmojiSearch()
        view.appendEmojiSearchText("あ")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        touch(MotionEvent.ACTION_UP)
        assertEquals(0, repeats); assertEquals(0, clicks); assertEquals("あ", text(view))
        view.closeEmojiSearch()
        // Search opens after the existing editor-repeat callback has begun.
        touch(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        assertEquals(1, repeats)
        view.openEmojiSearch(); view.appendEmojiSearchText("い")
        assertEquals(1, stops)
        touch(MotionEvent.ACTION_UP)
        assertEquals(0, clicks); assertEquals("い", text(view))
        view.release(); owner.registry.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun oldWorkerCannotReplaceNewQueryAndClosingClearsSearchState() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val (view, owner) = view(EmojiSearchEngine { q, _ ->
            if (q == "古い") { entered.countDown(); check(release.await(8, TimeUnit.SECONDS)) }
            listOf(EmojiSearchResult(q, if (q == "古い") "😢" else "🙏", q, 1000))
        })
        val adapter = ReflectionHelpers.getField<SymbolAdapter>(view, "symbolAdapter")
        try {
            view.openEmojiSearch(); view.appendEmojiSearchText("古い")
            await { entered.count == 0L }
            view.findViewById<View>(com.kazumaproject.symbol_keyboard.R.id.emoji_search_clear).performClick()
            view.appendEmojiSearchText("新しい")
            await { adapter.snapshot().items == listOf("🙏") }
            release.countDown()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            assertEquals(listOf("🙏"), adapter.snapshot().items)
            view.closeEmojiSearch()
            assertFalse(view.isEmojiSearchOpen)
            view.openEmojiSearch()
            assertEquals(context.getString(com.kazumaproject.symbol_keyboard.R.string.emoji_search_hint), text(view))
        } finally {
            release.countDown(); view.release(); owner.registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
