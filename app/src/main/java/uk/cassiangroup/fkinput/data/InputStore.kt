package uk.cassiangroup.fkinput.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import uk.cassiangroup.fkinput.core.WordCandidate
import uk.cassiangroup.fkinput.core.moveOneStep

/** Stores only user-maintained terms and candidate order; typed text is never recorded. */
class InputStore(context: Context) : SQLiteOpenHelper(context, "fk-input.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE terms (term TEXT PRIMARY KEY NOT NULL)")
        db.execSQL(
            "CREATE TABLE ranks (" +
                "pinyin TEXT NOT NULL, word TEXT NOT NULL, position INTEGER NOT NULL, " +
                "PRIMARY KEY (pinyin, word))"
        )
        db.execSQL("CREATE INDEX ranks_order ON ranks (pinyin, position)")
        listOf("和谐", "屏蔽", "敏感词").forEach { term ->
            db.execSQL("INSERT INTO terms (term) VALUES (?)", arrayOf(term))
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No migration from $oldVersion to $newVersion")
    }

    fun terms(query: String = ""): List<String> {
        val result = ArrayList<String>()
        val cursor = readableDatabase.rawQuery(
            "SELECT term FROM terms WHERE term LIKE ? ESCAPE '\\' ORDER BY term",
            arrayOf("%${escapeLike(query)}%")
        )
        cursor.use { while (it.moveToNext()) result.add(it.getString(0)) }
        return result
    }

    fun addTerm(value: String): Boolean {
        val term = value.trim()
        require(term.length in 1..40 && term.any { Character.isIdeographic(it.code) }) {
            "词条须包含汉字，且不超过 40 字"
        }
        return writableDatabase.insertWithOnConflict(
            "terms", null, ContentValues().apply { put("term", term) },
            SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun removeTerm(term: String) {
        writableDatabase.delete("terms", "term = ?", arrayOf(term))
    }

    fun importTerms(text: String): Int {
        require(text.length <= 1_000_000) { "导入文件过大" }
        var added = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            text.lineSequence().take(10_001).forEachIndexed { index, line ->
                require(index < 10_000) { "最多导入 10000 行" }
                val term = line.trim()
                if (term.isNotEmpty() && addTerm(term)) added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun exportTerms(): String = terms().joinToString(separator = "\n", postfix = "\n")

    fun ordered(pinyin: String, engineOrder: List<WordCandidate>): List<WordCandidate> {
        val stored = rankWords(pinyin)
        if (stored.isEmpty()) return engineOrder
        val byText = engineOrder.associateBy(WordCandidate::text)
        val selected = stored.mapNotNull(byText::get)
        return selected + engineOrder.filterNot { it.text in stored }
    }

    /** One selection exchanges the word with exactly one predecessor. */
    fun promote(pinyin: String, selected: String, current: List<WordCandidate>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = rankWords(pinyin)
            val ordered = if (existing.isEmpty()) current.map(WordCandidate::text).distinct()
            else existing + current.map(WordCandidate::text).filterNot(existing::contains)
            val index = ordered.indexOf(selected)
            if (index > 0) {
                val updated = moveOneStep(ordered, selected)
                updated.forEachIndexed { position, word ->
                    db.insertWithOnConflict(
                        "ranks", null,
                        ContentValues().apply {
                            put("pinyin", pinyin)
                            put("word", word)
                            put("position", position)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            } else if (existing.isEmpty()) {
                ordered.forEachIndexed { position, word ->
                    db.insertWithOnConflict(
                        "ranks", null,
                        ContentValues().apply {
                            put("pinyin", pinyin)
                            put("word", word)
                            put("position", position)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun resetLearning() {
        writableDatabase.delete("ranks", null, null)
    }

    private fun rankWords(pinyin: String): List<String> {
        val result = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT word FROM ranks WHERE pinyin = ? ORDER BY position",
            arrayOf(pinyin)
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.getString(0))
        }
        return result
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
