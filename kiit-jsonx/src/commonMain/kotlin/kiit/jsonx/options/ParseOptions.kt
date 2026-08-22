/** url: www.kiit.dev */
package kiit.jsonx.options

import kiit.jsonx.tags.TagRegistry

/**
 * Parse-time configuration, threaded through every dialect's parser.
 *
 * 1. [tagRegistry] defaults to a fresh [TagRegistry] (pre-seeded with the always-on
 *    eagerly-resolved built-ins, `@table` for now) rather than a shared instance, so registering
 *    a tag for one [ParseOptions] never leaks into another's.
 * 2. [envAccessPolicy] defaults to [EnvAccessPolicy.Deny]: secure by default. It's inert until
 *    the `resolveEnv` transform (which actually reads the environment) exists; parsing itself
 *    never touches the environment regardless of this setting.
 * 3. `transforms` is added later in Phase 3, `enabledStdTags` in Phase 4.
 */
data class ParseOptions(
    val duplicateKeyPolicy: DuplicateKeyPolicy = DuplicateKeyPolicy.Error,
    val retainComments: Boolean = false,
    val tagRegistry: TagRegistry = TagRegistry(),
    val envAccessPolicy: EnvAccessPolicy = EnvAccessPolicy.Deny,
) {
    enum class DuplicateKeyPolicy {
        Error,
        LastWins,
        FirstWins,
        CollectIntoArray,
    }
}
