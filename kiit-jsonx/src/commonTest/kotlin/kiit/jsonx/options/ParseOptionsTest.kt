package kiit.jsonx.options

import kiit.jsonx.tags.ExperimentalJsonxTagApi
import kiit.jsonx.tags.builtin.TableTagHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =================================================================================================
// ParseOptionsTest: Phase 1 stub defaults, plus the Phase 3 tagRegistry default
// =================================================================================================
@OptIn(ExperimentalJsonxTagApi::class)
class ParseOptionsTest {
    @Test
    fun defaults_areErrorOnDuplicateAndNoCommentRetention() {
        val options = ParseOptions()
        assertEquals(ParseOptions.DuplicateKeyPolicy.Error, options.duplicateKeyPolicy)
        assertFalse(options.retainComments)
    }

    @Test
    fun defaultTagRegistry_isPreSeededWithBuiltins() {
        assertTrue(ParseOptions().tagRegistry.contains(TableTagHandler.NAME))
    }

    @Test
    fun defaultTagRegistry_isFreshPerInstance() {
        // Each ParseOptions gets its own TagRegistry, not a shared one — registering on one
        // instance's registry must never be visible through another's.
        val a = ParseOptions()
        val b = ParseOptions()
        assertTrue(a.tagRegistry !== b.tagRegistry)
    }

    @Test
    fun defaultEnvAccessPolicy_isDeny() {
        assertEquals(EnvAccessPolicy.Deny, ParseOptions().envAccessPolicy)
    }

    @Test
    fun duplicateKeyPolicy_hasAllFourAlternatives() {
        val policies = ParseOptions.DuplicateKeyPolicy.entries
        assertEquals(
            setOf(
                ParseOptions.DuplicateKeyPolicy.Error,
                ParseOptions.DuplicateKeyPolicy.LastWins,
                ParseOptions.DuplicateKeyPolicy.FirstWins,
                ParseOptions.DuplicateKeyPolicy.CollectIntoArray,
            ),
            policies.toSet(),
        )
    }
}
