/** url: www.kiit.dev */
package kiit.jsonx.parser.json

import kiit.codes.Err
import kiit.jsonx.error.JsonXError
import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.parser.state.LexerState
import kiit.jsonx.parser.state.SourcePosition

/**
 * Tokenizes strict RFC 8259 JSON text into a stream of [Token]s, one [nextToken] call at a time.
 * Produces tokens only — building a [kiit.jsonx.element.JsonXElement] tree from them is
 * [kiit.jsonx.parser.json]'s parser, Milestone 2.2.
 *
 * Validates grammar (string escapes, number shape) but never decodes it — every [Token.text] is
 * the raw source slice, untouched. Turning that into a real `String`/`Long`/`Double` is the
 * parser's job.
 *
 * Signals a lex failure by throwing [JsonXParseException], not by returning a `JsonXResult` —
 * see that class's KDoc for why. This is purely an internal control-flow detail: the module's
 * public entry point (the future `JsonParser.parse`) catches it and converts it into a real
 * `JsonXResult.Failure`; nothing about [JsonXParseException] is part of this library's public API.
 *
 * `open` and its `protected` read helpers are extension points for [Json5Lexer] (Milestone 2.3)
 * to layer JSON5's additions (comments, unquoted keys, single-quoted strings, hex numbers, ...)
 * on top of rather than re-implementing this class from scratch.
 *
 * Naming convention for helpers in this class and its dialect subclasses:
 * - `readX` — consumes a sequence of characters that becomes part of a token's content
 *   (`readNumber`, `readKeyword`, `readDigits`).
 * - `isX`/`hasX` — a pure, non-consuming check ([isHexDigit]).
 * - `skipX` — consumes characters without building a value from them ([skipInsignificant],
 *   [skipUnicodeEscape], [skipEscape]).
 */
open class JsonLexer(text: CharSequence) {
    protected val state: LexerState = LexerState(text)

    /** Derived from [TokenType.ch] so the char↔type mapping has exactly one source of truth. */
    private val singlePunctuationTokens: Map<Char, TokenType> =
        TokenType.entries.mapNotNull { type -> type.ch?.let { it to type } }.toMap()

    /**
     * Returns the next token. Throws [JsonXParseException] if the text at the current position
     * doesn't start a valid token — see the class KDoc for why this isn't a `JsonXResult`.
     *
     * Once [TokenType.JEndOfInput] is returned, every subsequent call returns it again rather
     * than re-scanning past the end.
     */
    open fun nextToken(): Token {
        skipInsignificant()
        if (state.isAtEnd()) return Token(TokenType.JEndOfInput, "", state.snapshot())

        val position = state.snapshot()
        val c = state.peek()!!

        singlePunctuationTokens[c]?.let { type ->
            state.advance()
            return Token(type, c.toString(), position)
        }

        return when {
            c == '"' -> readString(position)
            c.isAsciiLetter() -> readKeyword(position)
            c == '-' || c.isAsciiDigit() -> readNumber(position)
            else -> lexError("unexpected character '$c'", position)
        }
    }

    /**
     * Advances past whitespace (and, for dialects layered on top, comments). Strict JSON
     * whitespace is exactly space, tab, `\n`, `\r` — U+2028/U+2029 are not JSON whitespace, they
     * only affect [LexerState]'s line/column bookkeeping.
     */
    protected open fun skipInsignificant() {
        while (true) {
            when (state.peek()) {
                ' ', '\t', '\n', '\r' -> state.advance()
                else -> return
            }
        }
    }

    /**
     * Reads a quoted string starting at [position] (the opening [quote] has not been consumed
     * yet). [quote] is parameterized so [Json5Lexer] can reuse this for single-quoted strings.
     * Returns the raw source slice, quotes included, as [Token.text] — see [Token]'s KDoc.
     */
    protected fun readString(position: SourcePosition, quote: Char = '"'): Token {
        state.advance() // opening quote

        while (true) {
            if (state.isAtEnd()) lexError("unterminated string", position)
            val c = state.peek()!!

            when {
                c == quote -> {
                    state.advance()
                    return Token(TokenType.JString, state.slice(position.offset), position)
                }
                c == '\\' -> {
                    state.advance()
                    if (!skipEscape()) lexError("invalid escape sequence", state.snapshot())
                }
                c.code < 0x20 -> lexError("unescaped control character in string", state.snapshot())
                else -> state.advance()
            }
        }
    }

