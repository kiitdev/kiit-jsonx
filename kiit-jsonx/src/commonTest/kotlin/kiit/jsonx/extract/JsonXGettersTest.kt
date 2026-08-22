package kiit.jsonx.extract

import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXBoolean
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.error.JsonXException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// =================================================================================================
// JsonXGettersTest: typed scalar/collection getters, all three suffixes, all three failure modes
// (missing, wrong type, unresolved tag)
// =================================================================================================
class JsonXGettersTest {
    private val tree =
        JsonXObject(
            linkedMapOf(
                "host" to JsonXString("localhost"),
                "port" to JsonXNumber.of(8080L),
                "ratio" to JsonXNumber.of(0.5),
                "wholeDouble" to JsonXNumber.of(8080.0),
                "ssl" to JsonXBoolean(true),
                "database" to JsonXObject(linkedMapOf("host" to JsonXString("db.local"))),
                "servers" to JsonXArray(listOf(JsonXString("s1"))),
                "secret" to JsonXTagged("env", listOf(JsonXString("SECRET"))),
                "big" to JsonXNumber.of(Long.MAX_VALUE),
            ),
        )

    // --- getString ---------------------------------------------------------------------------

    @Test
    fun getString_success() {
        assertEquals("localhost", tree.getString("host"))
    }

    @Test
    fun getString_missing_throws() {
        assertFailsWith<JsonXException> { tree.getString("nope") }
    }

    @Test
    fun getString_wrongType_throws() {
        assertFailsWith<JsonXException> { tree.getString("port") }
    }

    @Test
    fun getString_unresolvedTag_throws() {
        assertFailsWith<JsonXException> { tree.getString("secret") }
    }

    @Test
    fun getStringOrElse_missing_returnsDefault() {
        assertEquals("fallback", tree.getStringOrElse("nope", "fallback"))
    }

    @Test
    fun getStringOrElse_wrongType_returnsDefault() {
        assertEquals("fallback", tree.getStringOrElse("port", "fallback"))
    }

    @Test
    fun getStringOrElse_unresolvedTag_returnsDefault() {
        assertEquals("fallback", tree.getStringOrElse("secret", "fallback"))
    }

    @Test
    fun getStringOrNull_success() {
        assertEquals("localhost", tree.getStringOrNull("host"))
    }

    @Test
    fun getStringOrNull_missing_returnsNull() {
        assertNull(tree.getStringOrNull("nope"))
    }

    @Test
    fun getStringOrNull_unresolvedTag_returnsNull() {
        assertNull(tree.getStringOrNull("secret"))
    }

    // --- getBoolean ----------------------------------------------------------------------------

    @Test
    fun getBoolean_success() {
        assertEquals(true, tree.getBoolean("ssl"))
    }

    @Test
    fun getBoolean_wrongType_throws() {
        assertFailsWith<JsonXException> { tree.getBoolean("host") }
    }

    @Test
    fun getBooleanOrElse_missing_returnsDefault() {
        assertEquals(false, tree.getBooleanOrElse("nope", false))
    }

    @Test
    fun getBooleanOrNull_wrongType_returnsNull() {
        assertNull(tree.getBooleanOrNull("host"))
    }

    // --- getObject / getArray ------------------------------------------------------------------

    @Test
    fun getObject_success() {
        assertEquals(JsonXObject(linkedMapOf("host" to JsonXString("db.local"))), tree.getObject("database"))
    }

    @Test
    fun getObject_wrongType_throws() {
        assertFailsWith<JsonXException> { tree.getObject("host") }
    }

    @Test
    fun getArray_success() {
        assertEquals(JsonXArray(listOf(JsonXString("s1"))), tree.getArray("servers"))
    }

    @Test
    fun getArray_missing_throws() {
        assertFailsWith<JsonXException> { tree.getArray("nope") }
    }

    @Test
    fun getObjectOrNull_missing_returnsNull() {
        assertNull(tree.getObjectOrNull("nope"))
    }

    @Test
    fun getArrayOrElse_wrongType_returnsDefault() {
        val default = JsonXArray(emptyList())
        assertEquals(default, tree.getArrayOrElse("host", default))
    }

    // --- getLong / getInt / getDouble -------------------------------------------------------------

    @Test
    fun getLong_fromLongBackedNumber() {
        assertEquals(8080L, tree.getLong("port"))
    }

    @Test
    fun getLong_fromExactDoubleBackedNumber() {
        assertEquals(8080L, tree.getLong("wholeDouble"))
    }

    @Test
    fun getLong_fromNonIntegralDouble_throws() {
        assertFailsWith<JsonXException> { tree.getLong("ratio") }
    }

    @Test
    fun getLong_missing_throws() {
        assertFailsWith<JsonXException> { tree.getLong("nope") }
    }

    @Test
    fun getLong_unresolvedTag_throws() {
        assertFailsWith<JsonXException> { tree.getLong("secret") }
    }

    @Test
    fun getLongOrNull_fromNonIntegralDouble_returnsNull() {
        assertNull(tree.getLongOrNull("ratio"))
    }

    @Test
    fun getLongOrElse_missing_returnsDefault() {
        assertEquals(42L, tree.getLongOrElse("nope", 42L))
    }

    @Test
    fun getInt_success() {
        assertEquals(8080, tree.getInt("port"))
    }

    @Test
    fun getInt_overflowingLong_throws() {
        assertFailsWith<JsonXException> { tree.getInt("big") }
    }

    @Test
    fun getIntOrNull_overflowingLong_returnsNull() {
        assertNull(tree.getIntOrNull("big"))
    }

    @Test
    fun getIntOrElse_wrongType_returnsDefault() {
        assertEquals(-1, tree.getIntOrElse("host", -1))
    }

    @Test
    fun getDouble_fromLongBackedNumber_widens() {
        assertEquals(8080.0, tree.getDouble("port"))
    }

    @Test
    fun getDouble_fromDoubleBackedNumber() {
        assertEquals(0.5, tree.getDouble("ratio"))
    }

    @Test
    fun getDouble_missing_throws() {
        assertFailsWith<JsonXException> { tree.getDouble("nope") }
    }

    @Test
    fun getDoubleOrNull_fromLongBackedNumber_widens() {
        assertEquals(8080.0, tree.getDoubleOrNull("port"))
    }

    @Test
    fun getDoubleOrElse_missing_returnsDefault() {
        assertEquals(1.5, tree.getDoubleOrElse("nope", 1.5))
    }
}
