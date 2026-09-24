package uk.cassiangroup.fkinput.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRulesTest {
    private val rules = CandidateRules(
        mapOf(
            '和' to Reading("he", 2, 100),
            '河' to Reading("he", 2, 70),
            '何' to Reading("he", 2, 60),
            '贺' to Reading("he", 4, 90),
            '谐' to Reading("xie", 2, 100),
            '鞋' to Reading("xie", 2, 80),
            '斜' to Reading("xie", 2, 70),
        )
    )

    @Test fun longestMatchReplacesEveryCharacter() {
        assertEquals(
            listOf(0, 1, 2, 3),
            sensitivePositions("和平使者", listOf("和平", "和平使者"))
        )
        val results = rules.homophones("和谐", listOf("和谐"))
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it[0] != '和' && it[1] != '谐' })
    }

    @Test fun unmatchedWordChangesOneLastCharacter() {
        val results = rules.homophones("和谐", emptyList())
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it[0] == '和' && it[1] != '谐' })
    }

    @Test fun sameTonePrecedesOtherToneAndSensitiveOutputsAreRemoved() {
        val results = rules.homophones("和", listOf("和"))
        assertEquals("河", results.first())
        assertEquals("贺", results.last())
        assertFalse(rules.homophones("和谐", listOf("和谐", "河鞋")).contains("河鞋"))
    }

    @Test fun missingAlternativeNeverFallsBackToOriginal() {
        assertTrue(rules.homophones("未知", emptyList()).isEmpty())
    }

    @Test fun unmatchedWordUsesEarlierCharacterWhenLastHasNoReading() {
        assertEquals(listOf("河知", "何知", "贺知"), rules.homophones("和知", emptyList()))
    }
}
