/** url: www.kiit.dev */
package kiit.jsonx.options

import kiit.jsonx.tags.TagRegistry

/**
 * Parse-time configuration, threaded through every dialect's parser.
 *
 * [tagRegistry] defaults to a fresh [TagRegistry] (pre-seeded with the always-on built-ins,
 * `@env` for now) rather than a shared instance, so registering a tag for one [ParseOptions]
 * never leaks into another's. `transforms` is added later in Phase 3, `enabledStdTags` in
 * Phase 4.
 */
data class ParseOptions(
    val duplicateKeyPolicy: DuplicateKeyPolicy = DuplicateKeyPolicy.Error,
    val retainComments: Boolean = false,
    val tagRegistry: TagRegistry = TagRegistry(),
) {
    enum class DuplicateKeyPolicy {
        Error,
        LastWins,
        FirstWins,
        CollectIntoArray,
    }
}
