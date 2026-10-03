package uk.cassiangroup.fkinput.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompositionStateTest {
    @Test fun selectsAndRevisesPartsBeforeCommit() {
        val state = CompositionState()
        state.insert("nihaoma")
        state.setSegmentEnd(2)
        assertEquals("ni", state.candidateInput)
        assertNull(state.choose("你"))
        assertEquals("haoma", state.raw)
        state.setSegmentEnd(3)
        assertNull(state.choose("好"))
        state.reopen(0)
        assertEquals("nihaoma", state.raw)
        assertEquals(0, state.selectedParts.size)
        state.setSegmentEnd(2)
        assertNull(state.choose("尼"))
        assertEquals("尼好吗", state.choose("好吗"))
        assertEquals(false, state.hasContent)
    }

    @Test fun cursorEditingAndBackspaceReopenPreviousPart() {
        val state = CompositionState()
        state.insert("nihao")
        state.moveCursor(-1)
        state.insert("m")
        assertEquals("nihamo", state.raw)
        state.backspace()
        assertEquals("nihao", state.raw)
        state.setSegmentEnd(2)
        state.choose("你")
        state.moveCursor(-3)
        state.backspace()
        assertEquals("nihao", state.raw)
        assertEquals(2, state.cursor)
    }
}
