// Unchanged prediction methods from upstream df1f38315bf56d860d32919a3eaed6a75499e91e.
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.*

class OriginalPrediction(system: TripleDictionaryData, user: TripleDictionaryData? = null) {
    private val systemYomiTrie = system.yomiTrie
    private val systemTangoTrie = system.tangoTrie
    private val systemTokenArray = system.tokenArray
    private val systemSuccinctBitVectorLBSYomi = system.succinctBitVectorLBSYomi
    private val systemSuccinctBitVectorIsLeafYomi = system.succinctBitVectorIsLeafYomi
    private val systemSuccinctBitVectorTokenArray = system.succinctBitVectorTokenArray
    private val systemSuccinctBitVectorTangoLBS = system.succinctBitVectorTangoLBS
    private val systemUserYomiTrie = user?.yomiTrie
    private val systemUserTangoTrie = user?.tangoTrie
    private val systemUserTokenArray = user?.tokenArray
    private val systemUserSuccinctBitVectorLBSYomi = user?.succinctBitVectorLBSYomi
    private val systemUserSuccinctBitVectorIsLeaf = user?.succinctBitVectorIsLeafYomi
    private val systemUserSuccinctBitVectorTokenArray = user?.succinctBitVectorTokenArray
    private val systemUserSuccinctBitVectorLBSTango = user?.succinctBitVectorTangoLBS
    private fun deferredPrediction(
        input: String,
        yomiTrie: LOUDSWithTermId,
        succinctBitVector: SuccinctBitVector,
        predictionConfig: PredictionConfig,
    ): List<String> {
        return yomiTrie.predictiveSearch(
            prefix = input, succinctBitVector = succinctBitVector
        ).filter { predictionConfig.acceptsJapaneseReading(input.length, it.length) }
    }

    fun predict(
        input: String,
        predictionConfig: PredictionConfig,
    ): List<Candidate> {
        val systemCandidates = if (predictionConfig.systemDictionaryEnabled) {
            val systemPredictiveYomi = deferredPrediction(
                input = input,
                yomiTrie = systemYomiTrie,
                succinctBitVector = systemSuccinctBitVectorLBSYomi,
                predictionConfig = predictionConfig,
            )
            buildPredictiveCandidatesFromDictionary(
                input = input,
                yomiList = systemPredictiveYomi,
                yomiTrie = systemYomiTrie,
                tangoTrie = systemTangoTrie,
                tokenArray = systemTokenArray,
                succinctBitVectorLBSYomi = systemSuccinctBitVectorLBSYomi,
                succinctBitVectorIsLeafYomi = systemSuccinctBitVectorIsLeafYomi,
                succinctBitVectorTokenArray = systemSuccinctBitVectorTokenArray,
                succinctBitVectorTangoLBS = systemSuccinctBitVectorTangoLBS,
                predictionConfig = predictionConfig,
            )
        } else {
            emptyList()
        }

        val systemUserCandidates =
            if (predictionConfig.systemUserDictionaryEnabled && systemUserYomiTrie != null && systemUserTangoTrie != null && systemUserTokenArray != null && systemUserSuccinctBitVectorLBSYomi != null && systemUserSuccinctBitVectorIsLeaf != null && systemUserSuccinctBitVectorTokenArray != null && systemUserSuccinctBitVectorLBSTango != null) {
                val systemUserPredictiveYomi = deferredPrediction(
                    input = input,
                    yomiTrie = systemUserYomiTrie!!,
                    succinctBitVector = systemUserSuccinctBitVectorLBSYomi!!,
                    predictionConfig = predictionConfig,
                )
                buildPredictiveCandidatesFromDictionary(
                    input = input,
                    yomiList = systemUserPredictiveYomi,
                    yomiTrie = systemUserYomiTrie!!,
                    tangoTrie = systemUserTangoTrie!!,
                    tokenArray = systemUserTokenArray!!,
                    succinctBitVectorLBSYomi = systemUserSuccinctBitVectorLBSYomi!!,
                    succinctBitVectorIsLeafYomi = systemUserSuccinctBitVectorIsLeaf!!,
                    succinctBitVectorTokenArray = systemUserSuccinctBitVectorTokenArray!!,
                    succinctBitVectorTangoLBS = systemUserSuccinctBitVectorLBSTango!!,
                    predictionConfig = predictionConfig,
                )
            } else {
                emptyList()
            }

        return (systemCandidates + systemUserCandidates)
            .sortedBy { it.score }
            .take(predictionConfig.normalizedSystemCandidateLimit)
    }

    private fun buildPredictiveCandidatesFromDictionary(
        input: String,
        yomiList: List<String>,
        yomiTrie: LOUDSWithTermId,
        tangoTrie: LOUDS,
        tokenArray: TokenArray,
        succinctBitVectorLBSYomi: SuccinctBitVector,
        succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector,
        succinctBitVectorTangoLBS: SuccinctBitVector,
        predictionConfig: PredictionConfig,
    ): List<Candidate> {
        return buildList {
            yomiList.forEach { yomi ->
                if (yomi.length == input.length) return@forEach
                val nodeIndex =
                    yomiTrie.getNodeIndex(yomi, succinctBitVector = succinctBitVectorLBSYomi)
                if (nodeIndex <= 0) {
                    return@forEach
                }
                val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
                tokenArray.forEachDictionaryByYomiTermId(
                    termId,
                    succinctBitVector = succinctBitVectorTokenArray,
                ) { posTableIndex, wordCost, nodeId ->
                    val baseCost = wordCost.toInt()
                    val score = baseCost + predictionConfig.completionPenalty(
                        inputLength = input.length,
                        readingLength = yomi.length,
                    )
                    add(
                        Candidate(
                            string = when (nodeId) {
                                -2 -> yomi
                                -1 -> yomi.hiraToKata()
                                else -> tangoTrie.getLetter(nodeId, succinctBitVectorTangoLBS)
                            },
                            type = 9,
                            length = yomi.length.toUByte(),
                            score = score,
                            leftId = tokenArray.leftIds[posTableIndex.toInt()],
                            rightId = tokenArray.rightIds[posTableIndex.toInt()],
                        )
                    )
                }
            }
        }
    }

}
