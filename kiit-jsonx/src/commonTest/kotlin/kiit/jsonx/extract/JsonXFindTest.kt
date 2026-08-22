package kiit.jsonx.extract

import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXNull
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =================================================================================================
// JsonXFindTest: find/exists path resolution across objects, arrays, and nesting
// =================================================================================================
class JsonXFindTest {
    private val tree =
        JsonXObject(
            linkedMapOf(
                "host" to JsonXString("localhost"),
                "timeout" to JsonXNull,
                "database" to
                    JsonXObject(
                        linkedMapOf(
                            "host" to JsonXString("db.local"),
                            "port" to JsonXNumber.of(5432L),
                        ),
                    ),
                "servers" to
                    JsonXArray(
                        listOf(
                            JsonXObject(linkedMapOf("host" to JsonXString("s1"))),
                            JsonXObject(linkedMapOf("host" to JsonXString("s2"))),
                        ),
                    ),
                "grid" to JsonXArray(listOf(JsonXArray(listOf(JsonXNumber.of(1L), JsonXNumber.of(2L))))),
            ),
        )

    @Test
    fun emptyPath_returnsReceiverItself() {
        assertEquals(tree, tree.find(""))
    }

    @Test
    fun topLevelKey_found() {
        assertEquals(JsonXString("localhost"), tree.find("host"))
    }

    @Test
    fun nestedKey_found() {
        assertEquals(JsonXString("db.local"), tree.find("database.host"))
    }

    @Test
    fun missingTopLevelKey_returnsNull() {
        assertNull(tree.find("nope"))
    }

    @Test
    fun missingNestedKey_returnsNull() {
        assertNull(tree.find("database.nope"))
    }

    @Test
    fun keyingIntoAScalar_returnsNull() {
        assertNull(tree.find("host.nope"))
    }

    @Test
    fun arrayIndex_found() {
        val expected = JsonXObject(linkedMapOf("host" to JsonXString("s1")))
        assertEquals(expected, tree.find("servers[0]"))
    }

    @Test
    fun arrayIndexThenKey_found() {
        assertEquals(JsonXString("s2"), tree.find("servers[1].host"))
    }

    @Test
    fun arrayIndexOutOfBounds_returnsNull() {
        assertNull(tree.find("servers[5]"))
    }

    @Test
    fun largeOutOfBoundsIndex_returnsNull() {
        assertNull(tree.find("servers[99]"))
    }

    @Test
    fun nestedArrayIndices_found() {
        assertEquals(JsonXNumber.of(2L), tree.find("grid[0][1]"))
    }

    @Test
    fun indexingIntoAnObject_returnsNull() {
        assertNull(tree.find("database[0]"))
    }

    @Test
    fun explicitNullValue_isFoundAsJsonXNull() {
        assertEquals(JsonXNull, tree.find("timeout"))
    }

    // --- exists ----------------------------------------------------------------------------------

    @Test
    fun exists_trueForPresentValue() {
        assertTrue(tree.exists("host"))
    }

    @Test
    fun exists_trueForExplicitNull() {
        assertTrue(tree.exists("timeout"))
    }

    @Test
    fun exists_falseForMissingPath() {
        assertFalse(tree.exists("nope"))
    }
}
