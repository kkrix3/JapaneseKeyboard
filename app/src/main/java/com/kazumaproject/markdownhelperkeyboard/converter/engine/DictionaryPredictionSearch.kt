package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

/** Collect all eligible candidates before the caller's unchanged stable ranking/limit. */
internal fun buildDictionaryPredictionCandidates(
    input: String,
    data: TripleDictionaryData,
    config: PredictionConfig,
    context: CoroutineContext,
): List<Candidate> {
    context.ensureActive()
    if (!config.canPredictJapanese(input.length)) return emptyList()
    val readings = data.yomiTrie.predictiveSearch(
        prefix = input,
        succinctBitVector = data.succinctBitVectorLBSYomi,
        maxReadingLength = input.length + config.normalizedLookaheadCharacterCount,
        checkCancellation = { context.ensureActive() },
    )
    var readingCount = 0
    var candidateCount = 0
    return buildList {
        readings.forEach { reading ->
            if (readingCount++ and 63 == 0) context.ensureActive()
            if (!config.acceptsJapaneseReading(input.length, reading.length)) return@forEach
            val node = data.yomiTrie.getNodeIndex(reading, data.succinctBitVectorLBSYomi)
            if (node <= 0) return@forEach
            val term = data.yomiTrie.getTermId(node, data.succinctBitVectorIsLeafYomi)
            data.tokenArray.forEachDictionaryByYomiTermId(term, data.succinctBitVectorTokenArray) { pos, cost, surface ->
                if (candidateCount++ and 63 == 0) context.ensureActive()
                add(Candidate(
                    string = data.surfaceFor(surface, reading),
                    type = 9,
                    length = reading.length.toUByte(),
                    score = cost.toInt() + config.completionPenalty(input.length, reading.length),
                    leftId = data.tokenArray.leftIds[pos.toInt()],
                    rightId = data.tokenArray.rightIds[pos.toInt()],
                ))
            }
        }
        context.ensureActive()
    }
}
