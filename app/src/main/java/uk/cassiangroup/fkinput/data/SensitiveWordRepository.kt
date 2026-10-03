package uk.cassiangroup.fkinput.data

import android.content.Context

/** Keeps the legacy private UTF-8 file and the SQLite index in sync. */
class SensitiveWordRepository(
    private val file: SensitiveWordFile,
    private val store: InputStore,
) {
    constructor(context: Context, store: InputStore) : this(SensitiveWordFile(context), store)

    @Synchronized fun initialize(): Int {
        val source = file.initialize()
        val contents = source.readText(Charsets.UTF_8)
        val imported = store.importSensitiveWords(contents)
        val existing = contents.lineSequence().toHashSet()
        file.merge(store.sensitiveWords().filterNot(existing::contains))
        return imported
    }

    @Synchronized fun add(selection: String): Boolean {
        val word = selectedSensitiveWord(selection)
        initialize()
        val addedToFile = file.add(word)
        val addedToDatabase = store.addSensitiveWord(word)
        return addedToFile || addedToDatabase
    }
}
