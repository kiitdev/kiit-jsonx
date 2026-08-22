/** url: www.kiit.dev */
package kiit.jsonx.options

/**
 * Governs which environment variables `@env` may read once the (not-yet-built) `resolveEnv`
 * transform actually resolves it.
 *
 * 1. Parsing jsonx text carries some of the trust profile of executing code, not just reading
 *    passive data, since `@env` (and any side-effecting external tag) can touch host resources.
 *    [ParseOptions.envAccessPolicy] defaults to [Deny] so that's never true by accident: a
 *    consumer must explicitly opt in to some level of access.
 * 2. This type exists now, ahead of `resolveEnv` itself, because `@env` recognized as tag syntax
 *    and `@env` actually reading the environment are deliberately different milestones: parsing
 *    never touches the environment, no matter what this policy says. Only `resolveEnv` reads it,
 *    and only within whatever this policy allows.
 */
sealed class EnvAccessPolicy {
    /** Every environment variable is readable. */
    object AllowAll : EnvAccessPolicy()

    /** Only the named variables are readable; anything else behaves as if it were unset. */
    data class Allowlist(val names: Set<String>) : EnvAccessPolicy()

    /** No environment variable is readable — `@env` always fails to resolve. The default. */
    object Deny : EnvAccessPolicy()
}
