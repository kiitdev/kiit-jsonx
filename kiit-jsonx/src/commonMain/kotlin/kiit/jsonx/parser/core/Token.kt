/** url: www.kiit.dev */
package kiit.jsonx.parser.core

/**
 * A single lexical token.
 *
 * 1. Produced by [kiit.jsonx.parser.json.JsonLexer] (and, by extension, any dialect lexer
 *    layered on top of it). That's why this type lives here in `parser.core` rather than under
 *    `parser.json`.
 * 2. [text] is the raw, undecoded source slice: a [TokenType.JString] token's [text] includes
 *    the surrounding quotes and any escape sequences exactly as written (`"hello\n"`,
 *    backslash-n literal), and a [TokenType.JNumber] token's [text] is exactly its source
 *    digits (`123.45`). The lexer validates grammar (escapes, number shape) but never decodes
 *    it; turning `text` into a real `String`/`Long`/`Double` is the parser's job.
 * 3. [start] is where the token begins in the source; there's no separate end offset because
 *    [text]'s length recovers it.
 */
data class Token(val type: TokenType, val text: String, val start: SourcePosition)
