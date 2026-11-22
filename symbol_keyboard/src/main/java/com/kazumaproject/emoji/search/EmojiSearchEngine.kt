package com.kazumaproject.emoji.search

/** Future engines (including hybrid vector search) can return these same ranked results. */
fun interface EmojiSearchEngine {
    fun search(query: String, limit: Int): List<EmojiSearchResult>
}

data class EmojiSearchResult(val identity: String, val symbol: String, val name: String, val score: Int)

/** Immutable, offline and independent of Android, fonts, history and language models. */
class DictionaryEmojiSearchEngine(private val repository: EmojiSearchRepository) : EmojiSearchEngine {
    private val tokenSeparator = Regex("[\\s\\u3000]+")
    override fun search(query: String, limit: Int): List<EmojiSearchResult> {
        if (limit <= 0) return emptyList()
        val bounded = query.take(EmojiSearchNormalizer.MAX_QUERY_LENGTH)
        val q = EmojiSearchNormalizer.text(bounded)
        if (q.isEmpty()) return emptyList()
        val tokens = bounded.split(tokenSeparator)
            .map(EmojiSearchNormalizer::text).filter(String::isNotEmpty).distinct()
        val emojiId = EmojiSearchNormalizer.identity(bounded.trim())
        val ranked = ArrayList<Pair<EmojiSearchRepository.Entry, Int>>()
        repository.entries.forEach { entry ->
            if (Thread.currentThread().isInterrupted) return emptyList()
            val score = if (entry.identity == emojiId) 1600 else if (tokens.size > 1) {
                var total = 0
                var count = 0
                for (token in tokens) {
                    val part = relevance(entry, token)
                    if (part == 0) break
                    total += part
                    count++
                }
                if (count == tokens.size) total / count + 80 else 0
            } else relevance(entry, q)
            if (score > 0) ranked.add(entry to score)
        }
        val floor = (ranked.maxOfOrNull { it.second } ?: 0) * 3 / 5
        return ranked.asSequence().filter { it.second >= floor }.sortedWith(compareByDescending<Pair<EmojiSearchRepository.Entry, Int>> { it.second }
            .thenBy { it.first.order }).take(limit.coerceAtMost(200)).map { (entry, score) ->
            EmojiSearchResult(entry.identity, entry.symbol, entry.name, score)
        }.toList()
    }

    private fun relevance(entry: EmojiSearchRepository.Entry, query: String): Int {
        val names = matchTerms(entry.names, query, 1200)
        val usages = matchTerms(entry.usages, query, 1100)
        val keywords = matchTerms(entry.keywords, query, 1000)
        val related = matchTerms(entry.related, query, 800)
        val categories = matchTerms(entry.categories, query, 600)
        val best = maxOf(maxOf((names ushr 32).toInt(), (usages ushr 32).toInt()),
            maxOf((keywords ushr 32).toInt(), (related ushr 32).toInt()), (categories ushr 32).toInt())
        val evidence = names.toInt() + usages.toInt() + keywords.toInt() + related.toInt() + categories.toInt()
        return if (best == 0) 0 else best + minOf(evidence - 1, 3) * 10
    }

    // Return two primitive counters packed in a Long; avoid per-entry closures/temporary lists.
    private fun matchTerms(terms: List<String>, query: String, exact: Int): Long {
        var best = 0
        var evidence = 0
        for (index in terms.indices) {
            val term = terms[index]
            val score = when {
                term == query -> exact
                term.startsWith(query) -> exact - 300
                query.length >= 2 && term.contains(query) -> exact - 500
                term.length >= 2 && query.contains(term) &&
                    (term.length >= 3 || term.any { it in '\u3400'..'\u9fff' }) ->
                    200 + exact / 5 + (250 * term.length / query.length)
                // A kanji noun (雨, 犬, 本…) is stronger evidence than an incidental kana suffix.
                term.length == 1 && term[0] in '\u3400'..'\u9fff' && query.contains(term) ->
                    220 + exact / 5 + 250 / query.length
                else -> 0
            }
            if (score > 0) { best = maxOf(best, score); evidence++ }
        }
        return (best.toLong() shl 32) or evidence.toLong()
    }
}
