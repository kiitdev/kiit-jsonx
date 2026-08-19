package kiit.jsonx.element

import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXBoolean
import kiit.jsonx.element.JsonXElement.JsonXNull
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

// =================================================================================================
// JsonXElementTest: equality/construction for every JsonXElement variant
// =================================================================================================
class JsonXElementTest {
    @Test
    fun jsonXObject_equalsByEntries() {
        val a = JsonXObject(linkedMapOf("host" to JsonXString("localhost"), "port" to JsonXNumber.of(80L)))
        val b = JsonXObject(linkedMapOf("host" to JsonXString("localhost"), "port" to JsonXNumber.of(80L)))
        val c = JsonXObject(linkedMapOf("host" to JsonXString("example.com")))

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun jsonXObject_preservesInsertionOrder() {
        val obj = JsonXObject(linkedMapOf("b" to JsonXString("2"), "a" to JsonXString("1")))
        assertEquals(listOf("b", "a"), obj.entries.keys.toList())
    }

    @Test
    fun jsonXArray_equalsByOrderedItems() {
        val a = JsonXArray(listOf(JsonXString("x"), JsonXString("y")))
        val b = JsonXArray(listOf(JsonXString("x"), JsonXString("y")))
        val reordered = JsonXArray(listOf(JsonXString("y"), JsonXString("x")))

        assertEquals(a, b)
        assertNotEquals(a, reordered)
    }

    @Test
    fun jsonXString_equalsByValue() {
        assertEquals(JsonXString("hello"), JsonXString("hello"))
        assertNotEquals(JsonXString("hello"), JsonXString("world"))
    }

    @Test
    fun jsonXNumber_of_populatesExactlyOneBranch() {
        val long = JsonXNumber.of(42L)
        assertEquals(42L, long.long)
        assertEquals(null, long.double)

        val double = JsonXNumber.of(4.2)
        assertEquals(null, double.long)
        assertEquals(4.2, double.double)
    }

    @Test
    fun jsonXNumber_rejectsBothNull() {
        assertFailsWith<IllegalArgumentException> { JsonXNumber(null, null) }
    }

    @Test
    fun jsonXNumber_rejectsBothNonNull() {
        assertFailsWith<IllegalArgumentException> { JsonXNumber(1L, 1.0) }
    }

    @Test
    fun jsonXNumber_equalsByBranch() {
        assertEquals(JsonXNumber.of(42L), JsonXNumber.of(42L))
        assertNotEquals(JsonXNumber.of(42L), JsonXNumber.of(42.0))
    }

    @Test
    fun jsonXBoolean_equalsByValue() {
        assertEquals(JsonXBoolean(true), JsonXBoolean(true))
        assertNotEquals(JsonXBoolean(true), JsonXBoolean(false))
    }

    @Test
    fun jsonXNull_isASingleton() {
        assertSame(JsonXNull, JsonXNull)
        assertEquals(JsonXNull, JsonXNull)
    }

    @Test
    fun jsonXTagged_equalsByNameAndArgs() {
        val a = JsonXTagged("env", listOf(JsonXString("DB_HOST")))
        val b = JsonXTagged("env", listOf(JsonXString("DB_HOST")))
        val differentArgs = JsonXTagged("env", listOf(JsonXString("DB_PORT")))
        val differentName = JsonXTagged("ref", listOf(JsonXString("DB_HOST")))

        assertEquals(a, b)
        assertNotEquals(a, differentArgs)
        assertNotEquals(a, differentName)
    }
}
