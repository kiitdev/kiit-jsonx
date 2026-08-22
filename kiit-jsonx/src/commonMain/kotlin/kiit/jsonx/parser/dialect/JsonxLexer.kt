/** url: www.kiit.dev */
package kiit.jsonx.parser.dialect

import kiit.jsonx.parser.core.SourcePosition
import kiit.jsonx.parser.core.Token
import kiit.jsonx.parser.core.TokenType
import kiit.jsonx.parser.json5.Json5Lexer

/**
 * Tokenizes the jsonx dialect, extending [Json5Lexer] with the two additions that make jsonx a
 * genuine syntactic superset of JSON5 rather than just JSON5 itself:
 *
 * 1. Triple-quoted multiline strings (`"""..."""`), TOML-aligned: a newline immediately after
 *    the opening delimiter is trimmed at decode time (not here; see `JsonxDecoder`), no
 *    automatic dedent, standard JSON5 escape processing plus `\`-line-continuation that also
 *    eats the following line's leading whitespace, and unescaped `"`/`""` are permitted inside
 *    (only a run of three quote characters closes the string — TOML disallows an unescaped
 *    three-quote run anywhere else, so first-match-wins is unambiguous, never a special case).
 * 2. `@name(args)`/`@namespace.name(args)` tag literals, lexed as one atomic [TokenType.JTag]
 *    token (raw text includes the leading `@` and every segment/dot). Parsing the parenthesized
 *    argument list into a `JsonxTagged` node is `JsonxParser`'s job (Milestone 2.5); this class
 *    only recognizes where a tag name starts and ends.
 * 3. `#`-style line comments, dialect-only — never added to [Json5Lexer], per the plan's
 *    explicit constraint that this stays out of spec-pure JSON5.
 *
 * Still validates rather than decodes; see `JsonLexer`'s KDoc for why.
 */
open class JsonxLexer(text: CharSequence) : Json5Lexer(text) {
    override fun nextToken(): Token {
        skipInsignificant()
        if (state.isAtEnd()) return Token(TokenType.JEndOfInput, "", state.snapshot())

        val position = state.snapshot()
        val c = state.peek()!! // peek only, so nothing is consumed until we know what c starts

        singlePunctuationTokens[c]?.let { type ->
            state.advance()
            return Token(type, c.toString(), position)
        }

        return when {
            c == '"' -> readString(position, quote = '"')
            c == '\'' -> readString(position, quote = '\'')
            c == '@' -> readTag(position)
            c == '-' || c == '+' || c == '.' || c.isAsciiDigit() -> readNumber(position)
            isIdentifierStart(c) -> readIdentifierOrKeyword(position)
            else -> lexError("unexpected character '$c'", position)
        }
    }

    /** Adds `#`-style line comments on top of [Json5Lexer]'s whitespace/comment handling. Dialect-only. */
    override fun skipInsignificant() {
        while (true) {
            val c = state.peek() ?: return
            when {
                c == '#' -> skipHashComment()
                isJson5Whitespace(c) -> state.advance()
                c == '/' && state.peek(1) == '/' -> skipLineComment()
                c == '/' && state.peek(1) == '*' -> skipBlockComment()
                else -> return
            }
        }
    }

    private fun skipHashComment() {
        state.advance() // '#'
        while (!state.isAtEnd() && !isLineTerminator(state.peek()!!)) {
            state.advance()
        }
        // The line terminator itself is left for the outer skipInsignificant loop to consume,
        // same convention as Json5Lexer's // and /* */ comments.
    }

    /**
     * Detects a triple-quoted string before delegating; a lone or double `"` falls through to
     * [Json5Lexer.readString] unchanged. At this point the opening quote hasn't been consumed
     * yet, so `peek(1)`/`peek(2)` look at the two characters immediately following it.
     */
    override fun readString(position: SourcePosition, quote: Char): Token {
        if (quote == '"' && state.peek(1) == '"' && state.peek(2) == '"') {
            return readTripleQuotedString(position)
        }
        return super.readString(position, quote)
    }

    /**
     * Scans a triple-quoted string body. Unlike [Json5Lexer.readString], a raw (unescaped) line
     * terminator is valid content here, not an error, since the whole point is spanning source
     * lines. A run of three quote characters always closes the string; TOML disallows an
     * unescaped three-quote run appearing as content, so the first one found is unambiguously
     * the closer, no lookahead-past-it needed.
     */
    private fun readTripleQuotedString(position: SourcePosition): Token {
        state.advance() // """
        state.advance()
        state.advance()

        while (true) {
            if (state.isAtEnd()) lexError("unterminated triple-quoted string", position)
            if (state.peek() == '"' && state.peek(1) == '"' && state.peek(2) == '"') {
                state.advance()
                state.advance()
                state.advance()
                return Token(TokenType.JString, state.slice(position.offset), position)
            }
            if (state.peek() == '\\') {
                state.advance()
                skipJson5Escape()
            } else {
                state.advance() // any other char, including a raw line terminator, is valid content
            }
        }
    }

    /**
     * Reads `@name` or `@namespace.name` as one token. Namespace depth isn't validated here
     * (e.g. "exactly one dot for an external tag") — that belongs to Phase 3's `TagRegistry`,
     * which owns tag-name policy; this lexer only recognizes the dotted-identifier shape.
     */
    private fun readTag(position: SourcePosition): Token {
        state.advance() // '@'
        if (state.peek()?.let(::isIdentifierStart) != true) {
            lexError("invalid tag: expected a name after '@'", position)
        }
        readIdentifierRun()

        while (state.peek() == '.' && state.peek(1)?.let(::isIdentifierStart) == true) {
            state.advance() // '.'
            readIdentifierRun()
        }

        return Token(TokenType.JTag, state.slice(position.offset), position)
    }

    /** Consumes one identifier segment: an already-confirmed start char, then any run of identifier-part chars. */
    private fun readIdentifierRun() {
        state.advance()
        while (!state.isAtEnd() && isIdentifierPart(state.peek()!!)) state.advance()
    }
}
