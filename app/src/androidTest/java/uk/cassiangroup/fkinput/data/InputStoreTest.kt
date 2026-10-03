package uk.cassiangroup.fkinput.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.cassiangroup.fkinput.core.WordCandidate

@RunWith(AndroidJUnit4::class)
class InputStoreTest {
    @Test fun termsAndOneStepOrderSurviveReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("fk-input.db")
        val initial = listOf("和", "河", "何").mapIndexed { index, word ->
            WordCandidate(word, "he", 3 - index)
        }
        InputStore(context).use { store ->
            assertEquals(listOf("和谐", "屏蔽", "敏感词"), store.terms())
            assertTrue(store.addTerm("和平"))
            assertFalse(store.addTerm("和平"))
            store.promote("he", "何", initial)
            assertEquals(listOf("和", "何", "河"), store.ordered("he", initial).map { it.text })
        }
        InputStore(context).use { store ->
            assertTrue("和平" in store.terms())
            assertEquals(listOf("和", "何", "河"), store.ordered("he", initial).map { it.text })
            store.promote("he", "何", store.ordered("he", initial))
            assertEquals(listOf("何", "和", "河"), store.ordered("he", initial).map { it.text })
            store.resetLearning()
            assertEquals(listOf("和", "河", "何"), store.ordered("he", initial).map { it.text })
        }
    }

    @Test fun upgradesOldDatabaseAndImportsBundledAndUserSensitiveWords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("fk-input.db")
        val old = context.openOrCreateDatabase("fk-input.db", Context.MODE_PRIVATE, null)
        old.execSQL("CREATE TABLE terms (term TEXT PRIMARY KEY NOT NULL)")
        old.execSQL("CREATE TABLE ranks (pinyin TEXT NOT NULL, word TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (pinyin, word))")
        old.execSQL("INSERT INTO terms (term) VALUES ('旧词')")
        old.version = 1
        old.close()
        val file = SensitiveWordFile(context).path
        file.writeText("已有文件词\n", Charsets.UTF_8)
        val bundledFirst = context.assets.open("data/sensitive-words.txt").bufferedReader().use { it.readLine() }
        try {
            InputStore(context).use { store ->
                assertTrue("旧词" in store.terms())
                val repository = SensitiveWordRepository(context, store)
                repository.initialize()
                assertEquals(listOf("已有文件词"), store.sensitiveWords())
                assertTrue(repository.add("用户新增词"))
                assertFalse(repository.add("用户新增词"))
            }
            InputStore(context).use { store ->
                assertEquals(listOf("已有文件词", "用户新增词"), store.sensitiveWords())
            }
            file.delete()
            InputStore(context).use { store ->
                SensitiveWordRepository(context, store).initialize()
                assertTrue(bundledFirst in store.sensitiveWords())
                assertTrue("用户新增词" in store.sensitiveWords())
                assertTrue("用户新增词" in file.readLines(Charsets.UTF_8))
            }
        } finally {
            file.delete()
        }
    }
}
