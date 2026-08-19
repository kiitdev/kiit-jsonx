package kiit.jsonx.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// =================================================================================================
// ParseOptionsTest: Phase 1 stub defaults
// =================================================================================================
class ParseOptionsTest {
    @Test
    fun defaults_areErrorOnDuplicateAndNoCommentRetention() {
        val options = ParseOptions()
        assertEquals(ParseOptions.DuplicateKeyPolicy.Error, options.duplicateKeyPolicy)
        assertFalse(options.retainComments)
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
