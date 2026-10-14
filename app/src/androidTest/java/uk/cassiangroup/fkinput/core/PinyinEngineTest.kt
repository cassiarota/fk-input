package uk.cassiangroup.fkinput.core

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PinyinEngineTest {
    @Test fun upgradeRefreshesPreviouslyCopiedRimeData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dictionary = File(context.filesDir, "rime-shared/pinyin_simp.dict.yaml")
        dictionary.parentFile!!.mkdirs()
        dictionary.writeText("---\nname: pinyin_simp\nversion: '0.1'\nsort: by_weight\n...\n妈\tma\t100\n")
        val engine = PinyinEngine(context)
        try {
            assertEquals(context.assets.open("data/pinyin_simp.dict.yaml").bufferedReader().use { it.readText() },
                dictionary.readText())
            val native = NativeRime(context)
            assertTrue("妈的" in native.candidates("made"))
        } finally { engine.close() }
    }

    @Test fun leadingSegmentCandidatesConsumeOnlyTheirOwnKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = PinyinEngine(context)
        try {
            assertTrue(2 in engine.segmentLengths("nihao"))
            assertTrue(engine.exactGroups("ni").isNotEmpty())
            assertTrue(engine.exactGroups("ni").all { group ->
                group.pinyin.replace(" ", "").replace("'", "") == "ni"
            })
            assertTrue(engine.groups("64426").isEmpty())
        } finally { engine.close() }
    }

    @Test fun commonCompleteWordsPrecedeArtificialCombinations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = PinyinEngine(context)
        try {
            for ((input, expected) in mapOf(
                "made" to "妈的", "nihao" to "你好", "xiexie" to "谢谢",
                "women" to "我们", "haode" to "好的", "wocao" to "卧槽",
                "keyima" to "可以吗", "zenmehuishi" to "怎么回事",
            )) {
                assertEquals(input, expected, engine.groups(input).first().candidates.first().text)
            }
            val native = NativeRime(context)
            assertTrue("Rime must load the new conversational dictionary", "妈的" in native.candidates("made"))
            val sentence = engine.groups("wojintianhenkaixin").first()
            assertEquals("wo jin tian hen kai xin", sentence.pinyin)
            assertEquals("我今天很开心", sentence.candidates.first().text)
            assertEquals("我今天", engine.groups("wojinti").first().candidates.first().text)
        } finally { engine.close() }
    }

    @Test fun unavailableNativeBackendStillUsesCompleteWordsAndNaturalSegments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val blockedDirectory = File(context.cacheDir, "blocked-rime-path").apply { writeText("not a directory") }
        val fallbackContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = blockedDirectory
        }
        val engine = PinyinEngine(fallbackContext)
        try {
            assertEquals("妈的", engine.groups("made").first().candidates.first().text)
            assertEquals("你好", engine.groups("nihao").first().candidates.first().text)
            assertEquals("我今天很开心", engine.groups("wojintianhenkaixin").first().candidates.first().text)
        } finally {
            engine.close()
            blockedDirectory.delete()
        }
    }

    @Test fun eachNormalCandidateGetsItsOwnStrictHomophone() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = PinyinEngine(context)
        val rules = CandidateRules.fromAssets(context)
        try {
            val normal = engine.groups("made").first().candidates
            assertEquals("妈的", normal.first().text)
            val paired = normal.map { rules.homophones(it.text, emptyList(), limit = 1, pinyin = it.pinyin).firstOrNull() }
            assertEquals(normal.size, paired.size)
            assertEquals("妈地", paired.first())
            assertFalse(rules.samePronunciation("妈的", "吗得", "ma de"))
            for (input in listOf("made", "nihao", "women", "chongqing", "zhongliang", "kuaile", "xiexie")) {
                for (word in engine.groups(input).first().candidates) {
                    for (homophone in rules.homophones(word.text, emptyList(), pinyin = word.pinyin)) {
                        assertNotEquals(word.text, homophone)
                        assertTrue("${word.text} -> $homophone", rules.samePronunciation(word.text, homophone, word.pinyin))
                    }
                }
            }
            assertEquals(listOf("chong" to 2, "qing" to 4),
                rules.pronunciation("重庆", "chong qing").map { it!!.syllable to it.tone })
        } finally { engine.close() }
    }
}
