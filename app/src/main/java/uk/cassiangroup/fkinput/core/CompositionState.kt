package uk.cassiangroup.fkinput.core

data class SelectedPart(val keys: String, val text: String)

/** Editable Pinyin input. Selected segments stay local until the whole phrase is committed. */
class CompositionState {
    private val parts = ArrayList<SelectedPart>()
    val selectedParts: List<SelectedPart> get() = parts.toList()
    val selectedText: String get() = parts.joinToString("") { it.text }
    var raw: String = ""
        private set
    var cursor: Int = 0
        private set
    var segmentEnd: Int = 0
        private set
    val hasContent: Boolean get() = parts.isNotEmpty() || raw.isNotEmpty()
    val candidateInput: String get() = raw.take(segmentEnd)
    val pendingSuffix: String get() = raw.drop(segmentEnd)

    fun insert(value: String) {
        raw = raw.substring(0, cursor) + value + raw.substring(cursor)
        cursor += value.length
        segmentEnd = raw.length
    }

    fun moveCursor(delta: Int) {
        cursor = (cursor + delta).coerceIn(0, raw.length)
    }

    fun backspace(): Boolean {
        if (cursor > 0) {
            raw = raw.removeRange(cursor - 1, cursor)
            cursor--
            segmentEnd = raw.length
            return true
        }
        if (parts.isNotEmpty()) {
            reopen(parts.lastIndex)
            return true
        }
        return raw.isNotEmpty()
    }

    fun setSegmentEnd(end: Int) {
        require(end in 1..raw.length)
        segmentEnd = end
    }

    /** Returns the finished text, or null if a suffix still needs a candidate. */
    fun choose(text: String): String? {
        require(raw.isNotEmpty() && segmentEnd in 1..raw.length)
        val keys = candidateInput
        val suffix = pendingSuffix
        if (suffix.isEmpty()) {
            val result = selectedText + text
            clear()
            return result
        }
        parts.add(SelectedPart(keys, text))
        raw = suffix
        cursor = raw.length
        segmentEnd = raw.length
        return null
    }

    fun reopen(index: Int) {
        require(index in parts.indices)
        val restored = parts.drop(index).joinToString("") { it.keys }
        raw = restored + raw
        parts.subList(index, parts.size).clear()
        cursor = restored.length
        segmentEnd = raw.length
    }

    fun takeSelectedOnly(): String {
        require(raw.isEmpty())
        return selectedText.also { clear() }
    }

    fun clear() {
        parts.clear()
        raw = ""
        cursor = 0
        segmentEnd = 0
    }
}
