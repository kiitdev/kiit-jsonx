package kiit.jsonx.parser.json

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
// JsonParserTest: JsonParser tree construction, decoding, duplicate-key policies, error cases
// =================================================================================================
class JsonParserTest {
    private fun parseDocument(text: String, options: ParseOptions = ParseOptions()): JsonXElement {
        return JsonParser(JsonLexer(text), options).parseDocument()
    }

    // --- empty input -----------------------------------------------------------------------

    @Test
    fun emptyInput_yieldsEmptyObject() {
        assertEquals(JsonXObject(LinkedHashMap()), parseDocument(""))
    }

    @Test
    fun whitespaceOnlyInput_yieldsEmptyObject() {
        assertEquals(JsonXObject(LinkedHashMap()), parseDocument("   \n\t  "))
    }

    // --- scalars -------------------------------------------------------------------------------

    @Test
    fun scalar_string() {
        assertEquals(JsonXString("hello"), parseDocument("\"hello\""))
    }

    @Test
    fun scalar_integer() {
        assertEquals(JsonXNumber.of(42L), parseDocument("42"))
    }

    @Test
    fun scalar_decimal() {
        assertEquals(JsonXNumber.of(3.14), parseDocument("3.14"))
    }

    @Test
    fun scalar_true() {
        assertEquals(JsonXBoolean(true), parseDocument("true"))
    }

    @Test
    fun scalar_false() {
        assertEquals(JsonXBoolean(false), parseDocument("false"))
    }

    @Test
    fun scalar_null() {
        assertEquals(JsonXNull, parseDocument("null"))
    }

    // --- objects -------------------------------------------------------------------------------

    @Test
    fun object_empty() {
        assertEquals(JsonXObject(LinkedHashMap()), parseDocument("{}"))
    }

