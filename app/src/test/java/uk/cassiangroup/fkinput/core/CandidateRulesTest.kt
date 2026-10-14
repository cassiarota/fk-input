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

    @Test fun differentTonesAndSensitiveOutputsAreRemoved() {
        val results = rules.homophones("和", listOf("和"))
        assertEquals("河", results.first())
        assertEquals(listOf("河", "何"), results)
        assertFalse(results.contains("贺"))
        assertFalse(rules.homophones("和谐", listOf("和谐", "河鞋")).contains("河鞋"))
    }

    @Test fun missingAlternativeNeverFallsBackToOriginal() {
        assertTrue(rules.homophones("未知", emptyList()).isEmpty())
    }

    @Test fun unmatchedWordUsesEarlierCharacterWhenLastHasNoReading() {
        assertEquals(listOf("河知", "何知"), rules.homophones("和知", emptyList()))
    }

    @Test fun noSameToneAlternativeDoesNotUseAnotherTone() {
        val strict = CandidateRules(mapOf('妈' to Reading("ma", 1, 100), '吗' to Reading("ma", 5, 200)))
        assertTrue(strict.homophones("妈", emptyList()).isEmpty())
        assertFalse(strict.samePronunciation("妈", "吗"))
    }

    @Test fun neutralToneDoesNotBecomeSecondTone() {
        val strict = CandidateRules(mapOf(
            '妈' to Reading("ma", 1, 100), '吗' to Reading("ma", 5, 200),
            '的' to Reading("de", 5, 300), '地' to Reading("de", 5, 200),
            '得' to Reading("de", 2, 400),
        ))
        assertEquals(listOf("妈地"), strict.homophones("妈的", emptyList(), pinyin = "ma de"))
        assertFalse(strict.samePronunciation("妈的", "吗得", "ma de"))
    }

    @Test fun phraseReadingDisambiguatesPolyphonicCharacters() {
        val strict = CandidateRules(mapOf(
            '重' to Reading("zhong", 4, 100), '崇' to Reading("chong", 2, 80),
            '众' to Reading("zhong", 4, 90), '庆' to Reading("qing", 4, 100),
        ), mapOf("重庆" to listOf(Reading("chong", 2, 0), Reading("qing", 4, 0))))
        assertEquals(listOf("崇庆"), strict.homophones("重庆", emptyList(), pinyin = "chong qing"))
        assertFalse(strict.samePronunciation("重庆", "众庆", "chong qing"))
    }

    @Test fun rewrittenPhraseCannotChangeContextualTone() {
        val strict = CandidateRules(mapOf(
            '和' to Reading("he", 2, 100), '河' to Reading("he", 2, 70),
            '谐' to Reading("xie", 2, 100), '鞋' to Reading("xie", 2, 80),
        ), mapOf("河鞋" to listOf(Reading("he", 4, 0), Reading("xie", 2, 0))))
        assertTrue(strict.homophones("和谐", listOf("和谐"), pinyin = "he xie").isEmpty())
    }
}
