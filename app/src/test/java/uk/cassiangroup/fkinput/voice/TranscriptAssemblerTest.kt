package uk.cassiangroup.fkinput.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptAssemblerTest {
    @Test fun partialReplacesSnapshotAndFinalSealsSegment() {
        val transcript = TranscriptAssembler()
        transcript.partial(0, "你")
        transcript.partial(0, "你好")
        assertEquals("你好", transcript.preview())
        transcript.final(0, "你好。")
        transcript.partial(0, "错误的迟到结果")
        transcript.partial(1, "world")
        assertEquals("你好。world", transcript.preview())
        assertEquals("你好。", transcript.completed())
        transcript.final(1, "world!")
        assertEquals("你好。world!", transcript.completed())
    }

    @Test fun englishSegmentsReceiveASeparator() {
        assertEquals("Hello world", TranscriptAssembler.joinSegments(listOf("Hello", "world")))
    }
}
