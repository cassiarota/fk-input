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
class CandidateRules(private val readings: Map<Char, Reading>) {
    private val alternatives = readings.entries.groupBy { it.value.syllable }

    fun homophones(base: String, terms: Collection<String>, limit: Int = 40): List<String> {
        if (base.isBlank()) return emptyList()
        val matched = sensitivePositions(base, terms)
        if (matched.isEmpty()) {
            for (position in base.indices.reversed()) {
                if (!Character.isIdeographic(base[position].code)) continue
                val results = replacePositions(base, listOf(position), terms, limit)
                if (results.isNotEmpty()) return results
            }
            return emptyList()
        }
        return replacePositions(base, matched, terms, limit)
    }

    private fun replacePositions(
        base: String,
        positions: List<Int>,
        terms: Collection<String>,
        limit: Int,
    ): List<String> {
        var states = listOf(base to 0)
        for (position in positions) {
            val original = base[position]
            val source = readings[original] ?: return emptyList()
            val replacements = alternatives[source.syllable].orEmpty()
                .asSequence()
                .filter { it.key != original }
                .sortedWith(
                    compareBy<Map.Entry<Char, Reading>> { it.value.tone != source.tone }
                        .thenByDescending { it.value.frequency }
                        .thenBy { it.key }
                )
                .take(12)
                .toList()
            if (replacements.isEmpty()) return emptyList()
            states = states.flatMap { (text, score) ->
                replacements.map { entry ->
                    val changed = text.toCharArray()
                    changed[position] = entry.key
                    val tonePenalty = if (entry.value.tone == source.tone) 0 else 1_000_000
                    String(changed) to (score + tonePenalty - entry.value.frequency)
                }
            }.distinctBy { it.first }
                .sortedBy { it.second }
                .take(120)
        }
        return states.asSequence()
            .map { it.first }
            .filter { it != base && terms.none(it::contains) }
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
                    readings[fields[0][0]] = Reading(
                        fields[1], fields[2].toIntOrNull() ?: 5, fields[3].toIntOrNull() ?: 0
                    )
                }
            }
            return CandidateRules(readings)
        }
    }
}
