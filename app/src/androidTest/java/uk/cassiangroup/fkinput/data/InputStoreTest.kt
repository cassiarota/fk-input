package uk.cassiangroup.fkinput.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
