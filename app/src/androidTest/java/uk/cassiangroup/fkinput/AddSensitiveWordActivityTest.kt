package uk.cassiangroup.fkinput

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.cassiangroup.fkinput.data.SensitiveWordFile

@RunWith(AndroidJUnit4::class)
class AddSensitiveWordActivityTest {
    @Test fun selectedChatTextSeedsAndAddsOnlyOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = SensitiveWordFile(context).path
        file.delete()
        val selection = "专属测试敏感词"
        val bundledFirst = context.assets.open("data/sensitive-words.txt")
            .bufferedReader().use { it.readLine() }
        try {
            repeat(2) {
                instrumentation.startActivitySync(
                    Intent(context, AddSensitiveWordActivity::class.java)
                        .setAction(Intent.ACTION_PROCESS_TEXT)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_PROCESS_TEXT, selection)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                onView(withId(android.R.id.button1)).perform(click())
                instrumentation.waitForIdleSync()
            }
            val entries = file.readLines(Charsets.UTF_8)
            assertEquals(2_483, entries.size)
            assertEquals(1, entries.count { it == selection })
            assertTrue(entries.contains(bundledFirst))
        } finally {
            file.delete()
        }
    }
}
