package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.domain.categorizeEmoji
import com.kazumaproject.domain.sortByEmojiCategory
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.emoji.search.DictionaryEmojiSearchEngine
import com.kazumaproject.emoji.search.EmojiSearchNormalizer
import com.kazumaproject.emoji.search.EmojiSearchRepository
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.ObjectInputStream

class BundledEmojiSearchTest {
    private fun root(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "tools/emoji_search").isDirectory }

    @Test fun searchUsesTheRealDictionaryAndEvaluatesAllFixedSuites() {
        val root = root()
        val assets = File(root, "app/src/main/assets/emoji")
        val tango = ObjectInputStream(BufferedInputStream(File(assets, "tango_emoji.dat").inputStream()))
            .use { LOUDS().readExternalNotCompress(it) }
        val tokens = TokenArray().also { array ->
            ObjectInputStream(BufferedInputStream(File(assets, "token_emoji.dat").inputStream())).use(array::readExternal)
        }
        val bits = SuccinctBitVector(tango.LBS)
        val inventory = tokens.getNodeIds().map { tango.getLetterShortArray(it, bits) }.distinct()
            .map { Emoji(it, categorizeEmoji(it)) }.sortByEmojiCategory().map { it.symbol }
        val exported = File(root, "tools/emoji_search/bundled-emoji.tsv").readLines().map { it.substringBefore('\t') }.toSet()
        assertEquals(exported, inventory.toSet())
        val repository = EmojiSearchRepository.load(File(root, "symbol_keyboard/src/main/assets/emoji_search/ja.tsv").reader(), inventory)
        assertEquals(1902, repository.entries.size)
        assertTrue(repository.entries.all { it.name.isNotBlank() })
        assertEquals("祈り", repository.entries.single { it.identity == "1F64F" }.name)
        assertEquals("力こぶ", repository.entries.single { it.identity == "1F4AA" }.name)
        assertTrue(repository.entries.none { "肌色" in it.name })
        val engine = DictionaryEmojiSearchEngine(repository)
        val output = File(root, "app/build/reports/emoji-search").apply { mkdirs() }
        for (suite in listOf("regression", "development", "unseen")) {
            val cases = File(root, "tools/emoji_search/$suite.tsv").readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            var hit1 = 0; var hit5 = 0; var rr = 0.0
            val rows = mutableListOf("query,gold,rank_at_10,top5")
            for (case in cases) {
                val (query, goldText) = case.split('\t')
                val gold = goldText.split(' ').map(EmojiSearchNormalizer::identity).toSet()
                val matches = engine.search(query, 10)
                val rank = matches.indexOfFirst { it.identity in gold }.let { if (it < 0) 0 else it + 1 }
                assertTrue(matches.all { it.symbol in exported })
                assertEquals(matches.size, matches.map { it.identity }.distinct().size)
                if (rank == 1) hit1++
                if (rank in 1..5) hit5++
                if (rank > 0) rr += 1.0 / rank
                rows.add("\"$query\",\"$goldText\",$rank,\"${matches.take(5).joinToString(" ") { it.symbol }}\"")
            }
            File(output, "$suite.csv").writeText(rows.joinToString("\n") + "\n")
            println("$suite count=${cases.size} Hit@1=$hit1 Hit@5=$hit5 MRR@10=${rr / cases.size}")
            if (suite == "regression") assertEquals(28, hit5)
        }
        // Kana-only local input must reach the same concepts as the kanji examples.
        for ((kana, expected) in listOf("かんしゃ" to "🙏", "きまずい" to "😅", "はなび" to "🎆", "でんしゃ" to "🚃")) {
            assertTrue("$kana", engine.search(kana, 10).any { it.identity == EmojiSearchNormalizer.identity(expected) })
        }
    }
}
