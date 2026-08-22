/** url: www.kiit.dev */
package kiit.jsonx.tags

/**
 * How a [TagHandler] consumes its tag's contents.
 *
 * 1. [Simple]: an ordinary argument list, already parsed into `List<JsonXElement>` by the
 *    normal value grammar before the handler ever sees it. `@env('DB_HOST')` is this kind.
 * 2. [Structural]: a custom sub-grammar inside the parens, handed to the handler as a bounded
 *    token slice rather than a pre-parsed argument list. `@table(...)` (Milestone 3.3) is this
 *    kind. Not implemented yet; this milestone only proves the [Simple] path end to end.
 */
enum class TagKind {
    Simple,
    Structural,
}
