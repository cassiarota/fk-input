package uk.cassiangroup.fkinput.core

import android.content.Context
import java.io.File

/** Thin original JNI wrapper over the BSD-licensed librime C API. */
class NativeRime(context: Context) {
    private val shared = File(context.filesDir, "rime-shared")
    private val user = File(context.filesDir, "rime-user")

    init {
        System.loadLibrary("fk_rime")
        shared.mkdirs()
        user.mkdirs()
        listOf(
            "default.yaml",
            "fk_pinyin.schema.yaml",
            "pinyin_simp.dict.yaml",
            "conversation.dict.yaml",
        ).forEach { name ->
            val destination = File(shared, name)
            val data = context.assets.open("data/$name").use { it.readBytes() }
            if (!destination.exists() || !destination.readBytes().contentEquals(data)) {
                destination.writeBytes(data)
            }
        }
        check(nativeInit(shared.absolutePath, user.absolutePath)) { "Rime initialization failed" }
    }

    fun candidates(input: String): List<String> = nativeCandidates(input).toList()

    fun close() = nativeClose()

    private external fun nativeInit(sharedPath: String, userPath: String): Boolean
    private external fun nativeCandidates(input: String): Array<String>
    private external fun nativeClose()
}
