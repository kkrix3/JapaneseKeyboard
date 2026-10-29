package com.kazumaproject.symbol_keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.domain.EmojiSkinToneSupport
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class GoogleEmojiRenderingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val samples = listOf("😀", "❤\uFE0F", "☀\uFE0F", "👨‍👩‍👧‍👦", "👩🏽‍💻", "👍🏿", "🇯🇵",
        "1\uFE0F\u20E3", "🏳️‍🌈", "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F")
    private val output get() = File(context.getExternalFilesDir(null), "emoji-rendering").apply { mkdirs() }

    private val restartPhase get() = InstrumentationRegistry.getArguments().getString("emojiRestartPhase")
    @Before fun setup() {
        if (restartPhase == null) prefs.edit().remove(EmojiRenderingStyle.PREFERENCE_KEY).commit()
    }
    @After fun cleanup() {
        if (restartPhase != "save") prefs.edit().remove(EmojiRenderingStyle.PREFERENCE_KEY)
            .remove("emoji_restart_test_pid").commit()
    }

    @Test fun persistedGoogleSelectionSurvivesNewProcess() {
        if (restartPhase == "save") {
            setStyle(EmojiRenderingStyle.GOOGLE)
            assertTrue(prefs.edit().putInt("emoji_restart_test_pid", android.os.Process.myPid()).commit())
            return
        }
        if (restartPhase == "verify") {
            assertNotEquals("Instrumentation must use a fresh process", android.os.Process.myPid(),
                prefs.getInt("emoji_restart_test_pid", -1))
            assertEquals(EmojiRenderingStyle.GOOGLE, EmojiRenderingStyle.read(prefs))
        } else setStyle(EmojiRenderingStyle.GOOGLE)
        ActivityScenario.launch(EmojiTestActivity::class.java).use { scenario ->
            awaitFont(scenario, "👩🏽‍💻", Typeface.create(GoogleEmojiFont.get(context), Typeface.BOLD))
        }
    }

    @Test fun bundledColorGlyphsAndSequencesRenderWithoutOemFallback() {
        val font = GoogleEmojiFont.get(context)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 96f }
        for ((index, sample) in samples.withIndex()) {
            assertTrue("Missing glyph: $sample", paint.hasGlyph(sample))
            if (Build.VERSION.SDK_INT >= 31) assertBundledSingleGlyph(sample, paint)
            val bitmap = render(sample, font)
            var colored = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val c = bitmap.getPixel(x, y)
                if (Color.alpha(c) > 100 && maxOf(Color.red(c), Color.green(c), Color.blue(c)) -
                    minOf(Color.red(c), Color.green(c), Color.blue(c)) > 30) colored++
            }
            assertTrue("Monochrome/empty glyph: $sample", colored > 30)
            save(bitmap, "google-$index.png")
            save(render(sample, Typeface.DEFAULT), "device-$index.png")
        }
    }

    @RequiresApi(31) private fun assertBundledSingleGlyph(text: String, paint: Paint) {
        val shaped = TextRunShaper.shapeTextRun(text, 0, text.length, 0, text.length, 0f, 0f, false, paint)
        assertEquals("Sequence split: $text", 1, shaped.glyphCount())
        val bytes = shaped.getFont(0).buffer.duplicate().apply { rewind() }
        val hash = MessageDigest.getInstance("SHA-256").apply { update(bytes) }.digest()
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals("Used fallback font: $text", FONT_SHA256, hash)
    }

    @Test fun livePreferenceRestartHistoryPopupAndRecycledModes() {
        ActivityScenario.launch(EmojiTestActivity::class.java).use { scenario ->
            val history = "👩🏽‍💻"
            var original: Typeface? = null
            awaitCell(scenario, history)
            scenario.onActivity { original = cell(it).typeface }
            setStyle(EmojiRenderingStyle.GOOGLE)
            awaitFont(scenario, history, Typeface.create(GoogleEmojiFont.get(context), original?.style ?: 0))
            scenario.recreate()
            awaitFont(scenario, history, Typeface.create(GoogleEmojiFont.get(context), original?.style ?: 0))
            scenario.onActivity { activity ->
                (cell(activity).parent as View).performClick()
                assertEquals(history, activity.lastClicked?.symbol)
                activity.keyboard.setKeyboardFont(KeyboardFontSnapshot(Typeface.MONOSPACE, 991))
                activity.keyboard.findViewById<TabLayout>(R.id.category_tab_layout).getTabAt(1)!!.select()
            }
            awaitCell(scenario, "👍")
            scenario.onActivity { activity ->
                assertFalse((cell(activity) as com.google.android.material.textview.MaterialTextView).isEmojiCompatEnabled)
                if (Build.VERSION.SDK_INT >= 31) assertBundledSingleGlyph("👍", cell(activity).paint)
                (cell(activity).parent as View).performLongClick()
                val popup = popup(activity.keyboard)
                assertTrue(popup.isShowing)
                val content = popup.contentView as LinearLayout
                val expected = EmojiSkinToneSupport.skinToneVariants("👍")
                assertEquals(6, content.childCount)
                for (i in expected.indices) {
                    val option = content.getChildAt(i) as TextView
                    assertEquals(expected[i], option.text.toString())
                    assertEquals(GoogleEmojiFont.get(context), option.typeface)
                }
                save(draw(content), "skin-tones-google.png")
                content.getChildAt(5).performClick()
                assertEquals(expected[5], activity.lastClicked?.symbol)
                assertFalse(popup.isShowing)
            }
            val dark = EmojiSkinToneSupport.skinToneVariants("👍")[5]
            awaitCell(scenario, dark)
            setStyle(EmojiRenderingStyle.DEVICE)
            awaitFont(scenario, dark, original)
            scenario.onActivity { activity ->
                (cell(activity).parent as View).performLongClick()
                val options = popup(activity.keyboard).contentView as LinearLayout
                assertNotEquals(GoogleEmojiFont.get(context), (options.getChildAt(0) as TextView).typeface)
            }
            setStyle(EmojiRenderingStyle.GOOGLE)
            awaitFont(scenario, dark, Typeface.create(GoogleEmojiFont.get(context), original?.style ?: 0))
            scenario.onActivity { activity ->
                assertFalse(popup(activity.keyboard).isShowing)
                activity.keyboard.findViewById<TabLayout>(R.id.mode_tab_layout).getTabAt(1)!!.select()
            }
            awaitFont(scenario, "(^_^)", Typeface.create(Typeface.MONOSPACE, original?.style ?: 0))
            scenario.onActivity { it.keyboard.findViewById<TabLayout>(R.id.mode_tab_layout).getTabAt(0)!!.select() }
            awaitFont(scenario, history, Typeface.create(GoogleEmojiFont.get(context), original?.style ?: 0))
            scenario.onActivity { save(draw(it.keyboard), "history-google.png") }
            setStyle(EmojiRenderingStyle.DEVICE)
            awaitFont(scenario, history, original)
            scenario.onActivity { save(draw(it.keyboard), "history-device.png") }
        }
    }

    private fun setStyle(style: EmojiRenderingStyle) {
        prefs.edit().putString(EmojiRenderingStyle.PREFERENCE_KEY, style.preferenceValue).commit()
    }
    private fun cell(activity: EmojiTestActivity): TextView = activity.keyboard
        .findViewById<RecyclerView>(R.id.symbol_candidate_recycler_view)
        .findViewHolderForAdapterPosition(0)!!.itemView.findViewById(R.id.symbol_text)
    private fun popup(keyboard: CustomSymbolKeyboardView): PopupWindow =
        CustomSymbolKeyboardView::class.java.getDeclaredField("skinTonePopup").apply { isAccessible = true }
            .get(keyboard) as PopupWindow
    private fun awaitCell(scenario: ActivityScenario<EmojiTestActivity>, expected: String) =
        awaitCell(scenario, expected, false, null)
    private fun awaitFont(scenario: ActivityScenario<EmojiTestActivity>, text: String, font: Typeface?) =
        awaitCell(scenario, text, true, font)
    private fun awaitCell(scenario: ActivityScenario<EmojiTestActivity>, expected: String, checkFont: Boolean, font: Typeface?) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val recycler = activity.keyboard.findViewById<RecyclerView>(R.id.symbol_candidate_recycler_view)
                val view = recycler.findViewHolderForAdapterPosition(0)?.itemView?.findViewById<TextView>(R.id.symbol_text)
                ready = !recycler.isComputingLayout && !recycler.hasPendingAdapterUpdates() &&
                    view?.text?.toString() == expected && (!checkFont || view.typeface == font)
            }
            if (ready) return
            SystemClock.sleep(40)
        }
        fail("Timed out waiting for cell $expected and font=$font")
    }
    private fun render(text: String, font: Typeface): Bitmap {
        lateinit var result: Bitmap
        instrumentation.runOnMainSync {
            val view = TextView(context).apply {
                this.text = text; typeface = font; textSize = 64f
                gravity = Gravity.CENTER; setTextColor(Color.BLACK)
            }
            val spec = View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
            view.measure(spec, spec); view.layout(0, 0, 320, 320)
            result = draw(view)
        }
        return result
    }
    private fun draw(view: View): Bitmap {
        // A just-added PopupWindow has been measured but may not have had its first traversal.
        val width = view.width.takeIf { it > 0 } ?: view.measuredWidth
        val height = view.height.takeIf { it > 0 } ?: view.measuredHeight
        if (view.width == 0 || view.height == 0) view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    private fun save(bitmap: Bitmap, name: String) {
        File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    companion object {
        private const val FONT_SHA256 = "15671215ab769fdc7162a045d56fd7d7e477c51b04e6b3c761d914d8fdd6cc44"
    }
}
