package uk.cassiangroup.fkinput.core

import android.content.Context

data class Reading(val syllable: String, val tone: Int, val frequency: Int)

/** Longest non-overlapping whole-term matches, scanned from left to right. */
fun sensitivePositions(text: String, terms: Collection<String>): List<Int> {
    val usable = terms.filter(String::isNotBlank).sortedByDescending(String::length)
    val positions = ArrayList<Int>()
    var index = 0
    while (index < text.length) {
        val match = usable.firstOrNull { text.startsWith(it, index) }
        if (match == null) {
            index++
        } else {
            for (offset in match.indices) {
                if (Character.isIdeographic(text[index + offset].code)) positions.add(index + offset)
            }
            index += match.length
        }
    }
    return positions
}

/** Kept separate from Android UI so candidate behavior can be tested deterministically. */
class CandidateRules(
    private val readings: Map<Char, Reading>,
    private val phraseReadings: Map<String, List<Reading>> = emptyMap(),
) {
    private val alternatives = readings.entries.groupBy { it.value.syllable to it.value.tone }
    private val longestPhrase = phraseReadings.keys.maxOfOrNull(String::length) ?: 0

    fun homophones(base: String, terms: Collection<String>, limit: Int = 40, pinyin: String = ""): List<String> {
        if (base.isBlank()) return emptyList()
        val matched = sensitivePositions(base, terms)
        if (matched.isEmpty()) {
            for (position in base.indices.reversed()) {
                if (!Character.isIdeographic(base[position].code)) continue
                val results = replacePositions(base, listOf(position), terms, limit, pinyin)
                if (results.isNotEmpty()) return results
            }
            return emptyList()
        }
        return replacePositions(base, matched, terms, limit, pinyin)
    }

    /** Phrase readings disambiguate polyphones and neutral tones before single-character fallback. */
    fun pronunciation(text: String, pinyin: String = ""): List<Reading?> {
        val syllables = pinyin.split(' ', '\'').filter(String::isNotEmpty)
        if (syllables.isNotEmpty() && syllables.size != text.length) return List(text.length) { null }
        val result = ArrayList<Reading?>()
        var position = 0
        while (position < text.length) {
            val phrase = (minOf(longestPhrase, text.length - position) downTo 2)
                .firstNotNullOfOrNull { length ->
                    phraseReadings[text.substring(position, position + length)]?.takeIf { values ->
                        syllables.isEmpty() || values.map(Reading::syllable) ==
                            syllables.subList(position, position + length)
                    }
                }
            if (phrase != null) {
                result.addAll(phrase)
                position += phrase.size
            } else {
                result.add(readings[text[position]]?.takeIf {
                    syllables.isEmpty() || it.syllable == syllables[position]
                })
                position++
            }
        }
        return result
    }

    fun samePronunciation(base: String, changed: String, pinyin: String = ""): Boolean {
        if (base.length != changed.length) return false
        val source = pronunciation(base, pinyin)
        val target = pronunciation(changed, pinyin)
        return base.indices.all { position ->
            if (!Character.isIdeographic(base[position].code)) base[position] == changed[position]
            else if (source[position] == null && target[position] == null) base[position] == changed[position]
            else source[position] != null && target[position] != null &&
                source[position]?.syllable == target[position]?.syllable &&
                source[position]?.tone == target[position]?.tone
        }
    }

    private fun replacePositions(
        base: String,
        positions: List<Int>,
        terms: Collection<String>,
        limit: Int,
        pinyin: String,
    ): List<String> {
        var states = listOf(base to 0)
        val pronounced = pronunciation(base, pinyin)
        for (position in positions) {
            val original = base[position]
            val source = pronounced[position] ?: return emptyList()
            val replacements = alternatives[source.syllable to source.tone].orEmpty()
                .asSequence()
                .filter { it.key != original && it.value.frequency > 0 }
                .sortedWith(
                    compareByDescending<Map.Entry<Char, Reading>> { it.value.frequency }
                        .thenBy { it.key }
                )
                .take(12)
                .toList()
            if (replacements.isEmpty()) return emptyList()
            states = states.flatMap { (text, score) ->
                replacements.map { entry ->
                    val changed = text.toCharArray()
                    changed[position] = entry.key
                    String(changed) to (score - entry.value.frequency)
                }
            }.distinctBy { it.first }
                .sortedBy { it.second }
                .take(120)
        }
        return states.asSequence()
            .map { it.first }
            .filter { it != base && terms.none(it::contains) }
            .filter { samePronunciation(base, it, pinyin) }
            .distinct()
            .take(limit)
            .toList()
    }

    companion object {
        fun fromAssets(context: Context): CandidateRules {
            val readings = HashMap<Char, Reading>()
            context.assets.open("data/readings.tsv").bufferedReader().useLines { lines ->
                lines.filterNot { it.startsWith("#") }.forEach { line ->
                    val fields = line.split('\t')
                    if (fields.size != 4 || fields[0].length != 1) return@forEach
                    val tone = fields[2].toIntOrNull() ?: return@forEach
                    if (tone !in 1..5) return@forEach
                    readings[fields[0][0]] = Reading(fields[1], tone, fields[3].toIntOrNull() ?: 0)
                }
            }
            val phrases = HashMap<String, List<Reading>>()
            context.assets.open("data/phrase_readings.tsv").bufferedReader().useLines { lines ->
                lines.filterNot { it.startsWith("#") }.forEach { line ->
                    val fields = line.split('\t')
                    if (fields.size != 2) return@forEach
                    val values = fields[1].split(' ')
                    if (values.size != fields[0].length || values.any {
                        it.length < 2 || it.last() !in '1'..'5'
                    }) return@forEach
                    phrases[fields[0]] = values.map { Reading(it.dropLast(1), it.last().digitToInt(), 0) }
                }
            }
            return CandidateRules(readings, phrases)
        }
    }
}
