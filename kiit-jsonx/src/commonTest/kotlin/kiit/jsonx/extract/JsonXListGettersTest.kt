package kiit.jsonx.extract

import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXBoolean
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.error.JsonXException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// =================================================================================================
// JsonXListGettersTest: list-of-scalar getters, all-or-nothing on a mixed-type array
// =================================================================================================
class JsonXListGettersTest {
    private val tree =
        JsonXObject(
            linkedMapOf(
                "tags" to JsonXArray(listOf(JsonXString("dev"), JsonXString("local"))),
                "flags" to JsonXArray(listOf(JsonXBoolean(true), JsonXBoolean(false))),
                "ports" to JsonXArray(listOf(JsonXNumber.of(80L), JsonXNumber.of(443L))),
                "ratios" to JsonXArray(listOf(JsonXNumber.of(0.5), JsonXNumber.of(1.5))),
                "servers" to
                    JsonXArray(
                        listOf(
                            JsonXObject(linkedMapOf("host" to JsonXString("s1"))),
                            JsonXObject(linkedMapOf("host" to JsonXString("s2"))),
                        ),
                    ),
                "mixed" to JsonXArray(listOf(JsonXString("a"), JsonXNumber.of(1L))),
                "wholeDoubles" to JsonXArray(listOf(JsonXNumber.of(1.0), JsonXNumber.of(2.0))),
            ),
        )

    // --- String list -----------------------------------------------------------------------------

    @Test
    fun getStringList_success() {
        assertEquals(listOf("dev", "local"), tree.getStringList("tags"))
    }

    @Test
    fun getStringList_missing_throws() {
        assertFailsWith<JsonXException> { tree.getStringList("nope") }
    }

    @Test
    fun getStringList_mixedTypeArray_throws() {
        assertFailsWith<JsonXException> { tree.getStringList("mixed") }
    }

    @Test
    fun getStringListOrNull_mixedTypeArray_returnsNull() {
        assertNull(tree.getStringListOrNull("mixed"))
    }

    @Test
    fun getStringListOrElse_missing_returnsDefault() {
        assertEquals(listOf("x"), tree.getStringListOrElse("nope", listOf("x")))
    }

    // --- Boolean list ----------------------------------------------------------------------------

    @Test
    fun getBooleanList_success() {
        assertEquals(listOf(true, false), tree.getBooleanList("flags"))
    }

    @Test
    fun getBooleanListOrNull_wrongElementType_returnsNull() {
        assertNull(tree.getBooleanListOrNull("tags"))
    }

    // --- Object list -----------------------------------------------------------------------------

    @Test
    fun getObjectList_success() {
        val expected =
            listOf(
                JsonXObject(linkedMapOf("host" to JsonXString("s1"))),
                JsonXObject(linkedMapOf("host" to JsonXString("s2"))),
            )
        assertEquals(expected, tree.getObjectList("servers"))
    }

    @Test
    fun getObjectListOrElse_wrongElementType_returnsDefault() {
        assertEquals(emptyList(), tree.getObjectListOrElse("tags", emptyList()))
    }

    // --- Number lists ----------------------------------------------------------------------------

    @Test
    fun getLongList_success() {
        assertEquals(listOf(80L, 443L), tree.getLongList("ports"))
    }

    @Test
    fun getLongList_exactWholeDoubles_succeed() {
        assertEquals(listOf(1L, 2L), tree.getLongList("wholeDoubles"))
    }

    @Test
    fun getLongList_nonIntegralDoubles_throws() {
        assertFailsWith<JsonXException> { tree.getLongList("ratios") }
    }

    @Test
    fun getLongListOrNull_nonIntegralDoubles_returnsNull() {
        assertNull(tree.getLongListOrNull("ratios"))
    }

    @Test
    fun getIntList_success() {
        assertEquals(listOf(80, 443), tree.getIntList("ports"))
    }

    @Test
    fun getIntListOrElse_wrongElementType_returnsDefault() {
        assertEquals(emptyList(), tree.getIntListOrElse("tags", emptyList()))
    }

    @Test
    fun getDoubleList_widensLongBackedNumbers() {
        assertEquals(listOf(80.0, 443.0), tree.getDoubleList("ports"))
    }

    @Test
    fun getDoubleList_fromDoubleBackedNumbers() {
        assertEquals(listOf(0.5, 1.5), tree.getDoubleList("ratios"))
    }

    @Test
    fun getDoubleListOrNull_missing_returnsNull() {
        assertNull(tree.getDoubleListOrNull("nope"))
    }
}
