package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedCharArray
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.junit.Assert.*
import org.junit.Test
import java.util.BitSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

class DictionaryPredictionSearchTest {
    private fun dictionary(readings: List<String>, packed: Boolean, tokensPerReading: Int = 6): TripleDictionaryData {
        val tree = PrefixTreeWithTermId().apply { readings.forEach(::insert) }
        val raw = ConverterWithTermId().convert(tree.root).apply { convertListToBitSet() }
        val yomi = if (packed) LOUDSWithTermId.fromPacked(raw.LBS,
            PackedCharArray.from(raw.getAllLabels()), raw.isLeaf, PackedIntArray.from(raw.termIds.toIntArray()))
        else LOUDSWithTermId(raw.LBS, raw.getAllLabels(), raw.isLeaf, raw.termIds.toIntArray())
        val bits = BitSet()
        val costs = ArrayList<Short>()
        val positions = ArrayList<Short>()
        var bit = 0
        for (reading in readings) {
            bit++
            repeat(tokensPerReading) { index ->
                bits.set(bit++)
                costs.add(if (reading.length > 10) Short.MIN_VALUE else listOf(2000, 1000, 1000, 0, -3000, -3000)[index % 6].toShort())
                positions.add((index % 6).toShort())
            }
        }
        val tokens = TokenArray.fromPacked(positions.toShortArray(), costs.toShortArray(),
            PackedIntArray.from(IntArray(costs.size) { if (it % 2 == 0) -2 else -1 }), bits,
            ShortArray(6) { (100 + it).toShort() }, ShortArray(6) { (200 + it).toShort() })
        val tango = LOUDS()
        return TripleDictionaryData(tango, yomi, tokens, SuccinctBitVector(yomi.LBS),
            SuccinctBitVector(yomi.isLeaf), SuccinctBitVector(bits), SuccinctBitVector(tango.LBS))
    }

    private val readings = listOf("あいあ", "あいあい", "あいあう", "あいあえお", "あいあえおかきくけこ",
        "あいあ😀", "あ".repeat(16), "あ".repeat(17), "あ".repeat(22), "あ".repeat(23)).sorted()

    /** Original unbounded search, post-filter, token materialization and stable ranking. */
    private fun reference(input: String, data: TripleDictionaryData, config: PredictionConfig): List<Candidate> =
        data.yomiTrie.predictiveSearch(input, data.succinctBitVectorLBSYomi)
            .filter { config.acceptsJapaneseReading(input.length, it.length) }.flatMap { reading ->
                val term = data.yomiTrie.getTermId(data.yomiTrie.getNodeIndex(reading, data.succinctBitVectorLBSYomi), data.succinctBitVectorIsLeafYomi)
                data.tokenArray.getListDictionaryByYomiTermId(term, data.succinctBitVectorTokenArray).map { token ->
                    Candidate(string = data.surfaceFor(token.nodeId, reading), type = 9,
                        length = reading.length.toUByte(), score = token.wordCost.toInt() + config.completionPenalty(input.length, reading.length),
                        leftId = data.tokenArray.leftIds[token.posTableIndex.toInt()], rightId = data.tokenArray.rightIds[token.posTableIndex.toInt()])
                }
            }

    @Test fun boundedSearchPreservesDenseAndPackedDfsOrderIncludingUtf16Boundaries() {
        for (packed in listOf(false, true)) {
            val data = dictionary(readings, packed)
            for (prefix in listOf("", "あ", "あい", "あいあ", "あいあ😀", "ない")) for (limit in 0..24) {
                assertEquals("$packed/$prefix/$limit",
                    data.yomiTrie.predictiveSearch(prefix, data.succinctBitVectorLBSYomi).filter { it.length <= limit },
                    data.yomiTrie.predictiveSearch(prefix, data.succinctBitVectorLBSYomi, limit) {})
            }
        }
    }

    @Test fun candidateFieldsScoresStableTiesAndLimitsMatchOriginalAcrossSettings() {
        for (packed in listOf(false, true)) {
            val data = dictionary(readings, packed)
            for (input in listOf("", "あい", "あいあ", "あいあえ", "あ".repeat(7), "あ".repeat(8), "あ".repeat(16), "あ".repeat(17), "ない")) {
                for (minimum in listOf(-1, 3, 8, 100)) for (lookahead in listOf(0, 1, 3, 6, 100)) {
                    for (aggression in PredictionAggressiveness.entries) for (enabled in listOf(false, true)) {
                        val config = PredictionConfig(minimumInputLength = minimum, lookaheadCharacterCount = lookahead,
                            aggressiveness = aggression, japanesePredictionEnabled = enabled)
                        val actual = buildDictionaryPredictionCandidates(input, data, config, Job())
                        val expected = reference(input, data, config)
                        assertEquals("$packed/$input/$config", expected, actual)
                        for (limit in listOf(0, 1, 4, 16, 100)) {
                            val count = config.copy(systemCandidateLimit = limit).normalizedSystemCandidateLimit
                            assertEquals(expected.sortedBy { it.score }.take(count), actual.sortedBy { it.score }.take(count))
                        }
                    }
                }
            }
        }
    }

