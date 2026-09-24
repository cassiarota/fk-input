package uk.cassiangroup.fkinput.core

import android.content.Context
import kotlin.math.ln

enum class KeyboardLayout { NINE_KEY, FULL_KEY }

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

/**
 * Loads the Apache-licensed simplified Pinyin dictionary. It also provides an on-device
 * conversion path while the optional native Rime backend is unavailable.
 */
class PinyinEngine(context: Context) {
    private val roman = HashMap<String, MutableList<WordCandidate>>()
    private val nine = HashMap<String, MutableList<WordCandidate>>()
    private val byText = HashMap<String, MutableList<WordCandidate>>()
    private val native = runCatching { NativeRime(context) }.getOrNull()

    init {
        context.assets.open("data/pinyin_simp.dict.yaml").bufferedReader().use { reader ->
            var inEntries = false
            reader.forEachLine { line ->
                if (!inEntries) {
                    if (line == "...") inEntries = true
                    return@forEachLine
                }
                val fields = line.split('\t')
                if (fields.size < 3) return@forEachLine
                val spelling = fields[1].trim().lowercase()
                val compact = spelling.replace(" ", "").replace("'", "")
                val weight = fields[2].toIntOrNull() ?: 0
                if (compact.isEmpty() || !compact.all { it in 'a'..'z' }) return@forEachLine
                val candidate = WordCandidate(fields[0], spelling, weight)
                roman.getOrPut(compact) { ArrayList() }.add(candidate)
                nine.getOrPut(toNineKey(compact)) { ArrayList() }.add(candidate)
                byText.getOrPut(candidate.text) { ArrayList() }.add(candidate)
            }
        }
        roman.values.forEach { it.sortByDescending(WordCandidate::weight) }
        nine.values.forEach { it.sortByDescending(WordCandidate::weight) }
    }

    fun groups(rawInput: String, layout: KeyboardLayout): List<PinyinGroup> {
        val input = rawInput.lowercase().filter {
            if (layout == KeyboardLayout.NINE_KEY) it in '2'..'9' else it in 'a'..'z'
        }
        if (input.isEmpty()) return emptyList()
        val index = if (layout == KeyboardLayout.NINE_KEY) nine else roman
        val exact = index[input].orEmpty()
        val combined = ArrayList<WordCandidate>()
        native?.candidates(input, layout)?.forEachIndexed { position, text ->
            val match = byText[text]?.firstOrNull { entry ->
                val compact = entry.pinyin.replace(" ", "").replace("'", "")
                val keys = if (layout == KeyboardLayout.NINE_KEY) toNineKey(compact) else compact
                keys.startsWith(input)
            }
            if (match != null) {
                combined.add(match.copy(weight = Int.MAX_VALUE - position))
            }
        }
        combined.addAll(exact)
        if (input.length <= 24) combined.addAll(compose(input, index))
        if (combined.size < 60) {
            index.asSequence()
                .filter { (key, _) -> key.startsWith(input) && key != input }
                .flatMap { (_, values) -> values.asSequence().take(2) }
                .sortedByDescending(WordCandidate::weight)
                .take(80)
                .forEach(combined::add)
        }
        return combined.groupBy(WordCandidate::pinyin)
            .map { (pinyin, words) ->
                val unique = words.distinctBy(WordCandidate::text)
                    .sortedByDescending(WordCandidate::weight)
                    .take(40)
                PinyinGroup(pinyin, unique, unique.firstOrNull()?.weight?.toDouble() ?: 0.0)
            }
            .sortedByDescending(PinyinGroup::score)
            .take(12)
    }

    fun close() = native?.close()

    private fun compose(
        input: String,
        index: Map<String, List<WordCandidate>>,
    ): List<WordCandidate> {
        val memo = HashMap<Int, List<WordCandidate>>()
        fun at(position: Int): List<WordCandidate> {
            if (position == input.length) return listOf(WordCandidate("", "", 1))
            memo[position]?.let { return it }
            val results = ArrayList<WordCandidate>()
            for (end in position + 1..minOf(input.length, position + 12)) {
                val heads = index[input.substring(position, end)].orEmpty().take(6)
                if (heads.isEmpty()) continue
                val tails = at(end).take(6)
                for (head in heads) for (tail in tails) {
                    val spelling = if (tail.pinyin.isEmpty()) head.pinyin
                    else "${head.pinyin} ${tail.pinyin}"
                    val score = (ln(head.weight + 1.0) + ln(tail.weight + 1.0)) * 1000
                    results.add(
                        WordCandidate(head.text + tail.text, spelling, score.toInt())
                    )
                }
            }
            return results.distinctBy { it.text to it.pinyin }
                .sortedByDescending(WordCandidate::weight)
                .take(24)
                .also { memo[position] = it }
        }
        return at(0).filter { it.text.isNotEmpty() }
    }

    companion object {
        private val digitMap = mapOf(
            'a' to '2', 'b' to '2', 'c' to '2',
            'd' to '3', 'e' to '3', 'f' to '3',
            'g' to '4', 'h' to '4', 'i' to '4',
            'j' to '5', 'k' to '5', 'l' to '5',
            'm' to '6', 'n' to '6', 'o' to '6',
            'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
            't' to '8', 'u' to '8', 'v' to '8',
            'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
        )

        fun toNineKey(pinyin: String): String =
            pinyin.lowercase().filter { it in 'a'..'z' }.mapNotNull(digitMap::get).joinToString("")
    }
}
