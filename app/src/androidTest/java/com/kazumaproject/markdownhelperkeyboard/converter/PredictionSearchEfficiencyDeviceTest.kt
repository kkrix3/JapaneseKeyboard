package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.*
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PredictionSearchEfficiencyDeviceTest {
    private fun entry(): KanaKanjiEngineEntryPoint {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
    }

    private fun system(): TripleDictionaryData {
        val reader = entry().dictionaryBinaryReader()
        val yomi = reader.loadLoudsWithTermId(DictionaryFileKey.SYSTEM_YOMI)
        val tango = reader.loadLouds(DictionaryFileKey.SYSTEM_TANGO)
        val tokens = reader.loadTokenArray(DictionaryFileKey.SYSTEM_TOKEN)
        return TripleDictionaryData(tango, yomi, tokens, reader.loadSystemYomiLbsIndex(yomi),
            reader.loadSystemYomiLeafIndex(yomi), reader.loadSystemTokenIndex(tokens), reader.loadSystemTangoLbsIndex(tango))
    }

    private fun original(input: String, data: TripleDictionaryData, config: PredictionConfig): List<Candidate> =
        data.yomiTrie.predictiveSearch(input, data.succinctBitVectorLBSYomi)
            .filter { config.acceptsJapaneseReading(input.length, it.length) }.flatMap { reading ->
                val term = data.yomiTrie.getTermId(data.yomiTrie.getNodeIndex(reading, data.succinctBitVectorLBSYomi), data.succinctBitVectorIsLeafYomi)
                data.tokenArray.getListDictionaryByYomiTermId(term, data.succinctBitVectorTokenArray).map { token ->
                    Candidate(string = data.surfaceFor(token.nodeId, reading), type = 9,
                        length = reading.length.toUByte(), score = token.wordCost.toInt() + config.completionPenalty(input.length, reading.length),
                        leftId = data.tokenArray.leftIds[token.posTableIndex.toInt()], rightId = data.tokenArray.rightIds[token.posTableIndex.toInt()])
                }
            }

    @Test fun bundledPackedDictionaryMatchesOriginalCandidatesAndSettingsOnAndroid() {
        val data = system()
        var checked = 0
        for (input in listOf("あい", "こう", "しょう", "きょう", "にほん", "こうつう", "ちゅうしゃじょう", "あ".repeat(16), "あ".repeat(17))) {
            for (lookahead in listOf(1, 3, 6)) for (enabled in listOf(false, true)) {
                val config = PredictionConfig(lookaheadCharacterCount = lookahead, japanesePredictionEnabled = enabled)
                val expected = original(input, data, config).sortedBy { it.score }.take(config.normalizedSystemCandidateLimit)
                val actual = buildDictionaryPredictionCandidates(input, data, config, Job()).sortedBy { it.score }.take(config.normalizedSystemCandidateLimit)
                assertEquals("$input/$config", expected, actual)
                checked++
            }
        }
        println("PREDICTION_EFFICIENCY_ANDROID parity=$checked API=${android.os.Build.VERSION.SDK_INT} model=${android.os.Build.MODEL}")
    }

    @Test fun cancellationPropagatesAndNextRequestRunsOnSameWorker() {
        val data = system()
        val executor = Executors.newSingleThreadExecutor()
        val paused = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val job = Job()
        try {
            val old = executor.submit<Boolean> {
                var polls = 0
                try {
                    data.yomiTrie.predictiveSearch("しょ", data.succinctBitVectorLBSYomi, 8) {
                        if (++polls == 4) { paused.countDown(); check(resume.await(10, TimeUnit.SECONDS)) }
                        job.ensureActive()
                    }
                    false
                } catch (_: CancellationException) { true }
            }
            assertTrue(paused.await(10, TimeUnit.SECONDS))
            job.cancel()
            val next = executor.submit<List<Candidate>> { buildDictionaryPredictionCandidates("にほん", data, PredictionConfig(), Job()) }
            resume.countDown()
            assertTrue(old.get(10, TimeUnit.SECONDS))
            assertEquals(original("にほん", data, PredictionConfig()), next.get(10, TimeUnit.SECONDS))
        } finally { resume.countDown(); executor.shutdownNow() }
    }

    @Test fun exactEmojiAndSymbolReadingsRemainWhenJapanesePredictionIsOff() {
        val entry = entry()
        val reader = entry.dictionaryBinaryReader()
        val method = KanaKanjiEngine::class.java.getDeclaredMethod("deferredPredictionEmojiSymbols",
            String::class.java, LOUDSWithTermId::class.java, SuccinctBitVector::class.java,
            PredictionConfig::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        for (key in listOf(DictionaryFileKey.EMOJI_YOMI, DictionaryFileKey.SYMBOL_YOMI)) {
            val trie = reader.loadLoudsWithTermId(key)
            val lbs = reader.loadYomiLbsIndex(key, trie)
            val readings = trie.predictiveSearch("", lbs).filter { it.isNotEmpty() && it.length <= 16 }.take(8)
            assertTrue(readings.isNotEmpty())
            for (input in readings) {
                @Suppress("UNCHECKED_CAST")
                val actual = method.invoke(entry.kanaKanjiEngine(), input, trie, lbs,
                    PredictionConfig(japanesePredictionEnabled = false), true) as List<String>
                assertEquals(listOf(input), actual)
            }
        }
    }
}
