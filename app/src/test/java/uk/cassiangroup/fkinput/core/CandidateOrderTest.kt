package uk.cassiangroup.fkinput.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateOrderTest {
    @Test fun eachSelectionMovesOnlyOnePosition() {
        var order = listOf("甲", "乙", "丙", "丁")
        order = moveOneStep(order, "丁")
        assertEquals(listOf("甲", "乙", "丁", "丙"), order)
        order = moveOneStep(order, "丁")
        assertEquals(listOf("甲", "丁", "乙", "丙"), order)
        order = moveOneStep(order, "丁")
        assertEquals(listOf("丁", "甲", "乙", "丙"), order)
        assertEquals(order, moveOneStep(order, "丁"))
    }
}
