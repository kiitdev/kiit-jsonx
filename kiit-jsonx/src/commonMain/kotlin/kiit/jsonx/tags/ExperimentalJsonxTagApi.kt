/** url: www.kiit.dev */
package kiit.jsonx.tags

/**
 * Opt-in marker for [TagHandler] and anything that surfaces one (e.g. [TagRegistry.register],
 * [TagRegistry.find]).
 *
 * 1. `TagHandler` is the highest-stakes public API surface in this library, per the jsonx plan:
 *    it's only been proven against `@table`, a `Simple`-kind handler resolved eagerly during
 *    parsing. `TagKind.Structural` remains declared but unimplemented — no current tag needs a
 *    custom sub-grammar, `@table` turned out to be expressible as ordinary arguments — so the
 *    interface hasn't been proven against that case at all.
 * 2. `WARNING`, not `ERROR`: using the interface today is fine, just not yet a stability promise.
 *    If a future external or `Structural` tag forces a shape change, that change won't be a
 *    silent break for anyone who opted in, since opting in already means "I know this might move."
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message =
        "TagHandler is proven against only one real implementation (@table) so far, and only " +
            "for TagKind.Simple. Its shape may still change before a Structural-kind tag validates it further.",
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalJsonxTagApi
