package uk.cassiangroup.fkinput.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinyinEngineTest {
    @Test fun leadingSegmentCandidatesConsumeOnlyTheirOwnKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = PinyinEngine(context)
        try {
            assertTrue(2 in engine.segmentLengths("nihao", KeyboardLayout.FULL_KEY))
            assertTrue(engine.exactGroups("ni", KeyboardLayout.FULL_KEY).isNotEmpty())
            assertTrue(engine.exactGroups("ni", KeyboardLayout.FULL_KEY).all { group ->
                group.pinyin.replace(" ", "").replace("'", "") == "ni"
            })
            val nine = PinyinEngine.toNineKey("ni")
            assertTrue(nine.length in engine.segmentLengths(PinyinEngine.toNineKey("nihao"), KeyboardLayout.NINE_KEY))
            assertTrue(engine.exactGroups(nine, KeyboardLayout.NINE_KEY).isNotEmpty())
        } finally { engine.close() }
    }
}