    /** Consumes one escape sequence, having already consumed the leading `\`. Does not decode it. */
    private fun skipEscape(): Boolean {
        if (state.isAtEnd()) return false
        return when (state.advance()) {
            '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> true
            'u' -> skipUnicodeEscape()
            else -> false
        }
    }

    /** Consumes (without decoding) exactly 4 hex digits following `\u`. */
    private fun skipUnicodeEscape(): Boolean {
        repeat(4) {
            val c = state.peek() ?: return false
            if (!isHexDigit(c)) return false
            state.advance()
        }
        return true
    }

    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /**
     * Reads a strict JSON number: `-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?`. No leading `+`, no
     * leading zeros before a nonzero digit, no bare leading/trailing decimal point — those are
     * JSON5 relaxations, added by [Json5Lexer] overriding this method rather than here. Returns
     * the raw source slice as [Token.text] — see [Token]'s KDoc.
     */
    protected open fun readNumber(position: SourcePosition): Token {
        if (state.peek() == '-') state.advance()

        if (!readIntegerPart()) lexError("invalid number: expected a digit", position)
        if (readFractionPart() == null) lexError("invalid number: expected a digit after '.'", position)
        if (readExponentPart() == null) lexError("invalid number: expected a digit in exponent", position)

        return Token(TokenType.JNumber, state.slice(position.offset), position)
    }

    /** Strict JSON integer part: `0` or a nonzero digit followed by more digits. Returns false if absent. */
    private fun readIntegerPart(): Boolean {
        if (state.peek() == '0') {
            state.advance()
            return true
        }
        if (state.peek()?.isAsciiDigit() != true) return false
        readDigits()
        return true
    }

    /** Optional `.` + digits. Returns null on a malformed `.` with no following digit, else present/absent. */
    private fun readFractionPart(): Boolean? {
        if (state.peek() != '.') return false
        state.advance()
        if (state.peek()?.isAsciiDigit() != true) return null
        readDigits()
        return true
    }

    /** Optional `[eE][+-]?` + digits. Returns null on a malformed exponent with no digit, else present/absent. */
    private fun readExponentPart(): Boolean? {
        if (state.peek() != 'e' && state.peek() != 'E') return false
        state.advance()
        if (state.peek() == '+' || state.peek() == '-') state.advance()
        if (state.peek()?.isAsciiDigit() != true) return null
        readDigits()
        return true
    }

    private fun readDigits() {
        while (state.peek()?.isAsciiDigit() == true) state.advance()
    }

    /**
     * Reads a keyword literal starting at [position], picking which one (`true`/`false`/`null`)
     * from its first character, then matching the rest exactly. Fails on any other identifier-like
     * text (e.g. `tru3`, or a letter that doesn't start any of the three keywords).
     */
    private fun readKeyword(position: SourcePosition): Token {
        val (type, keyword) =
            when (state.peek()) {
                't' -> TokenType.JTrue to "true"
                'f' -> TokenType.JFalse to "false"
                'n' -> TokenType.JNull to "null"
                else -> lexError("invalid literal: unexpected character '${state.peek()}'", position)
            }

        for (expected in keyword) {
            if (state.isAtEnd() || state.peek() != expected) {
                lexError("invalid literal, expected '$keyword'", position)
            }
            state.advance()
        }
        return Token(type, keyword, position)
    }

    /** Throws [JsonXParseException] for a lex failure at [position] — never returns. */
    protected fun lexError(message: String, position: SourcePosition): Nothing {
        throw JsonXParseException(
            JsonXError(Err.of(message), line = position.line, column = position.column, offset = position.offset),
        )
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
}
