package kiit.jsonx.parser.dialect

import kotlin.test.Test
import kotlin.test.assertEquals

// =================================================================================================
// JsonXDecoderTest: triple-quoted string decoding (leading-newline trim, line continuation
// eating following whitespace, standard escapes) — decodeNumber is untouched, no test needed.
// =================================================================================================
class JsonXDecoderTest {
    private val decoder = JsonXDecoder()

    @Test
    fun decodeString_tripleQuoted_plain() {
        assertEquals("hello", decoder.decodeString("\"\"\"hello\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_empty() {
        assertEquals("", decoder.decodeString("\"\"\"\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_trimsImmediateLeadingNewline() {
        assertEquals("hello", decoder.decodeString("\"\"\"\nhello\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_trimsImmediateLeadingCrLf() {
        assertEquals("hello", decoder.decodeString("\"\"\"\r\nhello\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_onlyTrimsTheFirstNewline() {
        assertEquals("\nhello", decoder.decodeString("\"\"\"\n\nhello\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_preservesInternalRawLineBreaks() {
        assertEquals("line one\nline two", decoder.decodeString("\"\"\"line one\nline two\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_preservesUnescapedQuoteRuns() {
        assertEquals("a \" b \"\" c", decoder.decodeString("\"\"\"a \" b \"\" c\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_lineContinuationRemovesNewlineAndLeadingWhitespace() {
        // TOML rule: a backslash at end-of-line trims the terminator AND all following
        // whitespace up to the next non-whitespace character — wider than JSON5's own line
        // continuation, which only removes the terminator itself.
        assertEquals("ab", decoder.decodeString("\"\"\"a\\\n   b\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_lineContinuationAcrossMultipleBlankLines() {
        assertEquals("ab", decoder.decodeString("\"\"\"a\\\n\n   \n  b\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_standardEscapesStillDecode() {
        assertEquals("a\tb\nc", decoder.decodeString("\"\"\"a\\tb\\nc\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_hexAndUnicodeEscapesStillDecode() {
        assertEquals("A\u00e9", decoder.decodeString("\"\"\"\\x41\\u00e9\"\"\""))
    }

    @Test
    fun decodeString_tripleQuoted_nonEscapeCharacterPassthroughStillWorks() {
        assertEquals("q", decoder.decodeString("\"\"\"\\q\"\"\""))
    }

    @Test
    fun decodeString_regularDoubleQuoted_stillDelegatesToJson5Decoder() {
        assertEquals("hello", decoder.decodeString("\"hello\""))
    }

    @Test
    fun decodeString_singleQuoted_stillDelegatesToJson5Decoder() {
        assertEquals("hello", decoder.decodeString("'hello'"))
    }

    @Test
    fun decodeNumber_hex_stillDelegatesToJson5Decoder() {
        assertEquals(26L, decoder.decodeNumber("0x1A").long)
    }
}
