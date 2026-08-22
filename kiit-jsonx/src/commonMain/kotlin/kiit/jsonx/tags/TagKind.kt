/** url: www.kiit.dev */
package kiit.jsonx.tags

/**
 * How a [TagHandler] consumes its tag's contents.
 *
 * 1. [Simple]: an ordinary argument list, already parsed into `List<JsonXElement>` by the
 *    normal value grammar before the handler ever sees it. Every currently-specified tag is
 *    this kind, including `@table` — its one argument happens to be a `{names, rows}` object,
 *    but that object is still just an ordinary parsed value, nothing custom about the grammar.
 * 2. [Structural]: a custom sub-grammar inside the parens, handed to the handler as a bounded
 *    token slice rather than a pre-parsed argument list. Declared in the taxonomy but
 *    deliberately unimplemented: no current tag needs custom grammar, so there's nothing real to
 *    build this against yet. Build it only when a genuine tag demonstrably can't be expressed as
 *    ordinary [Simple] arguments, not speculatively.
 */
enum class TagKind {
    Simple,
    Structural,
}
