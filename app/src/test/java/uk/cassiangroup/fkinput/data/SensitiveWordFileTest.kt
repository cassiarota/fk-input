package uk.cassiangroup.fkinput.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SensitiveWordFileTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun seedsOnceAndKeepsUserAdditions() {
        val file = temporary.newFolder("home").resolve("sensitive-words.txt")
        val store = SensitiveWordFile(file) { ByteArrayInputStream("词一\n词二\n".toByteArray()) }

        store.initialize()
        assertEquals("词一\n词二\n", file.readText())
        assertTrue(store.add("  新词  "))
        assertFalse(store.add("新词"))
        SensitiveWordFile(file) { error("Existing file must not be replaced") }.initialize()
        assertEquals("词一\n词二\n新词\n", file.readText())
    }

    @Test fun rejectsMultilineAndOversizedSelectionsBeforeCreatingFile() {
        val file = temporary.newFolder("home").resolve("sensitive-words.txt")
        val store = SensitiveWordFile(file) { ByteArrayInputStream("默认\n".toByteArray()) }

        assertThrows(IllegalArgumentException::class.java) { store.add("第一行\n第二行") }
        assertThrows(IllegalArgumentException::class.java) { store.add("词".repeat(101)) }
        assertFalse(file.exists())
    }

    @Test fun keepsExistingFileWhenBundledCopyIsUnavailable() {
        val file = temporary.newFolder("home").resolve("sensitive-words.txt")
        file.writeText("用户自定义\n")
        val store = SensitiveWordFile(file) { error("Bundled file unavailable") }

        assertTrue(store.add("另一个词"))
        assertEquals("用户自定义\n另一个词\n", file.readText())
    }
}
