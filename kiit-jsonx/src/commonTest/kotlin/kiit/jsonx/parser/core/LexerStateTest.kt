package kiit.jsonx.parser.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =================================================================================================
// LexerStateTest: offset/line/column tracking across all line-terminator forms
// =================================================================================================
class LexerStateTest {
    @Test
    fun startsAtOffsetZeroLineOneColumnOne() {
        val state = LexerState("abc")
        assertEquals(0, state.offset)
        assertEquals(1, state.line)
        assertEquals(1, state.column)
        assertFalse(state.isAtEnd())
    }

    @Test
    fun advance_onPlainCharacters_incrementsColumnOnly() {
        val state = LexerState("ab")
        assertEquals('a', state.advance())
        assertEquals(1, state.offset)
        assertEquals(1, state.line)
        assertEquals(2, state.column)

        assertEquals('b', state.advance())
        assertTrue(state.isAtEnd())
    }

    @Test
    fun advance_onLineFeed_incrementsLineAndResetsColumn() {
        val state = LexerState("a\nb")
        state.advance() // 'a'
        assertEquals('\n', state.advance())
        assertEquals(2, state.line)
        assertEquals(1, state.column)
        assertEquals(2, state.offset)
    }

    @Test
    fun advance_onCarriageReturnOnly_incrementsLine() {
        val state = LexerState("a\rb")
        state.advance() // 'a'
        assertEquals('\r', state.advance())
        assertEquals(2, state.line)
        assertEquals(1, state.column)
        assertEquals(2, state.offset)
    }

    @Test
    fun advance_onCarriageReturnLineFeed_consumesBothAsOneTerminator() {
        val state = LexerState("a\r\nb")
        state.advance() // 'a', offset 1

        assertEquals('\r', state.advance())
        assertEquals(3, state.offset) // both \r and \n consumed together
        assertEquals(2, state.line)
        assertEquals(1, state.column)

        assertEquals('b', state.advance())
        assertEquals(4, state.offset)
        assertTrue(state.isAtEnd())
    }

    @Test
    fun advance_onLineSeparatorU2028_incrementsLine() {
        val state = LexerState("a b")
        state.advance() // 'a'
        assertEquals(' ', state.advance())
        assertEquals(2, state.line)
        assertEquals(1, state.column)
    }

    @Test
    fun advance_onParagraphSeparatorU2029_incrementsLine() {
        val state = LexerState("a b")
        state.advance() // 'a'
        assertEquals(' ', state.advance())
        assertEquals(2, state.line)
        assertEquals(1, state.column)
    }

    @Test
    fun peek_looksAheadWithoutConsuming() {
        val state = LexerState("xyz")
        assertEquals('x', state.peek())
        assertEquals('y', state.peek(1))
        assertEquals('z', state.peek(2))
        assertNull(state.peek(3))
        assertEquals(0, state.offset) // unchanged
    }

    @Test
    fun advance_atEnd_throws() {
        val state = LexerState("")
        assertTrue(state.isAtEnd())
        assertFailsWith<IllegalStateException> { state.advance() }
    }

    @Test
    fun snapshot_capturesCurrentPosition() {
        val state = LexerState("ab\ncd")
        state.advance() // 'a'
        state.advance() // 'b'
        state.advance() // '\n'

        val position = state.snapshot()
        assertEquals(SourcePosition(offset = 3, line = 2, column = 1), position)
    }
}
