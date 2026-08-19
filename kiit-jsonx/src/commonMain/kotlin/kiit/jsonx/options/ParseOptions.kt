/** url: www.kiit.dev */
package kiit.jsonx.options

/**
 * Parse-time configuration, threaded through every dialect's parser.
 *
 * Stubbed in Phase 1 with just [duplicateKeyPolicy] and [retainComments] — `tagRegistry` and
 * `transforms` are added in Phase 3, `enabledStdTags` in Phase 4.
 */
data class ParseOptions(
    val duplicateKeyPolicy: DuplicateKeyPolicy = DuplicateKeyPolicy.Error,
    val retainComments: Boolean = false,
) {
    enum class DuplicateKeyPolicy {
        Error,
        LastWins,
        FirstWins,
        CollectIntoArray,
    }
}
