package kiit.jsonx.parser.jsonx

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.options.ParseOptions
import kiit.result.Failure
import kiit.result.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// =================================================================================================
// JsonXParserTest: hand-written fixture corpus for jsonx-specific grammar (Milestone 2.5, task 5)
// — @tag(args) tree construction, triple-quoted strings end-to-end, and inherited JSON5 behavior.
// =================================================================================================
class JsonXParserTest {
    private fun parseDocument(text: String, options: ParseOptions = ParseOptions()): JsonXElement =
        JsonXParser(JsonXLexer(text), options).parseDocument()

    // --- inherited JSON5 behavior still works --------------------------------------------------

    @Test
    fun inheritsJson5TrailingCommasAndUnquotedKeys() {
        val expected = JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L)))
        assertEquals(expected, parseDocument("{a: 1,}"))
    }

    @Test
    fun emptyInput_yieldsEmptyObject() {
        assertEquals(JsonXObject(LinkedHashMap()), parseDocument(""))
    }

    // --- tags: no-arg, single-arg, multi-arg, namespaced ------------------------------------------

    @Test
    fun tag_noArgs() {
        assertEquals(JsonXTagged("now", emptyList()), parseDocument("@now()"))
    }

    @Test
    fun tag_singleStringArg() {
        val expected = JsonXTagged("env", listOf(JsonXString("DB_HOST")))
        assertEquals(expected, parseDocument("@env('DB_HOST')"))
    }

    @Test
    fun tag_singleNumberArg() {
        assertEquals(JsonXTagged("seconds", listOf(JsonXNumber.of(30L))), parseDocument("@seconds(30)"))
    }

    @Test
    fun tag_namespacedName() {
        val expected = JsonXTagged("acmecorp.date", listOf(JsonXString("2026-08-03")))
        assertEquals(expected, parseDocument("@acmecorp.date('2026-08-03')"))
    }

    @Test
    fun tag_multipleArgs() {
        val expected = JsonXTagged("range", listOf(JsonXNumber.of(1L), JsonXNumber.of(10L)))
        assertEquals(expected, parseDocument("@range(1, 10)"))
    }

    @Test
    fun tag_trailingCommaInArgs_isAllowed() {
        val expected = JsonXTagged("range", listOf(JsonXNumber.of(1L), JsonXNumber.of(10L)))
        assertEquals(expected, parseDocument("@range(1, 10,)"))
    }

    @Test
    fun tag_asObjectValue() {
        val result = parseDocument("{host: @env('DB_HOST')}") as JsonXObject
        assertEquals(JsonXTagged("env", listOf(JsonXString("DB_HOST"))), result.entries["host"])
    }

    @Test
    fun tag_asArrayItem() {
        val result = parseDocument("[@env('A'), 1]") as JsonXArray
        assertEquals(JsonXTagged("env", listOf(JsonXString("A"))), result.items[0])
    }

    @Test
    fun tag_nestedInsideAnotherTagsArgs() {
        // Parse-syntax level only: whether this is semantically valid is a Phase 3 TagKind
        // concern, not something this milestone's grammar rejects.
        val expected = JsonXTagged("outer", listOf(JsonXTagged("inner", emptyList())))
        assertEquals(expected, parseDocument("@outer(@inner())"))
    }

    @Test
    fun tag_argsCanBeObjectsAndArrays() {
        val result = parseDocument("@table({a: 1}, [1, 2])") as JsonXTagged
        assertEquals("table", result.name)
        assertEquals(JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L))), result.args[0])
        assertEquals(JsonXArray(listOf(JsonXNumber.of(1L), JsonXNumber.of(2L))), result.args[1])
    }

    // --- triple-quoted strings end-to-end -------------------------------------------------------

    @Test
    fun tripleQuotedString_decodesToPlainValue() {
        assertEquals(JsonXString("hello"), parseDocument("\"\"\"hello\"\"\""))
    }

    @Test
    fun tripleQuotedString_multilineValue() {
        val result = parseDocument("\"\"\"line one\nline two\"\"\"") as JsonXString
        assertEquals("line one\nline two", result.value)
    }

    @Test
    fun tripleQuotedString_asObjectValue() {
        val result = parseDocument("{bio: \"\"\"Hi, I'm here.\"\"\"}") as JsonXObject
        assertEquals(JsonXString("Hi, I'm here."), result.entries["bio"])
    }

    // --- # comments (dialect-only), end-to-end ---------------------------------------------------

    @Test
    fun hashComment_ignoredInFullDocument() {
        val source =
            """
            {
              # host to connect to
              host: 'localhost',
            }
            """.trimIndent()
        val result = parseDocument(source) as JsonXObject
        assertEquals(JsonXString("localhost"), result.entries["host"])
    }

    // --- error cases -----------------------------------------------------------------------------

    @Test
    fun error_tagMissingParens_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("@env") }
    }

    @Test
    fun error_tagMissingClosingParen_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("@env('A'") }
    }

    @Test
    fun error_tagDoubleTrailingComma_fails() {
        assertFailsWith<JsonXParseException> { parseDocument("@range(1,,)") }
    }

    // --- public boundary (JsonXParser.parse) ---------------------------------------------------

    @Test
    fun parse_validInput_returnsSuccess() {
        val result = JsonXParser.parse("@env('X')")
        assertTrue(result is Success)
        assertEquals(JsonXTagged("env", listOf(JsonXString("X"))), result.value)
    }

    @Test
    fun parse_invalidInput_returnsFailure() {
        assertTrue(JsonXParser.parse("@env(") is Failure)
    }
}