    private fun setDictionary(engine: KanaKanjiEngine, user: Boolean, data: TripleDictionaryData) {
        val prefix = if (user) "systemUser" else "system"
        val fields = mapOf("YomiTrie" to data.yomiTrie, "TangoTrie" to data.tangoTrie, "TokenArray" to data.tokenArray,
            "SuccinctBitVectorLBSYomi" to data.succinctBitVectorLBSYomi,
            (if (user) "SuccinctBitVectorIsLeaf" else "SuccinctBitVectorIsLeafYomi") to data.succinctBitVectorIsLeafYomi,
            "SuccinctBitVectorTokenArray" to data.succinctBitVectorTokenArray,
            (if (user) "SuccinctBitVectorLBSTango" else "SuccinctBitVectorTangoLBS") to data.succinctBitVectorTangoLBS)
        fields.forEach { (suffix, value) ->
            KanaKanjiEngine::class.java.getDeclaredField(prefix + suffix).apply { isAccessible = true }.set(engine, value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun enginePrediction(engine: KanaKanjiEngine, input: String, config: PredictionConfig, context: CoroutineContext = Job()): List<Candidate> {
        val continuation = object : Continuation<List<Candidate>> {
            override val context = context
            override fun resumeWith(result: Result<List<Candidate>>) { error("Prediction does not suspend") }
        }
        val method = KanaKanjiEngine::class.java.getDeclaredMethod("buildPredictiveCandidatesIncludingSystemUser",
            String::class.java, PredictionConfig::class.java, Continuation::class.java).apply { isAccessible = true }
        try { return method.invoke(engine, input, config, continuation) as List<Candidate> }
        catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
    }

    @Test fun engineKeepsSystemThenUserTieOrderAndIndependentDictionarySwitches() {
        val system = dictionary(readings, true)
        val user = dictionary(readings, false).also { data ->
            data.tokenArray.leftIds.indices.forEach { data.tokenArray.leftIds[it] = (300 + it).toShort() }
        }
        val engine = KanaKanjiEngine().also { setDictionary(it, false, system); setDictionary(it, true, user) }
        for (systemEnabled in listOf(false, true)) for (userEnabled in listOf(false, true)) {
            for (limit in listOf(1, 4, 16)) for (lookahead in listOf(1, 3, 6)) {
                val config = PredictionConfig(systemDictionaryEnabled = systemEnabled, systemUserDictionaryEnabled = userEnabled,
                    systemCandidateLimit = limit, lookaheadCharacterCount = lookahead)
                val expected = ((if (systemEnabled) reference("あいあ", system, config) else emptyList()) +
                    (if (userEnabled) reference("あいあ", user, config) else emptyList())).sortedBy { it.score }.take(limit)
                assertEquals(expected, enginePrediction(engine, "あいあ", config))
            }
        }
    }

    @Test fun disabledAndOutOfRangeRequestsNeverAccessUninitializedDictionaries() {
        val engine = KanaKanjiEngine()
        for ((input, config) in listOf("あいあ" to PredictionConfig(japanesePredictionEnabled = false),
            "あい" to PredictionConfig(), "あ".repeat(7) to PredictionConfig(minimumInputLength = 8),
            "あ".repeat(17) to PredictionConfig(), "あいあ" to PredictionConfig(systemDictionaryEnabled = false, systemUserDictionaryEnabled = false))) {
            assertEquals(emptyList<Candidate>(), enginePrediction(engine, input, config))
        }
    }

    @Test fun lengthBoundPrunesLongBranchesAndTraversalCancellationPropagates() {
        val words = (0 until 1000).map { "あいあ" + it.toString().padStart(4, '0') }.sorted()
        val data = dictionary(words, true)
        var polls = 0
        assertTrue(data.yomiTrie.predictiveSearch("あいあ", data.succinctBitVectorLBSYomi, 3) { polls++ }.isEmpty())
        assertEquals(3, polls) // entry, first node, completion; no descendants visited
        val job = Job()
        polls = 0
        try {
            data.yomiTrie.predictiveSearch("あいあ", data.succinctBitVectorLBSYomi, 7) {
                if (++polls == 4) job.cancel()
                job.ensureActive()
            }
            fail("Cancellation must not become a partial result")
        } catch (_: CancellationException) { assertEquals(4, polls) }
    }

    @Test fun cancellationInsideLargeTokenListReleasesSingleWorkerForNextRequest() {
        val data = dictionary(listOf("あいあい"), true, tokensPerReading = 4096)
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val delegate = Job()
        var polls = 0
        val monitored = object : Job by delegate {
            @Suppress("UNCHECKED_CAST")
            override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? =
                if (key == Job) this as E else null
            override val isActive: Boolean get() {
                if (++polls == 7) { // A second token checkpoint, after the tiny trie has completed.
                    entered.countDown()
                    check(resume.await(5, TimeUnit.SECONDS))
                }
                return delegate.isActive
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val old = executor.submit<Boolean> {
                try { buildDictionaryPredictionCandidates("あいあ", data, PredictionConfig(), monitored); false }
                catch (_: CancellationException) { true }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            delegate.cancel()
            val next = executor.submit<List<Candidate>> {
                buildDictionaryPredictionCandidates("あいあ", data, PredictionConfig(), Job())
            }
            resume.countDown()
            assertTrue(old.get(5, TimeUnit.SECONDS))
            assertEquals(7, polls)
            assertEquals(reference("あいあ", data, PredictionConfig()), next.get(5, TimeUnit.SECONDS))
        } finally { resume.countDown(); executor.shutdownNow() }
    }

    @Test fun alreadyCancelledRequestThrowsEvenWhenPredictionWouldBeEmpty() {
        val job = Job().apply { cancel() }
        try {
            enginePrediction(KanaKanjiEngine(), "あい", PredictionConfig(japanesePredictionEnabled = false), job)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { /* expected */ }
    }
}
