package kiit.jsonx.tags.builtin

import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXBoolean
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.tags.ExperimentalJsonxTagApi
import kiit.jsonx.tags.TagKind
import kiit.result.Failure
import kiit.result.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =================================================================================================
// TableTagHandlerTest: @table resolution — named columns, positional fallback, mismatch errors
// =================================================================================================
@OptIn(ExperimentalJsonxTagApi::class)
class TableTagHandlerTest {
    private val handler = TableTagHandler()

    @Test
    fun name_isTable() {
        assertEquals("table", handler.name)
    }

    @Test
    fun kind_isSimple() {
        assertEquals(TagKind.Simple, handler.kind)
    }

    @Test
    fun resolve_withNames_zipsRowsIntoObjects() {
        val table =
            JsonXObject(
                linkedMapOf(
                    "names" to JsonXArray(listOf(JsonXString("name"), JsonXString("active"))),
                    "rows" to
                        JsonXArray(
                            listOf(
                                JsonXArray(listOf(JsonXString("Superman"), JsonXBoolean(true))),
                                JsonXArray(listOf(JsonXString("Batman"), JsonXBoolean(true))),
                                JsonXArray(listOf(JsonXString("Wonder Woman"), JsonXBoolean(false))),
                            ),
                        ),
                ),
            )

        val expected =
            JsonXArray(
                listOf(
                    JsonXObject(linkedMapOf("name" to JsonXString("Superman"), "active" to JsonXBoolean(true))),
                    JsonXObject(linkedMapOf("name" to JsonXString("Batman"), "active" to JsonXBoolean(true))),
                    JsonXObject(linkedMapOf("name" to JsonXString("Wonder Woman"), "active" to JsonXBoolean(false))),
                ),
            )

        val result = handler.resolve(listOf(table))
        assertTrue(result is Success)
        assertEquals(expected, result.value)
    }

    @Test
    fun resolve_withoutNames_usesPositionalColumnNames() {
        val table =
            JsonXObject(
                linkedMapOf(
                    "rows" to JsonXArray(listOf(JsonXArray(listOf(JsonXString("a"), JsonXNumber.of(1L))))),
                ),
            )

        val expected = JsonXArray(listOf(JsonXObject(linkedMapOf("col0" to JsonXString("a"), "col1" to JsonXNumber.of(1L)))))

        val result = handler.resolve(listOf(table))
        assertTrue(result is Success)
        assertEquals(expected, result.value)
    }

    @Test
    fun resolve_emptyRows_withNames_succeedsWithEmptyArray() {
        val table =
            JsonXObject(
                linkedMapOf(
                    "names" to JsonXArray(listOf(JsonXString("a"))),
                    "rows" to JsonXArray(emptyList()),
                ),
            )
        val result = handler.resolve(listOf(table))
        assertTrue(result is Success)
        assertEquals(JsonXArray(emptyList()), result.value)
    }

    @Test
    fun resolve_emptyRows_withoutNames_succeedsWithEmptyArray() {
        val table = JsonXObject(linkedMapOf("rows" to JsonXArray(emptyList())))
        val result = handler.resolve(listOf(table))
        assertTrue(result is Success)
        assertEquals(JsonXArray(emptyList()), result.value)
    }

    // --- error cases -----------------------------------------------------------------------------

    @Test
    fun resolve_noArguments_fails() {
        assertTrue(handler.resolve(emptyList()) is Failure)
    }

    @Test
    fun resolve_tooManyArguments_fails() {
        val table = JsonXObject(linkedMapOf("rows" to JsonXArray(emptyList())))
        assertTrue(handler.resolve(listOf(table, table)) is Failure)
    }

    @Test
    fun resolve_argumentNotAnObject_fails() {
        assertTrue(handler.resolve(listOf(JsonXString("nope"))) is Failure)
    }

    @Test
    fun resolve_missingRowsKey_fails() {
        val table = JsonXObject(linkedMapOf("names" to JsonXArray(listOf(JsonXString("a")))))
        assertTrue(handler.resolve(listOf(table)) is Failure)
    }

    @Test
    fun resolve_rowsNotAnArray_fails() {
        val table = JsonXObject(linkedMapOf("rows" to JsonXString("nope")))
        assertTrue(handler.resolve(listOf(table)) is Failure)
    }

    @Test
    fun resolve_namesNotAnArrayOfStrings_fails() {
        val table =
            JsonXObject(
                linkedMapOf(
                    "names" to JsonXArray(listOf(JsonXNumber.of(1L))),
                    "rows" to JsonXArray(emptyList()),
                ),
            )
        assertTrue(handler.resolve(listOf(table)) is Failure)
    }

    @Test
    fun resolve_rowNotAnArray_fails() {
        val table = JsonXObject(linkedMapOf("rows" to JsonXArray(listOf(JsonXString("not a row")))))
        assertTrue(handler.resolve(listOf(table)) is Failure)
    }

    @Test
    fun resolve_rowWidthMismatchAgainstNames_fails() {
        val table =
            JsonXObject(
                linkedMapOf(
                    "names" to JsonXArray(listOf(JsonXString("a"), JsonXString("b"))),
                    "rows" to JsonXArray(listOf(JsonXArray(listOf(JsonXString("onlyOne"))))),
                ),
            )
        assertTrue(handler.resolve(listOf(table)) is Failure)
    }

    @Test
    fun resolve_rowWidthMismatchAgainstOtherRows_fails() {
        val table =
            JsonXObject(
                linkedMapOf(
                    "rows" to
                        JsonXArray(
                            listOf(
                                JsonXArray(listOf(JsonXString("a"), JsonXString("b"))),
                                JsonXArray(listOf(JsonXString("onlyOne"))),
                            ),
                        ),
                ),
            )
        assertTrue(handler.resolve(listOf(table)) is Failure)
    }

    @Test
    fun resolve_rowWidthMismatch_neverTruncatesOrPads() {
        // a mismatched row must fail outright, not silently produce a short/padded object
        val table =
            JsonXObject(
                linkedMapOf(
                    "names" to JsonXArray(listOf(JsonXString("a"), JsonXString("b"), JsonXString("c"))),
                    "rows" to JsonXArray(listOf(JsonXArray(listOf(JsonXString("x"), JsonXString("y"))))),
                ),
            )
        val result = handler.resolve(listOf(table))
        assertTrue(result is Failure)
    }
}
