package kiit.jsonx.extract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// =================================================================================================
// JsonXPathTest: dotted-path-with-bracket-index parsing, including malformed-path rejection
// =================================================================================================
class JsonXPathTest {
    @Test
    fun emptyPath_yieldsNoSegments() {
        assertEquals(emptyList(), parseJsonXPath(""))
    }

    @Test
    fun singleKey() {
        assertEquals(listOf(JsonXPathSegment("host", emptyList())), parseJsonXPath("host"))
    }

    @Test
    fun dottedKeys() {
        val expected =
            listOf(
                JsonXPathSegment("database", emptyList()),
                JsonXPathSegment("host", emptyList()),
            )
        assertEquals(expected, parseJsonXPath("database.host"))
    }

    @Test
    fun keyWithSingleIndex() {
        assertEquals(listOf(JsonXPathSegment("servers", listOf(0))), parseJsonXPath("servers[0]"))
    }

    @Test
    fun keyWithMultipleIndices() {
        assertEquals(listOf(JsonXPathSegment("grid", listOf(1, 2))), parseJsonXPath("grid[1][2]"))
    }

    @Test
    fun indexThenMoreKeys() {
        val expected =
            listOf(
                JsonXPathSegment("servers", listOf(0)),
                JsonXPathSegment("host", emptyList()),
            )
        assertEquals(expected, parseJsonXPath("servers[0].host"))
    }

    @Test
    fun bareLeadingIndex() {
        assertEquals(listOf(JsonXPathSegment("", listOf(0))), parseJsonXPath("[0]"))
    }

    @Test
    fun bareLeadingIndexThenKey() {
        val expected = listOf(JsonXPathSegment("", listOf(0)), JsonXPathSegment("host", emptyList()))
        assertEquals(expected, parseJsonXPath("[0].host"))
    }

    @Test
    fun multiDigitIndex() {
        assertEquals(listOf(JsonXPathSegment("items", listOf(123))), parseJsonXPath("items[123]"))
    }

    // --- malformed paths -------------------------------------------------------------------------

    @Test
    fun unterminatedBracket_fails() {
        assertFailsWith<IllegalArgumentException> { parseJsonXPath("a[0") }
    }

    @Test
    fun nonDigitIndex_fails() {
        assertFailsWith<IllegalArgumentException> { parseJsonXPath("a[x]") }
    }

    @Test
    fun emptyBrackets_fails() {
        assertFailsWith<IllegalArgumentException> { parseJsonXPath("a[]") }
    }

    @Test
    fun emptyMiddleSegment_fails() {
        assertFailsWith<IllegalArgumentException> { parseJsonXPath("a..b") }
    }

    @Test
    fun trailingDot_fails() {
        assertFailsWith<IllegalArgumentException> { parseJsonXPath("a.") }
    }

    @Test
    fun leadingDot_fails() {
        assertFailsWith<IllegalArgumentException> { parseJsonXPath(".a") }
    }
}
