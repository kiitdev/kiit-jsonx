package kiit.jsonx.options

import kotlin.test.Test
import kotlin.test.assertEquals

// =================================================================================================
// EnvAccessPolicyTest: the three policy shapes construct and compare as expected
// =================================================================================================
class EnvAccessPolicyTest {
    @Test
    fun allowAll_isASingleton() {
        assertEquals(EnvAccessPolicy.AllowAll, EnvAccessPolicy.AllowAll)
    }

    @Test
    fun deny_isASingleton() {
        assertEquals(EnvAccessPolicy.Deny, EnvAccessPolicy.Deny)
    }

    @Test
    fun allowlist_comparesByNames() {
        val a = EnvAccessPolicy.Allowlist(setOf("DB_HOST", "DB_PORT"))
        val b = EnvAccessPolicy.Allowlist(setOf("DB_HOST", "DB_PORT"))
        assertEquals(a, b)
    }

    @Test
    fun allowlist_differentNames_notEqual() {
        val a = EnvAccessPolicy.Allowlist(setOf("DB_HOST"))
        val b = EnvAccessPolicy.Allowlist(setOf("DB_PORT"))
        assert(a != b)
    }
}
