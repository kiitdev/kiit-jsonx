package kiit.jsonx.tags

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.tags.builtin.EnvTagHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =================================================================================================
// TagRegistryTest: built-in pre-seeding, namespace/prefix validation, mutation semantics
// =================================================================================================
@OptIn(ExperimentalJsonxTagApi::class)
class TagRegistryTest {
    private class FakeTag(override val name: String) : TagHandler {
        override val kind: TagKind = TagKind.Simple

        override fun resolve(args: List<JsonXElement>): JsonXResult<JsonXElement> = error("not needed for these tests")
    }

    @Test
    fun freshRegistry_alreadyContainsEnv() {
        val registry = TagRegistry()
        assertTrue(registry.contains(EnvTagHandler.NAME))
        assertTrue(registry.find(EnvTagHandler.NAME) is EnvTagHandler)
    }

    @Test
    fun unknownName_notContained() {
        val registry = TagRegistry()
        assertFalse(registry.contains("nope"))
        assertNull(registry.find("nope"))
    }

    @Test
    fun register_externalTagWithNamespace_succeeds() {
        val registry = TagRegistry()
        registry.register(FakeTag("acmecorp.date"))
        assertTrue(registry.contains("acmecorp.date"))
        assertEquals("acmecorp.date", registry.find("acmecorp.date")?.name)
    }

    @Test
    fun register_unprefixedName_fails() {
        val registry = TagRegistry()
        assertFailsWith<IllegalArgumentException> { registry.register(FakeTag("seconds")) }
    }

    @Test
    fun register_reservedKiitNamespace_fails() {
        val registry = TagRegistry()
        assertFailsWith<IllegalArgumentException> { registry.register(FakeTag("kiit.custom")) }
    }

    @Test
    fun register_reservedJsonxNamespace_fails() {
        val registry = TagRegistry()
        assertFailsWith<IllegalArgumentException> { registry.register(FakeTag("jsonx.custom")) }
    }

    @Test
    fun register_attemptingToReRegisterEnvWithoutNamespace_fails() {
        // "env" itself has no dot, so it's rejected by the prefix rule regardless of collision.
        val registry = TagRegistry()
        assertFailsWith<IllegalArgumentException> { registry.register(FakeTag("env")) }
    }

    @Test
    fun register_mutatesInPlace_matchingTheApplyStyleUsagePattern() {
        val registry =
            TagRegistry().apply {
                register(FakeTag("acmecorp.date"))
            }
        assertTrue(registry.contains("acmecorp.date"))
        assertTrue(registry.contains(EnvTagHandler.NAME))
    }

    @Test
    fun register_reRegisteringSameName_replacesHandler() {
        val registry = TagRegistry()
        val first = FakeTag("acmecorp.date")
        val second = FakeTag("acmecorp.date")
        registry.register(first)
        registry.register(second)
        assertTrue(registry.find("acmecorp.date") === second)
    }

    @Test
    fun eachInstance_isIndependentlyScoped() {
        val registryA = TagRegistry().apply { register(FakeTag("acmecorp.date")) }
        val registryB = TagRegistry()
        assertTrue(registryA.contains("acmecorp.date"))
        assertFalse(registryB.contains("acmecorp.date"))
    }
}
