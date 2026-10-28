package com.kazumaproject.emoji.search

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class EmojiSearchEngineTest {
    private fun repository(rows: String, symbols: List<String>) = EmojiSearchRepository.load(StringReader(rows), symbols)

    @Test fun widthKanaCaseWhitespaceAndSelectorsNormalizeOnlyForMatching() {
        assertEquals("ありがとうabc123", EmojiSearchNormalizer.text(" アリガトウ ＡＢＣ１２３！"))
        assertEquals(EmojiSearchNormalizer.text("パーティー"), EmojiSearchNormalizer.text("ﾊﾟｰﾃｨｰ"))
        assertEquals(EmojiSearchNormalizer.identity("🙇‍♂"), EmojiSearchNormalizer.identity("🙇🏽‍♂️"))
        assertNotEquals(EmojiSearchNormalizer.identity("🙇"), EmojiSearchNormalizer.identity("🙇‍♂"))
        assertNotEquals(EmojiSearchNormalizer.identity("👩‍💻"), EmojiSearchNormalizer.identity("👨‍💻"))
    }

    @Test fun runtimeInventoryIsAuthoritativeAndCommitsOriginalSequence() {
        val repo = repository("2764\tハート\t愛\t感謝\t\n1FAE0\t溶ける\t\t気まずい\t", listOf("❤️", "❤", "🙏"))
        assertEquals(listOf("❤️", "🙏"), repo.entries.map { it.symbol })
        val results = DictionaryEmojiSearchEngine(repo).search("感謝", 10)
        assertEquals(listOf("❤️"), results.map { it.symbol })
        assertTrue(DictionaryEmojiSearchEngine(repo).search("気まずい", 10).isEmpty())
        assertEquals("❤️", DictionaryEmojiSearchEngine(repo).search("❤︎", 10).single().symbol)
    }

    @Test fun exactNamesOutrankUsagesPrefixesSubstringsAndRelatedTerms() {
        val repo = repository("""
1F600\t感謝\t\t\t
1F601\t笑顔\t\t感謝\t
1F602\t涙\t\t感謝する\t
1F603\t笑い\t\t深い感謝\t
1F604\t喜び\t\t\t感謝
""".trimIndent(), listOf("😀", "😁", "😂", "😃", "😄"))
        val results = DictionaryEmojiSearchEngine(repo).search("感謝", 10)
        assertEquals("😀", results.first().symbol)
        assertTrue(results.indexOfFirst { it.symbol == "😁" } < results.indexOfFirst { it.symbol == "😂" })
        assertTrue(results.indexOfFirst { it.symbol == "😄" } >= 0)
        assertEquals(results.size, results.map { it.identity }.distinct().size)
    }

    @Test fun multipleWordsRequireEvidenceForEveryWordAndTiesFollowInventory() {
        val repo = repository("1F600\t顔\t\t感謝|応援\t\n1F601\t顔\t\t感謝\t\n1F602\t顔\t\t感謝|応援\t", listOf("😂", "😁", "😀"))
        assertEquals(listOf("😂", "😀"), DictionaryEmojiSearchEngine(repo).search("感謝　応援", 10).map { it.symbol })
    }

    @Test fun blankUnknownLongAndShortIncidentalKanaDoNotInventResults() {
        val engine = DictionaryEmojiSearchEngine(repository("1F3BF\tスキー\tいた\t\t", listOf("🎿")))
        for (query in listOf("", "　", "quantumfrobnicator", "桜が咲いた")) assertTrue(engine.search(query, 10).isEmpty())
        assertTrue(engine.search("いた".repeat(1000), 0).isEmpty())
        assertTrue(engine.search("ita", -5).isEmpty())
    }

    @Test fun localBufferDeletesWholeCodePointsAndCyclesDakutenAndSmallKana() {
        val state = EmojiSearchInputState()
        state.append("か"); state.modifyLastKana(); assertEquals("が", state.query)
        state.append("つ"); state.modifyLastKana(); state.modifyLastKana(); assertEquals("がっ", state.query)
        state.append("🙇"); state.delete(); assertEquals("がっ", state.query)
        state.clear(); state.append("あ".repeat(1000)); assertEquals(128, state.query.length)
        state.clear(); state.delete(); state.modifyLastKana(); assertEquals("", state.query)
    }
}
