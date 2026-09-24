package uk.cassiangroup.fkinput.voice

import java.util.TreeMap

/** Partial results replace one segment; final results seal it. */
class TranscriptAssembler {
    private val finals = TreeMap<Int, String>()
    private val partials = HashMap<Int, String>()

    fun partial(segmentId: Int, text: String) {
        if (segmentId !in finals) partials[segmentId] = text
    }

    fun final(segmentId: Int, text: String) {
        finals[segmentId] = text
        partials.remove(segmentId)
    }

    fun preview(): String {
        val segments = TreeMap<Int, String>()
        segments.putAll(finals)
        segments.putAll(partials)
        return joinSegments(segments.values.toList())
    }

    fun completed(): String = joinSegments(finals.values.toList())

    companion object {
        fun joinSegments(segments: List<String>): String {
            val result = StringBuilder()
            for (part in segments.map(String::trim).filter(String::isNotEmpty)) {
                if (result.isNotEmpty() && result.last().isLetter() &&
                    result.last().code < 128 && part.first().isLetter() &&
                    part.first().code < 128
                ) result.append(' ')
                result.append(part)
            }
            return result.toString()
        }
    }
}
