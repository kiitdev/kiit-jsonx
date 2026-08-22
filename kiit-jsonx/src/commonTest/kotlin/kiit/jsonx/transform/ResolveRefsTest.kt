package kiit.jsonx.transform

import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.result.Failure
import kiit.result.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =================================================================================================
// ResolveRefsTest: @ref path lookup, chained/nested refs, and cycle detection
// =================================================================================================
class ResolveRefsTest {
    @Test
    fun resolve_directReferenceToScalar_succeeds() {
        val tree =
            JsonXObject(
                linkedMapOf(
                    "host" to JsonXString("prod-db.internal"),
                    "alias" to JsonXTagged("ref", listOf(JsonXString("host"))),
                ),
            )
        val expected =
            JsonXObject(
                linkedMapOf(
                    "host" to JsonXString("prod-db.internal"),
                    "alias" to JsonXString("prod-db.internal"),
                ),
            )
        assertEquals(Success(expected), resolveRefs(tree))
    }

    @Test
    fun resolve_referenceToNestedPath_succeeds() {
        val tree =
            JsonXObject(
                linkedMapOf(
                    "database" to JsonXObject(linkedMapOf("host" to JsonXString("prod-db.internal"))),
                    "alias" to JsonXTagged("ref", listOf(JsonXString("database.host"))),
                ),
            )
        val result = resolveRefs(tree) as Success
        assertEquals(JsonXString("prod-db.internal"), (result.value as JsonXObject).entries["alias"])
    }

    @Test
    fun resolve_referenceToArrayIndex_succeeds() {
        val tree =
            JsonXObject(
                linkedMapOf(
                    "hosts" to JsonXArray(listOf(JsonXString("a"), JsonXString("b"))),
                    "primary" to JsonXTagged("ref", listOf(JsonXString("hosts[0]"))),
                ),
            )
        val result = resolveRefs(tree) as Success
        assertEquals(JsonXString("a"), (result.value as JsonXObject).entries["primary"])
    }

    @Test
    fun resolve_chainedRefs_resolvesTransitively() {
        val tree =
            JsonXObject(
                linkedMapOf(
                    "a" to JsonXString("value"),
                    "b" to JsonXTagged("ref", listOf(JsonXString("a"))),
                    "c" to JsonXTagged("ref", listOf(JsonXString("b"))),
                ),
            )
        val result = resolveRefs(tree) as Success
        val obj = result.value as JsonXObject
        assertEquals(JsonXString("value"), obj.entries["b"])
        assertEquals(JsonXString("value"), obj.entries["c"])
    }

    @Test
    fun resolve_referenceToCompoundTargetWithNestedRef_fullyResolvesTheCopy() {
        // "b" references "a", and "a" itself contains an unresolved ref ("a.y" -> "z") — the
        // value spliced in at "b" must be fully resolved, not a partial copy of "a".
        val tree =
            JsonXObject(
                linkedMapOf(
                    "a" to
                        JsonXObject(
                            linkedMapOf(
                                "x" to JsonXNumber.of(1L),
                                "y" to JsonXTagged("ref", listOf(JsonXString("z"))),
                            ),
                        ),
                    "z" to JsonXString("resolved-z"),
                    "b" to JsonXTagged("ref", listOf(JsonXString("a"))),
                ),
            )
        val expectedA = JsonXObject(linkedMapOf("x" to JsonXNumber.of(1L), "y" to JsonXString("resolved-z")))
        val result = resolveRefs(tree) as Success
        val obj = result.value as JsonXObject
        assertEquals(expectedA, obj.entries["a"])
        assertEquals(expectedA, obj.entries["b"])
    }

    @Test
    fun resolve_multipleReferencesToSameTarget_bothResolveCorrectly() {
        val tree =
            JsonXObject(
                linkedMapOf(
                    "a" to JsonXString("value"),
                    "b" to JsonXTagged("ref", listOf(JsonXString("a"))),
                    "c" to JsonXTagged("ref", listOf(JsonXString("a"))),
                ),
            )
        val result = resolveRefs(tree) as Success
        val obj = result.value as JsonXObject
        assertEquals(JsonXString("value"), obj.entries["b"])
        assertEquals(JsonXString("value"), obj.entries["c"])
    }

    // --- error cases -----------------------------------------------------------------------------

    @Test
    fun resolve_targetNotFound_fails() {
        val tree = JsonXObject(linkedMapOf("a" to JsonXTagged("ref", listOf(JsonXString("nope")))))
        assertTrue(resolveRefs(tree) is Failure)
    }

    @Test
    fun resolve_directCycle_fails() {
        val tree =
            JsonXObject(
                linkedMapOf(
                    "a" to JsonXTagged("ref", listOf(JsonXString("b"))),
                    "b" to JsonXTagged("ref", listOf(JsonXString("a"))),
                ),
            )
        assertTrue(resolveRefs(tree) is Failure)
    }

    @Test
    fun resolve_selfCycle_fails() {
        val tree = JsonXObject(linkedMapOf("a" to JsonXTagged("ref", listOf(JsonXString("a")))))
        assertTrue(resolveRefs(tree) is Failure)
    }

    @Test
    fun resolve_malformedPathArgument_fails() {
        val tree = JsonXObject(linkedMapOf("a" to JsonXTagged("ref", listOf(JsonXNumber.of(1L)))))
        assertTrue(resolveRefs(tree) is Failure)
    }

    @Test
    fun resolve_unrelatedTag_isLeftUntouched() {
        val tree = JsonXTagged("env", listOf(JsonXString("X")))
        assertEquals(Success(tree), resolveRefs(tree))
    }
}
