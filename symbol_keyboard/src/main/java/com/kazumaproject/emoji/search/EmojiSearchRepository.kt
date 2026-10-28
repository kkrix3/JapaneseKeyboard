package com.kazumaproject.emoji.search

import java.io.Reader

/** Metadata is loaded only when search first opens, on a worker dispatcher. */
class EmojiSearchRepository private constructor(val entries: List<Entry>) {
    data class Entry(
        val identity: String,
        val symbol: String,
        val name: String,
        val names: List<String>,
        val keywords: List<String>,
        val usages: List<String>,
        val related: List<String>,
        val categories: List<String>,
        val order: Int,
    )

    companion object {
        /** The caller's runtime inventory is authoritative, even when the bundled asset is newer. */
        fun load(reader: Reader, symbols: List<String>, categoryLabels: Map<String, String> = emptyMap()): EmojiSearchRepository {
            val available = symbols.distinctBy(EmojiSearchNormalizer::identity)
            val records = reader.buffered().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith('#') }.associate { line ->
                    val fields = line.split('\t')
                    require(fields.size == 5) { "Invalid emoji search record" }
                    fields[0] to fields.drop(1)
                }
            }
            fun terms(value: String) = value.split('|').map(EmojiSearchNormalizer::text).filter(String::isNotEmpty).distinct()
            return EmojiSearchRepository(available.mapIndexed { order, symbol ->
                val id = EmojiSearchNormalizer.identity(symbol)
                val record = records[id]
                Entry(id, symbol, record?.get(0).orEmpty(), terms(record?.get(0).orEmpty()),
                    terms(record?.get(1).orEmpty()), terms(record?.get(2).orEmpty()), terms(record?.get(3).orEmpty()),
                    terms(categoryLabels[symbol].orEmpty()), order)
            })
        }
    }
}
