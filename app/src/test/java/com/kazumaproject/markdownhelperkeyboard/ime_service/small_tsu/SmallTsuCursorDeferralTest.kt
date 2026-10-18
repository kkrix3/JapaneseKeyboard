package com.kazumaproject.markdownhelperkeyboard.ime_service.small_tsu

import android.app.Application
import android.content.Context
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.small_tsu.KanaGestureObserver
import com.kazumaproject.core.domain.small_tsu.SmallTsuSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor.CursorInputSynchronization
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SmallTsuCursorDeferralTest {
    private fun checkQueuedPair(cancelBetween: Boolean = false, resetBeforeReplay: Boolean = false) = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreference.init(context)
        val ime = Robolectric.buildService(IMEService::class.java).get()
        ime.appPreference = AppPreference
        val editor = mock<InputConnection>()
        ReflectionHelpers.setField(ime, "mStartedInputConnection", editor)
        assertSame(editor, ime.currentInputConnection)
        ReflectionHelpers.setField(ime, "isFlickOnlyMode", true)
        ReflectionHelpers.setField(ime, "smallTsuSettings", SmallTsuSettings(enabled = true, intervalMillis = 500))
        ReflectionHelpers.setField(ime, "cursorInputTracking", true)
        ReflectionHelpers.getField<SmallTsuSelectionGuard>(ime, "smallTsuSelection").reset(0, 0)
        val reading = ReflectionHelpers.getField<ReadingStateFlow>(ime, "_inputString")
        val observer = ReflectionHelpers.getField<KanaGestureObserver>(ime, "kanaGestureObserver")
        val sync = CursorInputSynchronization(this, { editor }, { 0L }, Mutex(), { null },
            StandardTestDispatcher(testScheduler))
        ReflectionHelpers.setField(ime, "cursorSynchronization\$delegate", lazyOf(sync))
        try {
            // The real synchronization class queues the entire gesture before any
            // editor read completes. DOWN must observe the preceding queued write.
            sync.verifyNotification(0, 0) { _, _ -> }
            val replacements = mutableListOf<Pair<String, String>?>()
            fun stroke(down: Long) {
                observer.down("kana-ka", down)
                observer.text("か", true, down + 20) {
                    val replacement = ReflectionHelpers.getField<Pair<String, String>?>(ime, "smallTsuReplacement")
                    replacements.add(replacement)
                    reading.value = replacement?.second ?: reading.value + "か"
                }
            }
            stroke(100)
            if (cancelBetween) observer.cancel()
            stroke(200)
            assertEquals("", reading.value)
            assertTrue(replacements.isEmpty())
            if (resetBeforeReplay) sync.reset()
            advanceUntilIdle()
            if (resetBeforeReplay) {
                assertEquals("", reading.value)
                assertTrue(replacements.isEmpty())
            } else {
                assertEquals(if (cancelBetween) "かか" else "っか", reading.value)
                assertEquals(listOf(null, if (cancelBetween) null else "か" to "っか"), replacements)
            }
            assertNull(ReflectionHelpers.getField<Pair<String, String>?>(ime, "smallTsuReplacement"))
        } finally {
            sync.reset()
            listOf("scope", "ioScope").forEach { ReflectionHelpers.getField<CoroutineScope>(ime, it).cancel() }
        }
    }

    @Test fun queuedPairKeepsReplacementAliveUntilItsDispatch() = checkQueuedPair()
    @Test fun queuedCancellationPreventsCrossGestureReplacement() = checkQueuedPair(cancelBetween = true)
    @Test fun editorSessionResetDropsQueuedGestures() = checkQueuedPair(resetBeforeReplay = true)
}
