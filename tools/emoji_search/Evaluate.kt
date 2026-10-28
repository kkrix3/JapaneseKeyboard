import com.kazumaproject.emoji.search.*
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.measureNanoTime

/** Runs production Kotlin, not a reimplementation of scoring. */
fun main(args: Array<String>) {
    val root = File(args[0])
    val out = File(args[1]).apply { mkdirs() }
    val tool = File(root, "tools/emoji_search")
    val asset = File(root, "symbol_keyboard/src/main/assets/emoji_search/ja.tsv")
    // Production orders the actual inventory by Unicode category/order.
    val metadata = File(root, "symbol_keyboard/src/main/java/com/kazumaproject/domain/EmojiCategoryMetadata.kt").readText()
    val unicodeOrder = Regex("^[SPNFTAOYL]:([0-9A-F ]+)$", RegexOption.MULTILINE).findAll(metadata).map { row ->
        row.groupValues[1].split(' ').filter(String::isNotBlank).joinToString("") { String(Character.toChars(it.toInt(16))) }
    }.toList().map(EmojiSearchNormalizer::identity).withIndex().associate { it.value to it.index }
    val inventory = File(tool, "bundled-emoji.tsv").readLines().map { it.substringBefore('\t') }
        .sortedBy { unicodeOrder[EmojiSearchNormalizer.identity(it)] ?: Int.MAX_VALUE }
    fun load() = EmojiSearchRepository.load(asset.reader(), inventory)
    // Warm classes before retained-heap comparison. Heap deltas are not Android PSS.
    load()
    fun used(): Long { repeat(3) { System.gc(); Thread.sleep(20) }; return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() }
    val before = used()
    lateinit var repo: EmojiSearchRepository
    val loadNs = measureNanoTime { repo = load() }
    val after = used()
    val engine = DictionaryEmojiSearchEngine(repo)
    val allQueries = mutableListOf<String>()
    for (suite in listOf("regression", "holdout", "unseen")) {
        val path = File(tool, "$suite.tsv")
        if (!path.exists()) continue
        val cases = path.readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') }
        var hit1 = 0; var hit5 = 0; var reciprocal = 0.0
        val rows = mutableListOf("query,representative_gold,rank_at_10,top5")
        for ((query, goldText) in cases) {
            allQueries.add(query)
            val gold = goldText.split(' ').map(EmojiSearchNormalizer::identity).toSet()
            val results = engine.search(query, 10)
            val rank = results.indexOfFirst { it.identity in gold }.let { if (it < 0) 0 else it + 1 }
            if (rank == 1) hit1++
            if (rank in 1..5) hit5++
            if (rank > 0) reciprocal += 1.0 / rank
            rows.add("\"$query\",\"$goldText\",$rank,\"${results.take(5).joinToString(" ") { it.symbol }}\"")
        }
        File(out, "$suite-results.csv").writeText(rows.joinToString("\n") + "\n")
        println("$suite n=${cases.size} Hit@1=$hit1 Hit@5=$hit5 MRR@10=${reciprocal / cases.size}")
    }
    repeat(8) { allQueries.forEach { engine.search(it, 100) } }
    val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    val thread = Thread.currentThread().id
    val allocatedBefore = bean?.getThreadAllocatedBytes(thread) ?: 0L
    val timings = (0 until 20).flatMap { allQueries.map { query -> measureNanoTime { engine.search(query, 100) } / 1e6 } }.sorted()
    val allocatedAfter = bean?.getThreadAllocatedBytes(thread) ?: 0L
    val stats = "inventory=${repo.entries.size}\nasset_bytes=${asset.length()}\nload_ms=${loadNs / 1e6}\nretained_heap_delta_bytes=${after - before}\nqueries=${timings.size}\np50_ms=${timings[timings.size / 2]}\np95_ms=${timings[(timings.size * .95).toInt()]}\np99_ms=${timings[(timings.size * .99).toInt()]}\nallocated_bytes_per_query=${(allocatedAfter - allocatedBefore) / timings.size}\n"
    File(out, "host-performance.txt").writeText(stats)
    print(stats)
}
