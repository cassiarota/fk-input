package uk.cassiangroup.fkinput.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun selectedSensitiveWord(selection: String): String {
    val word = selection.trim()
    require(word.isNotEmpty() && word.codePointCount(0, word.length) <= 100 &&
        word.none { Character.isISOControl(it) || it == '\u2028' || it == '\u2029' }) {
        "请选择不超过 100 字的单行词或短语"
    }
    return word
}

/** Private UTF-8 word list seeded once from the APK and editable by the user. */
class SensitiveWordFile(private val file: File, private val bundled: () -> InputStream) {
    constructor(context: Context) : this(
        File(context.filesDir, FILE_NAME),
        { context.assets.open("data/sensitive-words.txt") }
    )

    val path: File get() = file

    /** Preserve an existing user file, including one edited outside the app. */
    fun initialize(): File = synchronized(lock) {
        if (file.exists()) {
            require(file.isFile) { "敏感词库路径不是文件" }
        } else {
            writeAtomically(bundled().use { it.readBytes() })
        }
        file
    }

    /** Add one selected word or phrase without rewriting an existing entry. */
    fun add(selection: String): Boolean = synchronized(lock) {
        val word = selectedSensitiveWord(selection)
        initialize()
        val content = file.readText(Charsets.UTF_8)
        if (content.lineSequence().any { it == word }) return@synchronized false
        val separator = if (content.isNotEmpty() && !content.endsWith('\n')) "\n" else ""
        writeAtomically("$content$separator$word\n".toByteArray(Charsets.UTF_8))
        true
    }

    private fun writeAtomically(bytes: ByteArray) {
        val parent = file.parentFile ?: error("敏感词库路径无父目录")
        require(parent.isDirectory || parent.mkdirs()) { "无法创建敏感词库目录" }
        val temporary = File.createTempFile(".sensitive-words-", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "sensitive-words.txt"
        val lock = Any()
    }
}
