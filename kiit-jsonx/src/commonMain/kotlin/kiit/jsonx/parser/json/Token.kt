/** url: www.kiit.dev */
package kiit.jsonx.parser.json

import kiit.jsonx.parser.state.SourcePosition

/**
 * A single lexical token produced by [JsonLexer] (and, by extension, any dialect lexer layered
 * on top of it).
 *
 * [text] is the raw, undecoded source slice — e.g. a [TokenType.JString] token's [text] includes
 * the surrounding quotes and any escape sequences exactly as written (`"hello\n"`, backslash-n
 * literal), and a [TokenType.JNumber] token's [text] is exactly its source digits (`123.45`).
 * [JsonLexer] validates grammar (escapes, number shape) but never decodes it — turning `text`
 * into a real `String`/`Long`/`Double` is the parser's job (Milestone 2.2), which reads [text].
 *
 * [start] is where the token begins in the source; there's no separate end offset because
 * [text]'s length recovers it.
 */
data class Token(val type: TokenType, val text: String, val start: SourcePosition)
