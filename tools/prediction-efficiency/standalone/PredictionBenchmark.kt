import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedCharArray
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import com.kazumaproject.markdownhelperkeyboard.converter.engine.*
import com.sun.management.ThreadMXBean
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.io.*
import java.lang.management.ManagementFactory
import java.util.zip.ZipInputStream
import kotlin.random.Random

@Volatile private var sink: Any? = null

private fun <T> read(file: File, block: (ObjectInputStream) -> T): T {
    val stream = BufferedInputStream(FileInputStream(file))
    val input = if (file.extension == "zip") ZipInputStream(stream).also { checkNotNull(it.nextEntry) } else stream
    return ObjectInputStream(input).use(block)
}

private fun loadSystem(assets: File): TripleDictionaryData {
    val directory = File(assets, "system")
    val rawYomi = read(File(directory, "yomi.dat.zip")) { LOUDSWithTermId().readExternalNotCompress(it) }
    val rawTango = read(File(directory, "tango.dat.zip")) { LOUDS().readExternalNotCompress(it) }
    val rawTokens = TokenArray().apply {
        read(File(directory, "token.dat.zip")) { readExternal(it) }
        read(File(assets, "pos_table.dat")) { readPOSTable(it) }
    }
    val yomi = LOUDSWithTermId.fromPacked(rawYomi.LBS, PackedCharArray.from(rawYomi.getAllLabels()),
        rawYomi.isLeaf, PackedIntArray.from(rawYomi.getAllTermIds()))
    val tango = LOUDS.fromPacked(rawTango.LBS, PackedCharArray.from(rawTango.getAllLabels()), rawTango.isLeaf)
    val tokens = TokenArray.fromPacked(rawTokens.getPosTableIndices(), rawTokens.getWordCosts(),
        PackedIntArray.from(rawTokens.getNodeIds()), rawTokens.bitvector, rawTokens.leftIds, rawTokens.rightIds)
    return TripleDictionaryData(tango, yomi, tokens, SuccinctBitVector(yomi.LBS), SuccinctBitVector(yomi.isLeaf),
        SuccinctBitVector(tokens.bitvector), SuccinctBitVector(tango.LBS))
}

fun main(args: Array<String>) {
    val system = loadSystem(File(args[0]))
    val original = OriginalPrediction(system)
    val context = Job()
    val config = PredictionConfig()
    fun optimized(input: String, settings: PredictionConfig) =
        buildDictionaryPredictionCandidates(input, system, settings, context)
            .sortedBy { it.score }.take(settings.normalizedSystemCandidateLimit).also { context.ensureActive() }
    val phrases = listOf("きょうはいいてんきですね", "あしたのよていをかくにんします", "よろしくおねがいします",
        "ありがとうございます", "おつかれさまです", "こんにちは", "おはようございます", "こんばんは",
        "しょうひんをこうにゅうします", "かいしゃにれんらくしてください", "しんかんせんのじかんをしらべる",
        "にほんごにへんかんします", "きょうとでしゃしんをとりました", "こうつうのじょうほうをかくにん",
        "じゅうしょをへんこうしました", "でんしゃがすこしおくれています", "きょうのかいぎはさんじからです",
        "らいしゅうのしゅうまつにあいます", "すみませんすこしまってください", "ちゅうしゃじょうはどこですか",
        "しゅうりょうしたられんらくします", "きょうりゅうのはくぶつかん", "とうきょうえきでまちあわせ",
        "きょういくについてかんがえる", "しょうらいのけいかくをたてる", "きゅうけいしてからつづけます",
        "かぞくでりょこうにいきます", "あたらしいしごとをはじめます", "それではまたあした", "ばってりーのもちがよくなる")
    val workload = phrases.flatMap { phrase -> (2..minOf(16, phrase.length)).map { phrase.take(it) } }
    val fixed = listOf("こう", "しょ", "きょう", "しょう", "にほん", "こうつう", "ちゅうしゃじょう", "ぜんぜんないよみ")
    val sample = ArrayList<String>()
    val random = Random(24681357)
    val iterator = system.yomiTrie.termIterator(system.succinctBitVectorLBSYomi, system.succinctBitVectorIsLeafYomi)
    var seen = 0
    while (iterator.hasNext()) {
        val reading = iterator.next().first
        if (reading.length !in 2..16) continue
        seen++
        if (sample.size < 160) sample.add(reading)
        else { val index = random.nextInt(seen); if (index < 160) sample[index] = reading }
    }
    val inputs = (fixed + workload + sample.flatMap { listOf(it.take(2), it.take(3), it.take(6), it) } +
        listOf("", "あ", "あ".repeat(16), "あ".repeat(17))).distinct()
    var checked = 0
    for (input in inputs) for (lookahead in listOf(1, 3, 6)) for (enabled in listOf(false, true)) {
        val settings = config.copy(lookaheadCharacterCount = lookahead, japanesePredictionEnabled = enabled)
        check(original.predict(input, settings) == optimized(input, settings)) { "Mismatch $input/$settings" }
        checked++
    }
    println("PARITY\t$checked\tinputs=${inputs.size}\tworkload=${workload.size}\tpacked=true\tsampled_from=$seen")
    val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    bean.isThreadAllocatedMemoryEnabled = true
    val thread = Thread.currentThread().id
    fun measure(label: String, variant: String, repeats: Int, block: () -> Any?) {
        val samples = DoubleArray(repeats)
        val cpu = bean.currentThreadCpuTime
        val bytes = bean.getThreadAllocatedBytes(thread)
        repeat(repeats) { index ->
            val start = System.nanoTime(); sink = block(); samples[index] = (System.nanoTime() - start) / 1000.0
        }
        val cpuUs = (bean.currentThreadCpuTime - cpu) / 1000.0 / repeats
        val allocated = (bean.getThreadAllocatedBytes(thread) - bytes).toDouble() / repeats
        samples.sort()
        println("RESULT\t$label\t$variant\t$repeats\t${samples[repeats / 2]}\t${samples[(repeats * .95).toInt()]}\t$cpuUs\t$allocated")
    }
    repeat(20) { for (input in fixed) { sink = original.predict(input, config); sink = optimized(input, config) } }
    repeat(5) { for (input in workload) { sink = original.predict(input, config); sink = optimized(input, config) } }
    for (input in fixed + listOf("workload", "workload_off")) {
        val settings = config.copy(japanesePredictionEnabled = input != "workload_off")
        for (changed in if (args[1] == "2") listOf(true, false) else listOf(false, true)) {
            measure(input, if (changed) "feature" else "baseline", if (input.startsWith("workload")) 30 else 250) {
                if (input.startsWith("workload")) {
                    var total = 0
                    for (reading in workload) total += if (changed) optimized(reading, settings).size else original.predict(reading, settings).size
                    total
                } else if (changed) optimized(input, settings) else original.predict(input, settings)
            }
        }
    }
}
