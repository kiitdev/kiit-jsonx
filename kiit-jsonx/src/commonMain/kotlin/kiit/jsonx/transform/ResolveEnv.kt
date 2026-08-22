/** url: www.kiit.dev */
package kiit.jsonx.transform

import kiit.codes.Err
import kiit.codes.Failed
import kiit.codes.Invalid
import kiit.codes.Rejected
import kiit.codes.Restricted
import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.error.JsonXError
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.options.EnvAccessPolicy
import kiit.result.Failure
import kiit.result.Success

private const val ENV_TAG_NAME = "env"

/**
 * Resolves every `@env(...)` occurrence in [tree] against the process environment, gated by
 * [policy] (security model, PRD §7.5): [EnvAccessPolicy.Deny] (the [kiit.jsonx.options.ParseOptions]
 * default) rejects every variable outright; [EnvAccessPolicy.Allowlist] permits only the named
 * variables; [EnvAccessPolicy.AllowAll] permits any.
 *
 * 1. `@env('NAME')` errors if `NAME` is unset; `@env('NAME', 'default')` falls back to `'default'`
 *    instead of erroring.
 * 2. Every failure here — access denied, or the variable unset with no default — reports only the
 *    variable *name*, never a resolved value: there is never a value to redact in the first place,
 *    since these are exactly the cases where resolution didn't produce one (§7.5.4).
 * 3. [lookup] defaults to the real process environment; overriding it is how tests (and any other
 *    caller that wants deterministic behavior) avoid depending on actual process state.
 */
fun resolveEnv(
    tree: JsonXElement,
    policy: EnvAccessPolicy,
    lookup: (String) -> String? = ::readEnvironmentVariable,
): JsonXResult<JsonXElement> {
    fun resolveEnvTag(tagged: JsonXTagged): JsonXResult<JsonXElement> {
        val name =
            (tagged.args.getOrNull(0) as? JsonXString)?.value
                ?: return failure(Invalid.INVALID_VALUE, "@env requires a string variable name as its first argument")
        val default = (tagged.args.getOrNull(1) as? JsonXString)?.value

        val allowed =
            when (policy) {
                is EnvAccessPolicy.AllowAll -> true
                is EnvAccessPolicy.Allowlist -> name in policy.names
                is EnvAccessPolicy.Deny -> false
            }
        if (!allowed) {
            return failure(Restricted.DENIED, "access to environment variable '$name' is denied by EnvAccessPolicy")
        }

        val value = lookup(name)
        return when {
            value != null -> Success(JsonXString(value))
            default != null -> Success(JsonXString(default))
            else -> failure(Rejected.NOT_EXISTS, "environment variable '$name' is not set and no default was given")
        }
    }

    return tree.mapTagged { tagged ->
        if (tagged.name != ENV_TAG_NAME) return@mapTagged Success(tagged)
        resolveEnvTag(tagged)
    }
}

private fun failure(status: Failed, message: String): JsonXResult<Nothing> {
    return Failure(JsonXError(Err.of(message)), status)
}
