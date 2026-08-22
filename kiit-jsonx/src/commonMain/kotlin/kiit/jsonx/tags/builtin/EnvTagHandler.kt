/** url: www.kiit.dev */
package kiit.jsonx.tags.builtin

import kiit.codes.Err
import kiit.codes.Invalid
import kiit.codes.Rejected
import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.error.JsonXError
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.tags.ExperimentalJsonxTagApi
import kiit.jsonx.tags.TagHandler
import kiit.jsonx.tags.TagKind
import kiit.result.Failure
import kiit.result.Success

/**
 * The first always-on built-in tag: `@env('VAR_NAME')` resolves to the named environment
 * variable's value as a string.
 *
 * 1. Exactly one string argument. A wrong argument count or a non-string argument is
 *    [Invalid.INVALID_VALUE], not a "missing" failure — the tag itself is well-formed, its
 *    argument isn't.
 * 2. An unset environment variable is [Rejected.NOT_EXISTS] — the tag and its argument are both
 *    fine, the thing they reference just isn't there right now.
 * 3. No default-value form (e.g. a second fallback argument) exists. Nothing in the PRD/plan
 *    documents one, so this doesn't invent one; a caller wanting a fallback composes it from
 *    [kiit.jsonx.extract.getStringOrElse] once transform-stage resolution (Milestone 3.4) lands.
 */
@ExperimentalJsonxTagApi
class EnvTagHandler : TagHandler {
    override val name: String = NAME
    override val kind: TagKind = TagKind.Simple

    override fun resolve(args: List<JsonXElement>): JsonXResult<JsonXElement> {
        val arg = args.singleOrNull()
        if (arg !is JsonXString) {
            return Failure(
                JsonXError(Err.of("@env expects exactly one string argument, got $args")),
                Invalid.INVALID_VALUE,
            )
        }

        val value =
            readEnvironmentVariable(arg.value)
                ?: return Failure(
                    JsonXError(Err.on(arg.value, "", "environment variable '${arg.value}' is not set")),
                    Rejected.NOT_EXISTS,
                )

        return Success(JsonXString(value))
    }

    companion object {
        const val NAME: String = "env"
    }
}
