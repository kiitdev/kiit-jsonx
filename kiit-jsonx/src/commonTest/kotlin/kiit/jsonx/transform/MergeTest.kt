package kiit.jsonx.transform

import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kotlin.test.Test
import kotlin.test.assertEquals

// =================================================================================================
// MergeTest: recursive object merge, override-wins for everything else
// =================================================================================================
class MergeTest {
    @Test
    fun merge_disjointKeys_unionsThem() {
        val base = JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L)))
        val override = JsonXObject(linkedMapOf("b" to JsonXNumber.of(2L)))
        val expected = JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L), "b" to JsonXNumber.of(2L)))
        assertEquals(expected, merge(base, override))
    }

    @Test
    fun merge_overlappingScalarKey_overrideWins() {
        val base = JsonXObject(linkedMapOf("port" to JsonXNumber.of(8080L)))
        val override = JsonXObject(linkedMapOf("port" to JsonXNumber.of(9090L)))
        assertEquals(JsonXObject(linkedMapOf("port" to JsonXNumber.of(9090L))), merge(base, override))
    }

    @Test
    fun merge_nestedObjects_mergeRecursively() {
        val base =
            JsonXObject(
                linkedMapOf(
                    "database" to JsonXObject(linkedMapOf("host" to JsonXString("localhost"), "port" to JsonXNumber.of(5432L))),
                ),
            )
        val override =
            JsonXObject(
                linkedMapOf("database" to JsonXObject(linkedMapOf("host" to JsonXString("prod-db.internal")))),
            )
        val expected =
            JsonXObject(
                linkedMapOf(
                    "database" to JsonXObject(linkedMapOf("host" to JsonXString("prod-db.internal"), "port" to JsonXNumber.of(5432L))),
                ),
            )
        assertEquals(expected, merge(base, override))
    }

    @Test
    fun merge_arrayInOverride_replacesWholesaleRatherThanConcatenating() {
        val base = JsonXObject(linkedMapOf("hosts" to JsonXArray(listOf(JsonXString("a"), JsonXString("b")))))
        val override = JsonXObject(linkedMapOf("hosts" to JsonXArray(listOf(JsonXString("c")))))
        assertEquals(JsonXObject(linkedMapOf("hosts" to JsonXArray(listOf(JsonXString("c"))))), merge(base, override))
    }

    @Test
    fun merge_typeMismatchAtSameKey_overrideWins() {
        val base = JsonXObject(linkedMapOf("value" to JsonXObject(linkedMapOf("nested" to JsonXNumber.of(1L)))))
        val override = JsonXObject(linkedMapOf("value" to JsonXString("now a string")))
        assertEquals(JsonXObject(linkedMapOf("value" to JsonXString("now a string"))), merge(base, override))
    }

    @Test
    fun merge_baseIsNotAnObject_overrideWinsOutright() {
        assertEquals(JsonXString("override"), merge(JsonXString("base"), JsonXString("override")))
    }

    @Test
    fun merge_baseUnmutated() {
        val base = JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L)))
        val override = JsonXObject(linkedMapOf("b" to JsonXNumber.of(2L)))
        merge(base, override)
        assertEquals(JsonXObject(linkedMapOf("a" to JsonXNumber.of(1L))), base)
    }
}