    @Test
    fun object_singleEntry() {
        val expected = JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L)))
        assertEquals(expected, parseDocument("""{"a": 1}"""))
    }

    @Test
    fun object_multipleEntries_preservesInsertionOrder() {
        val result = parseDocument("""{"b": 1, "a": 2}""") as JsonXObject
        assertEquals(listOf("b", "a"), result.entries.keys.toList())
    }

    @Test
    fun object_nested() {
        val expected =
            JsonXObject(
                linkedMapOf(
                    "outer" to JsonXObject(linkedMapOf("inner" to JsonXBoolean(true))),
                ),
            )
        assertEquals(expected, parseDocument("""{"outer": {"inner": true}}"""))
    }

    // --- arrays --------------------------------------------------------------------------------

    @Test
    fun array_empty() {
        assertEquals(JsonXArray(emptyList()), parseDocument("[]"))
    }

    @Test
    fun array_mixedTypes() {
        val expected =
            JsonXArray(
                listOf(JsonXString("x"), JsonXNumber.of(1L), JsonXBoolean(true), JsonXNull),
            )
        assertEquals(expected, parseDocument("""["x", 1, true, null]"""))
    }

    @Test
    fun array_nested() {
        val expected = JsonXArray(listOf(JsonXArray(listOf(JsonXNumber.of(1L), JsonXNumber.of(2L)))))
        assertEquals(expected, parseDocument("[[1, 2]]"))
    }

    // --- string decoding -------------------------------------------------------------------------

    @Test
    fun string_standardEscapesDecoded() {
        val result = parseDocument(""""\"\\\/\b\f\n\r\t"""") as JsonXString
        assertEquals("\"\\/\b\u000C\n\r\t", result.value)
    }

    @Test
    fun string_unicodeEscapeDecoded() {
        val result = parseDocument("\"\\u0041\\u00e9\"") as JsonXString
        assertEquals("Aé", result.value)
    }

    @Test
    fun string_surrogatePairDecoded() {
        val result = parseDocument("\"\\uD83D\\uDE00\"") as JsonXString
        assertEquals("😀", result.value)
    }

    @Test
    fun string_key_decodedInObject() {
        val result = parseDocument("""{"a\nb": 1}""") as JsonXObject
        assertEquals(listOf("a\nb"), result.entries.keys.toList())
    }

    // --- number decoding -------------------------------------------------------------------------

    @Test
    fun number_negativeInteger() {
        assertEquals(JsonXNumber.of(-17L), parseDocument("-17"))
    }

    @Test
    fun number_exponent() {
        assertEquals(JsonXNumber.of(1000.0), parseDocument("1e3"))
    }

    @Test
    fun number_overflowingLong_fallsBackToDouble() {
        val result = parseDocument("99999999999999999999999999") as JsonXNumber
        assertEquals(null, result.long)
        assertEquals(1.0E26, result.double)
    }

    // --- duplicate key policy ------------------------------------------------------------------

    @Test
    fun duplicateKey_defaultPolicyIsError() {
        assertFailsWith<JsonXParseException> { parseDocument("""{"a": 1, "a": 2}""") }
    }

    @Test
    fun duplicateKey_lastWins() {
        val options = ParseOptions(duplicateKeyPolicy = ParseOptions.DuplicateKeyPolicy.LastWins)
        val result = parseDocument("""{"a": 1, "a": 2}""", options) as JsonXObject
        assertEquals(JsonXNumber.of(2L), result.entries["a"])
        assertEquals(1, result.entries.size)
    }

    @Test
    fun duplicateKey_firstWins() {
        val options = ParseOptions(duplicateKeyPolicy = ParseOptions.DuplicateKeyPolicy.FirstWins)
        val result = parseDocument("""{"a": 1, "a": 2}""", options) as JsonXObject
        assertEquals(JsonXNumber.of(1L), result.entries["a"])
        assertEquals(1, result.entries.size)
    }

    @Test
    fun duplicateKey_collectIntoArray() {
        val options = ParseOptions(duplicateKeyPolicy = ParseOptions.DuplicateKeyPolicy.CollectIntoArray)
        val result = parseDocument("""{"a": 1, "a": 2, "a": 3}""", options) as JsonXObject
        assertEquals(JsonXArray(listOf(JsonXNumber.of(1L), JsonXNumber.of(2L), JsonXNumber.of(3L))), result.entries["a"])
    }

    // --- error cases -----------------------------------------------------------------------------

    @Test
    fun error_trailingCommaInObject_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("""{"a": 1,}""") }
    }

    @Test
    fun error_trailingCommaInArray_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("[1,]") }
    }

    @Test
    fun error_missingColon_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("""{"a" 1}""") }
    }

    @Test
    fun error_missingCommaBetweenEntries_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("""{"a": 1 "b": 2}""") }
    }

    @Test
    fun error_nonStringKey_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("""{1: 2}""") }
    }

    @Test
    fun error_unterminatedObject_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("""{"a": 1""") }
    }

    @Test
    fun error_unterminatedArray_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("[1, 2") }
    }

    @Test
    fun error_trailingContentAfterValue_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("1 2") }
    }

    @Test
    fun error_emptyValuePosition_fails() {
        assertFailsWith<JsonXParseException> { parseDocument(",") }
    }

    @Test
    fun error_nestingBeyondMaxDepth_failsCleanly_insteadOfStackOverflow() {
        val deeplyNested = "[".repeat(JsonParser.MAX_NESTING_DEPTH + 1) + "]".repeat(JsonParser.MAX_NESTING_DEPTH + 1)
        assertFailsWith<JsonXParseException> { parseDocument(deeplyNested) }
    }

    @Test
    fun nestingAtMaxDepth_parsesSuccessfully() {
        val atLimit = "[".repeat(JsonParser.MAX_NESTING_DEPTH) + "]".repeat(JsonParser.MAX_NESTING_DEPTH)
        parseDocument(atLimit) // must not throw
    }

    // --- public boundary (JsonParser.parse) ---------------------------------------------------

    @Test
    fun parse_validInput_returnsSuccess() {
        val result = JsonParser.parse("""{"a": 1}""")
        assertTrue(result is Success)
        assertEquals(JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L))), result.value)
    }

    @Test
    fun parse_invalidInput_returnsFailure() {
        val result = JsonParser.parse("{invalid}")
        assertTrue(result is Failure)
    }

    @Test
    fun parse_failure_carriesSourcePosition() {
        val result = JsonParser.parse("""{"a": 1, "a": 2}""") as Failure
        assertEquals(1, result.error.line)
    }
}
