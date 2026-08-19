/** url: www.kiit.dev */
package kiit.jsonx.parser.core

/**
 * The kind of a [Token]. Carries no other data of its own beyond [ch] — every token's payload
 * lives on [Token] itself ([Token.text], the raw undecoded source slice), so a flat enum is
 * enough; there's nothing left that would need a sealed class's per-variant fields.
 *
 * Entries are prefixed with `J` (`JString`, `JNumber`, ...) to avoid shadowing
 * [kotlin.String]/[kotlin.Number] and to stay unambiguous even without the `TokenType.`
 * qualifier at a call site.
 *
 * [ch] is the single literal character [kiit.jsonx.parser.json.JsonLexer] dispatches on for the
 * six punctuation token types (`{`, `}`, `[`, `]`, `:`, `,`) — null for every other entry. A
 * [JNumber] can start with `-` or any digit, and [JTrue]/[JFalse]/[JNull] are multi-character
 * keywords whose first letter is only a dispatch hint, not the token's identity — modeling
 * either of those as `ch` would be misleading, so they're deliberately left null rather than
 * forced to fit.
 */
enum class TokenType(val ch: Char? = null) {
    JLBrace('{'),
    JRBrace('}'),
    JLBracket('['),
    JRBracket(']'),
    JColon(':'),
    JComma(','),
    JString,
    JNumber,
    JTrue,
    JFalse,
    JNull,

    /** Terminal — once `JsonLexer.nextToken` returns this, every later call returns it again. */
    JEndOfInput,
}
