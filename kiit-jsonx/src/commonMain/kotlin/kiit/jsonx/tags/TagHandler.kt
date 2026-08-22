/** url: www.kiit.dev */
package kiit.jsonx.tags

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.error.JsonXResult

/**
 * Resolves one registered `@name(args)` tag into a plain [JsonXElement].
 *
 * 1. [name] is the full name a `JsonXTagged.name` must match to route to this handler: unprefixed
 *    for a built-in (`"env"`), `namespace.tag` for an external one (`"acmecorp.date"`) — see
 *    [TagRegistry.register] for the namespace rule.
 * 2. [kind] tells the parser how to hand this handler its contents: see [TagKind].
 * 3. [resolve] receives already-parsed, unresolved arguments and returns a [JsonXResult] rather
 *    than throwing — a bad argument (wrong count, wrong type) or an unresolvable reference (e.g.
 *    an unset environment variable) is an ordinary [kiit.jsonx.error.JsonXError] failure, not an
 *    exception.
 * 4. This is parsing/resolution only, not wiring: nothing yet calls [resolve] automatically
 *    during a parse. That's the transform pipeline's job (Milestone 3.4).
 */
@ExperimentalJsonxTagApi
interface TagHandler {
    val name: String
    val kind: TagKind

    fun resolve(args: List<JsonXElement>): JsonXResult<JsonXElement>
}
