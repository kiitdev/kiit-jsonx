package kiit.jsonx.parser.json5

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXBoolean
import kiit.jsonx.element.JsonXElement.JsonXNull
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.options.ParseOptions
import kiit.result.Failure
import kiit.result.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// =================================================================================================
// Json5ParserTest: trailing commas, unquoted/single-quoted keys, JSON5 number/string decoding
// =================================================================================================
class Json5ParserTest {
    private fun parseDocument(text: String, options: ParseOptions = ParseOptions()): JsonXElement =
        Json5Parser(Json5Lexer(text), options).parseDocument()

    // --- inherited strict-JSON behavior still works --------------------------------------------

    @Test
    fun inheritsStandardObjectAndArrayParsing() {
        val expected = JsonXObject(linkedMapOf("a" to JsonXArray(listOf(JsonXNumber.of(1L), JsonXBoolean(true)))))
        assertEquals(expected, parseDocument("""{"a": [1, true]}"""))
    }

    @Test
    fun emptyInput_yieldsEmptyObject() {
        assertEquals(JsonXObject(LinkedHashMap()), parseDocument(""))
    }

    // --- trailing commas -------------------------------------------------------------------------

    @Test
    fun trailingComma_inObject_isAllowed() {
        val expected = JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L)))
        assertEquals(expected, parseDocument("""{"a": 1,}"""))
    }

    @Test
    fun trailingComma_inArray_isAllowed() {
        assertEquals(JsonXArray(listOf(JsonXNumber.of(1L), JsonXNumber.of(2L))), parseDocument("[1, 2,]"))
    }

    @Test
    fun trailingComma_inEmptyObject_stillFails() {
        // a comma with nothing before it is not a "trailing" comma, there's no entry to trail
        assertFailsWith<JsonXParseException> { parseDocument("{,}") }
    }

    @Test
    fun trailingComma_inEmptyArray_stillFails() {
        assertFailsWith<JsonXParseException> { parseDocument("[,]") }
    }

    @Test
    fun doubleTrailingComma_stillFails() {
        assertFailsWith<JsonXParseException> { parseDocument("[1,,]") }
    }

    // --- unquoted and single-quoted keys ---------------------------------------------------------

    @Test
    fun unquotedIdentifierKey_isAccepted() {
        val result = parseDocument("{a: 1}") as JsonXObject
        assertEquals(JsonXNumber.of(1L), result.entries["a"])
    }

    @Test
    fun singleQuotedKey_isAccepted() {
        val result = parseDocument("{'a': 1}") as JsonXObject
        assertEquals(JsonXNumber.of(1L), result.entries["a"])
    }

    @Test
    fun mixedKeyStyles_inOneObject() {
        val result = parseDocument("""{a: 1, 'b': 2, "c": 3}""") as JsonXObject
        assertEquals(listOf("a", "b", "c"), result.entries.keys.toList())
    }

    @Test
    fun bareIdentifier_asValue_fails() {
        // unquoted identifiers are only valid in key position, never as a value
        assertFailsWith<JsonXParseException> { parseDocument("[foo]") }
    }

    @Test
    fun trueFalseNull_asValues_stillWork() {
        assertEquals(JsonXBoolean(true), parseDocument("true"))
        assertEquals(JsonXBoolean(false), parseDocument("false"))
        assertEquals(JsonXNull, parseDocument("null"))
    }

    // --- comments interspersed with structure ---------------------------------------------------

    @Test
    fun commentsAreIgnoredThroughout() {
        val source =
            """
            {
              // leading comment
              a: 1, /* trailing */
            }
            """.trimIndent()
        val result = parseDocument(source) as JsonXObject
        assertEquals(JsonXNumber.of(1L), result.entries["a"])
    }

    // --- number decoding: hex, Infinity, NaN ------------------------------------------------------

    @Test
    fun hexNumber_decodesToLong() {
        assertEquals(JsonXNumber.of(26L), parseDocument("0x1A"))
    }

    @Test
    fun hexNumber_negative_decodesToLong() {
        assertEquals(JsonXNumber.of(-26L), parseDocument("-0x1A"))
    }

    @Test
    fun hexNumber_overflowingLong_fallsBackToDouble() {
        val result = parseDocument("0x${"F".repeat(20)}") as JsonXNumber
        assertEquals(null, result.long)
        assertTrue(result.double!! > 0.0)
    }

    @Test
    fun infinity_decodesToPositiveInfinity() {
        assertEquals(JsonXNumber.of(Double.POSITIVE_INFINITY), parseDocument("Infinity"))
    }

    @Test
    fun negativeInfinity_decodesToNegativeInfinity() {
        assertEquals(JsonXNumber.of(Double.NEGATIVE_INFINITY), parseDocument("-Infinity"))
    }

    @Test
    fun nan_decodesToNaN() {
        val result = parseDocument("NaN") as JsonXNumber
        assertTrue(result.double!!.isNaN())
    }

    @Test
    fun leadingDecimalPoint_decodesCorrectly() {
        assertEquals(JsonXNumber.of(0.5), parseDocument(".5"))
    }

    @Test
    fun trailingDecimalPoint_decodesCorrectly() {
        assertEquals(JsonXNumber.of(5.0), parseDocument("5."))
    }

    @Test
    fun leadingPlus_decodesCorrectly() {
        assertEquals(JsonXNumber.of(5L), parseDocument("+5"))
    }

    // --- string decoding: line continuation, hex escape, vertical tab, NUL, NonEscapeCharacter --

    @Test
    fun lineContinuation_isRemovedFromDecodedValue() {
        val result = parseDocument("\"a\\\nb\"") as JsonXString
        assertEquals("ab", result.value)
    }

    @Test
    fun hexEscape_decodesCorrectly() {
        val result = parseDocument("\"\\x41\"") as JsonXString
        assertEquals("A", result.value)
    }

    @Test
    fun verticalTabEscape_decodesCorrectly() {
        val result = parseDocument("\"\\v\"") as JsonXString
        assertEquals("\u000B", result.value)
    }

    @Test
    fun nullEscape_decodesCorrectly() {
        val result = parseDocument("\"\\0\"") as JsonXString
        assertEquals("\u0000", result.value)
    }

    @Test
    fun nonEscapeCharacter_decodesToItself() {
        val result = parseDocument("\"\\q\"") as JsonXString
        assertEquals("q", result.value)
    }

    @Test
    fun singleQuotedString_decodesCorrectly() {
        val result = parseDocument("'hello'") as JsonXString
        assertEquals("hello", result.value)
    }

    // --- duplicate key policy (inherited from JsonParser) -----------------------------------------

    @Test
    fun duplicateKey_defaultPolicyIsError() {
        assertFailsWith<JsonXParseException> { parseDocument("{a: 1, a: 2}") }
    }

    @Test
    fun duplicateKey_lastWins() {
        val options = ParseOptions(duplicateKeyPolicy = ParseOptions.DuplicateKeyPolicy.LastWins)
        val result = parseDocument("{a: 1, a: 2}", options) as JsonXObject
        assertEquals(JsonXNumber.of(2L), result.entries["a"])
    }

    // --- nesting -----------------------------------------------------------------------------

    @Test
    fun nestedObjectsAndArrays_withTrailingCommas() {
        val source = "{a: {b: [1, 2,],},}"
        val expected =
            JsonXObject(
                linkedMapOf(
                    "a" to
                        JsonXObject(
                            linkedMapOf("b" to JsonXArray(listOf(JsonXNumber.of(1L), JsonXNumber.of(2L)))),
                        ),
                ),
            )
        assertEquals(expected, parseDocument(source))
    }

    // --- error cases -----------------------------------------------------------------------------

    @Test
    fun error_missingColon_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("{a 1}") }
    }

    @Test
    fun error_unterminatedObject_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("{a: 1") }
    }

    @Test
    fun error_numericKey_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("{1: 2}") }
    }

    // --- public boundary (Json5Parser.parse) ---------------------------------------------------

    @Test
    fun parse_validInput_returnsSuccess() {
        val result = Json5Parser.parse("{a: 1,}")
        assertTrue(result is Success)
        assertEquals(JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L))), result.value)
    }

    @Test
    fun parse_invalidInput_returnsFailure() {
        assertTrue(Json5Parser.parse("{,}") is Failure)
    }
}
