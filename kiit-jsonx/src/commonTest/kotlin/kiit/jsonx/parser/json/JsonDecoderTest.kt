package kiit.jsonx.parser.json

import kotlin.test.Test
import kotlin.test.assertEquals

// =================================================================================================
// JsonDecoderTest: decodeString/decodeNumber, direct from raw JsonLexer-shaped text
// =================================================================================================
class JsonDecoderTest {
    private val decoder = JsonDecoder()

    // --- decodeString ----------------------------------------------------------------------

    @Test
    fun decodeString_plain() {
        assertEquals("hello", decoder.decodeString("\"hello\""))
    }

    @Test
    fun decodeString_empty() {
        assertEquals("", decoder.decodeString("\"\""))
    }

    @Test
    fun decodeString_standardEscapes() {
        val raw = """"\"\\\/\b\f\n\r\t""""
        assertEquals("\"\\/\b\n\r\t", decoder.decodeString(raw))
    }

    @Test
    fun decodeString_unicodeEscape() {
        assertEquals("Aé", decoder.decodeString("\"\\u0041\\u00e9\""))
    }

    @Test
    fun decodeString_surrogatePair() {
        assertEquals("😀", decoder.decodeString("\"\\uD83D\\uDE00\""))
    }

    // --- decodeNumber ----------------------------------------------------------------------

    @Test
    fun decodeNumber_integer() {
        val result = decoder.decodeNumber("42")
        assertEquals(42L, result.long)
        assertEquals(null, result.double)
    }

    @Test
    fun decodeNumber_negativeInteger() {
        val result = decoder.decodeNumber("-17")
        assertEquals(-17L, result.long)
    }

    @Test
    fun decodeNumber_decimal() {
        val result = decoder.decodeNumber("3.14")
        assertEquals(null, result.long)
        assertEquals(3.14, result.double)
    }

    @Test
    fun decodeNumber_exponent() {
        val result = decoder.decodeNumber("1e3")
        assertEquals(1000.0, result.double)
    }

    @Test
    fun decodeNumber_overflowingLong_fallsBackToDouble() {
        val result = decoder.decodeNumber("99999999999999999999999999")
        assertEquals(null, result.long)
        assertEquals(1.0E26, result.double)
    }
}
