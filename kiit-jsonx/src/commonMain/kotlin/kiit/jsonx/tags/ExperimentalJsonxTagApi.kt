/** url: www.kiit.dev */
package kiit.jsonx.tags

/**
 * Opt-in marker for [TagHandler] and anything that surfaces one (e.g. [TagRegistry.register],
 * [TagRegistry.find]).
 *
 * 1. `TagHandler` is the highest-stakes public API surface in this library, per the jsonx plan:
 *    it's only been proven against `@env`, a `Simple`-kind handler. Whether the same interface
 *    shape holds up for a `Structural`-kind handler (`@table`, Milestone 3.3) is exactly what
 *    that milestone tests.
 * 2. `WARNING`, not `ERROR`: using the interface today is fine, just not yet a stability promise.
 *    If `@table` forces a shape change, that change won't be a silent break for anyone who opted
 *    in, since opting in already means "I know this might move."
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message =
        "TagHandler is proven against only one real implementation (@env) so far. " +
            "Its shape may still change before the structural-tag case (@table) validates it further.",
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalJsonxTagApi
