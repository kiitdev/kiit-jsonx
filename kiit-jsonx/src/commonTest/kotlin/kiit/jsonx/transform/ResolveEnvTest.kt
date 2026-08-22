package kiit.jsonx.transform

import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.options.EnvAccessPolicy
import kiit.result.Failure
import kiit.result.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =================================================================================================
// ResolveEnvTest: @env resolution, EnvAccessPolicy gating, and the no-secrets-in-errors guarantee
// =================================================================================================
class ResolveEnvTest {
    private val fakeEnv = mapOf("DB_HOST" to "prod-db.internal")

    private fun lookup(name: String): String? = fakeEnv[name]

    @Test
    fun resolve_setVariable_allowAll_succeeds() {
        val tree = JsonXTagged("env", listOf(JsonXString("DB_HOST")))
        val result = resolveEnv(tree, EnvAccessPolicy.AllowAll, ::lookup)
        assertEquals(Success(JsonXString("prod-db.internal")), result)
    }

    @Test
    fun resolve_setVariable_allowlisted_succeeds() {
        val tree = JsonXTagged("env", listOf(JsonXString("DB_HOST")))
        val result = resolveEnv(tree, EnvAccessPolicy.Allowlist(setOf("DB_HOST")), ::lookup)
        assertEquals(Success(JsonXString("prod-db.internal")), result)
    }

    @Test
    fun resolve_unsetVariable_withDefault_fallsBack() {
        val tree = JsonXTagged("env", listOf(JsonXString("DB_PORT"), JsonXString("5432")))
        val result = resolveEnv(tree, EnvAccessPolicy.AllowAll, ::lookup)
        assertEquals(Success(JsonXString("5432")), result)
    }

    @Test
    fun resolve_unsetVariable_withoutDefault_fails() {
        val tree = JsonXTagged("env", listOf(JsonXString("DB_PORT")))
        val result = resolveEnv(tree, EnvAccessPolicy.AllowAll, ::lookup)
        assertTrue(result is Failure)
    }

    @Test
    fun resolve_defaultDenyPolicy_rejectsEvenASetVariable() {
        val tree = JsonXTagged("env", listOf(JsonXString("DB_HOST")))
        val result = resolveEnv(tree, EnvAccessPolicy.Deny, ::lookup)
        assertTrue(result is Failure)
    }

    @Test
    fun resolve_allowlist_excludesUnlistedVariable() {
        val tree = JsonXTagged("env", listOf(JsonXString("DB_HOST")))
        val result = resolveEnv(tree, EnvAccessPolicy.Allowlist(setOf("OTHER_VAR")), ::lookup)
        assertTrue(result is Failure)
    }

    @Test
    fun resolve_deniedAccess_errorNamesTheVariableButNeverALeakedValue() {
        val tree = JsonXTagged("env", listOf(JsonXString("SECRET_KEY")))
        val result = resolveEnv(tree, EnvAccessPolicy.Deny, ::lookup)
        assertTrue(result is Failure)
        assertTrue(result.error.err.message.contains("SECRET_KEY"))
    }

    @Test
    fun resolve_missingNameArgument_fails() {
        val tree = JsonXTagged("env", emptyList())
        assertTrue(resolveEnv(tree, EnvAccessPolicy.AllowAll, ::lookup) is Failure)
    }

    // --- nested inside ordinary structure -------------------------------------------------------

    @Test
    fun resolve_nestedInsideObjectAndArray_replacesInPlace() {
        val tree =
            JsonXObject(
                linkedMapOf(
                    "host" to JsonXTagged("env", listOf(JsonXString("DB_HOST"))),
                    "list" to JsonXArray(listOf(JsonXTagged("env", listOf(JsonXString("DB_HOST"))))),
                ),
            )
        val expected =
            JsonXObject(
                linkedMapOf(
                    "host" to JsonXString("prod-db.internal"),
                    "list" to JsonXArray(listOf(JsonXString("prod-db.internal"))),
                ),
            )
        assertEquals(Success(expected), resolveEnv(tree, EnvAccessPolicy.AllowAll, ::lookup))
    }

    @Test
    fun resolve_unrelatedTag_isLeftUntouched() {
        val tree = JsonXTagged("ref", listOf(JsonXString("a.b")))
        assertEquals(Success(tree), resolveEnv(tree, EnvAccessPolicy.AllowAll, ::lookup))
    }
}
