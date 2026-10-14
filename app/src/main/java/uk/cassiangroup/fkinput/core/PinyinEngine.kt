package uk.cassiangroup.fkinput.core

import android.content.Context
import kotlin.math.ln

data class WordCandidate(
    val text: String,
    val pinyin: String,
    val weight: Int,
)

data class PinyinGroup(
    val pinyin: String,
    val candidates: List<WordCandidate>,
    val score: Double,
)

/** Local word frequencies with Rime sentence conversion and an offline fallback. */
class PinyinEngine(context: Context) {
    private val roman = HashMap<String, MutableList<WordCandidate>>()
    private val byText = HashMap<String, MutableList<WordCandidate>>()
    private val native = runCatching { NativeRime(context) }.getOrNull()

    init {
        for (dictionary in listOf("pinyin_simp", "conversation")) {
            context.assets.open("data/$dictionary.dict.yaml").bufferedReader().use { reader ->
                var inEntries = false
                reader.forEachLine { line ->
                    if (!inEntries) {
                        if (line == "...") inEntries = true
                        return@forEachLine
                    }
                    val fields = line.split('\t')
                    if (fields.size < 3) return@forEachLine
                    val spelling = fields[1].trim().lowercase()
                    val compact = compact(spelling)
                    val weight = fields[2].toIntOrNull() ?: 0
                    if (compact.isEmpty() || !compact.all { it in 'a'..'z' }) return@forEachLine
                    val candidate = WordCandidate(fields[0], spelling, weight)
                    roman.getOrPut(compact) { ArrayList() }.add(candidate)
                    byText.getOrPut(candidate.text) { ArrayList() }.add(candidate)
                }
            }
        }
        roman.values.forEach { it.sortByDescending(WordCandidate::weight) }
    }

    fun groups(rawInput: String): List<PinyinGroup> {
        val input = rawInput.lowercase().filter { it in 'a'..'z' }
        if (input.isEmpty()) return emptyList()
        val exact = roman[input].orEmpty()
        val combined = ArrayList<WordCandidate>()
        // Complete dictionary words always precede generated sentences and completions.
        combined.addAll(exact.map { it.copy(weight = 1_500_000_000 + it.weight) })
        native?.candidates(input)?.forEachIndexed { position, text ->
            val match = byText[text]?.firstOrNull { compact(it.pinyin).startsWith(input) }
            if (match != null) {
                if (compact(match.pinyin) != input) {
                    combined.add(match.copy(weight = 1_000_000_000 - position))
                }
            } else if (exact.isEmpty()) {
                // Rime sentences need not exist as whole entries in the dictionary.
                spellingFor(text, input)?.let { spelling ->
                    combined.add(WordCandidate(text, spelling, 500_000_000 - position))
                }
            }
        }
        if (combined.isEmpty() && input.length <= 48) combined.addAll(compose(input))
        if (combined.size < 60) {
            roman.asSequence()
                .filter { (key, _) -> key.startsWith(input) && key != input }
                .flatMap { (_, values) -> values.asSequence().take(2) }
                .sortedByDescending(WordCandidate::weight)
                .take(80)
                .forEach { combined.add(it.copy(weight = 750_000_000 + it.weight)) }
        }
        return combined.groupBy(WordCandidate::pinyin)
            .map { (pinyin, words) ->
                val unique = words.sortedByDescending(WordCandidate::weight)
                    .distinctBy(WordCandidate::text)
                    .take(40)
                PinyinGroup(pinyin, unique, unique.firstOrNull()?.weight?.toDouble() ?: 0.0)
            }
            .sortedByDescending(PinyinGroup::score)
            .take(12)
    }

    /** Dictionary-backed boundaries for selecting a leading word in a longer composition. */
    fun segmentLengths(rawInput: String): List<Int> =
        (1 until rawInput.length).filter { roman.containsKey(rawInput.substring(0, it)) } + rawInput.length

    fun exactGroups(rawInput: String): List<PinyinGroup> =
        groups(rawInput).filter { compact(it.pinyin) == rawInput }

    fun close() = native?.close()

    private fun spellingFor(text: String, input: String): String? {
        val failed = HashSet<Pair<Int, Int>>()
        fun at(character: Int, key: Int): String? {
            if (character == text.length) return if (key == input.length) "" else null
            if (!failed.add(character to key)) return null
            for (end in text.length downTo character + 1) {
                for (entry in byText[text.substring(character, end)].orEmpty()) {
                    val spelling = compact(entry.pinyin)
                    if (end == text.length && key < input.length && spelling.startsWith(input.substring(key))) {
                        return entry.pinyin
                    }
                    if (!input.startsWith(spelling, key)) continue
                    val tail = at(end, key + spelling.length) ?: continue
                    return if (tail.isEmpty()) entry.pinyin else "${entry.pinyin} $tail"
                }
            }
            return null
        }
        return at(0, 0)
    }

    private fun compose(input: String): List<WordCandidate> {
        data class Phrase(val text: String, val pinyin: String, val score: Double)
        val memo = HashMap<Int, List<Phrase>>()
        val totalWeight = roman.values.sumOf { words -> words.sumOf { it.weight.toDouble() + 1 } }
        fun at(position: Int): List<Phrase> {
            if (position == input.length) return listOf(Phrase("", "", 0.0))
            memo[position]?.let { return it }
            val results = ArrayList<Phrase>()
            for (end in position + 1..input.length) {
                val heads = roman[input.substring(position, end)].orEmpty().take(6)
                if (heads.isEmpty()) continue
                val tails = at(end).take(6)
                for (head in heads) for (tail in tails) {
                    val spelling = if (tail.pinyin.isEmpty()) head.pinyin else "${head.pinyin} ${tail.pinyin}"
                    results.add(Phrase(head.text + tail.text, spelling,
                        ln((head.weight + 1.0) / totalWeight) + tail.score))
                }
            }
            return results.sortedByDescending(Phrase::score)
                .distinctBy { it.text to it.pinyin }
                .take(24)
                .also { memo[position] = it }
        }
        return at(0).map { WordCandidate(it.text, it.pinyin, (1_000_000 + it.score * 1000).toInt()) }
    }

    private fun compact(pinyin: String) = pinyin.replace(" ", "").replace("'", "")
}
