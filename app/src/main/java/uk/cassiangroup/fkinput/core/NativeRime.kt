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
            "fk_nine.schema.yaml",
            "pinyin_simp.dict.yaml",
        ).forEach { name ->
            val destination = File(shared, name)
            if (!destination.exists()) {
                context.assets.open("data/$name").use { source ->
                    destination.outputStream().use(source::copyTo)
                }
            }
        }
        check(nativeInit(shared.absolutePath, user.absolutePath)) { "Rime initialization failed" }
    }

    fun candidates(input: String, layout: KeyboardLayout): List<String> =
        nativeCandidates(input, layout == KeyboardLayout.NINE_KEY).toList()

    fun close() = nativeClose()

    private external fun nativeInit(sharedPath: String, userPath: String): Boolean
    private external fun nativeCandidates(input: String, nineKey: Boolean): Array<String>
    private external fun nativeClose()
}
