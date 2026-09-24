package uk.cassiangroup.fkinput.core

/** Swaps the selected item with its immediate predecessor, preserving all other positions. */
fun moveOneStep(queue: List<String>, selected: String): List<String> {
    val index = queue.indexOf(selected)
    if (index <= 0) return queue
    return queue.toMutableList().apply {
        this[index] = queue[index - 1]
        this[index - 1] = selected
    }
}
