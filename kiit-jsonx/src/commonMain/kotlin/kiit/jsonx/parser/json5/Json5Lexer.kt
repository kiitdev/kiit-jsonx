/** url: www.kiit.dev */
package kiit.jsonx.parser.json5

import kiit.jsonx.parser.core.SourcePosition
import kiit.jsonx.parser.core.Token
import kiit.jsonx.parser.core.TokenType
import kiit.jsonx.parser.json.JsonLexer

/**
 * Tokenizes [JSON5](https://json5.org) text, extending [JsonLexer] with:
 * - `//` line comments and `/* */` block comments.
 * - Unquoted, identifier-style object keys (produced as [TokenType.JIdentifier] — a bare
 *   identifier is only meaningful in key position, which is [kiit.jsonx.parser.json5.Json5Parser]'s job to
 *   enforce, not the lexer's).
 * - Single-quoted strings, in addition to double-quoted.
 * - `Infinity`/`NaN` (with an optional leading `+`/`-`) as [TokenType.JNumber] tokens.
 * - Hex number literals (`0x1A`), leading/trailing decimal points (`.5`, `5.`), and a leading `+`.
 * - Line continuation (`\` + line terminator) inside strings, so a string can span source lines.
 * - JSON5's wider whitespace set: `<VT>`, `<FF>`, `<NBSP>`, `<BOM>`, any Unicode space-separator
 *   character, and (unlike strict JSON) `<LS>`/`<PS>` (U+2028/U+2029) count as whitespace too.
 *
 * Still validates rather than decodes — see [JsonLexer]'s KDoc for why. Decoding JSON5-specific
 * text (hex numbers, `Infinity`/`NaN`, line-continuation removal, the wider escape set) is
 * `Json5Decoder`'s job (Milestone 2.4), not this class's.
 */
open class Json5Lexer(text: CharSequence) : JsonLexer(text) {
    override fun nextToken(): Token {
        skipInsignificant()
        if (state.isAtEnd()) return Token(TokenType.JEndOfInput, "", state.snapshot())

        val position = state.snapshot()
        val c = state.peek()!! // peek only, so nothing is consumed until we know what c starts

        // Reused as-is from JsonLexer: JSON5 introduces no new single-char punctuation.
        singlePunctuationTokens[c]?.let { type ->
            state.advance()
            return Token(type, c.toString(), position)
        }

        return when {
            c == '"' -> readString(position, quote = '"')
            c == '\'' -> readString(position, quote = '\'')
            c == '-' || c == '+' || c == '.' || c.isAsciiDigit() -> readNumber(position)
            isIdentifierStart(c) -> readIdentifierOrKeyword(position)
            else -> lexError("unexpected character '$c'", position)
        }
    }

    /**
     * JSON5 whitespace is wider than strict JSON's: tab/VT/FF/space/NBSP/BOM/any Unicode
     * space-separator, plus every line terminator (`\n`, `\r`, U+2028, U+2029 — the latter two
     * are *not* whitespace in strict JSON, only line-position bookkeeping there). Also skips
     * `//` and `/* */` comments, which JSON5 permits anywhere whitespace is permitted.
     */
    override fun skipInsignificant() {
        while (true) {
            val c = state.peek() ?: return
            when {
                isJson5Whitespace(c) -> state.advance()
                c == '/' && state.peek(1) == '/' -> skipLineComment()
                c == '/' && state.peek(1) == '*' -> skipBlockComment()
                else -> return
            }
        }
    }

    /** JSON5's wider whitespace: space/tab/VT/FF/NBSP/BOM/any Unicode space-separator, plus every line terminator. */
    private fun isJson5Whitespace(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\r' ||
            c == '\u000B' || c == '\u000C' ||
            c == '\u00A0' || c == '\uFEFF' ||
            c == '\u2028' || c == '\u2029' ||
            c.category == CharCategory.SPACE_SEPARATOR

    private fun skipLineComment() {
        state.advance() // first '/'
        state.advance() // second '/'
        while (!state.isAtEnd() && !isLineTerminator(state.peek()!!)) {
            state.advance()
        }
        // The line terminator itself is left for the outer skipInsignificant loop to consume.
    }

    private fun skipBlockComment() {
        val start = state.snapshot()
        state.advance() // '/'
        state.advance() // '*'
        while (true) {
            if (state.isAtEnd()) lexError("unterminated block comment", start)
            if (state.peek() == '*' && state.peek(1) == '/') {
                state.advance()
                state.advance()
                return
            }
            state.advance()
        }
    }

    /**
     * Reads a quoted string. Overrides [JsonLexer.readString] for two JSON5-specific relaxations:
     * raw (non-line-terminator) control characters are allowed unescaped, and a line terminator
     * may appear only as part of a `\`-escaped line continuation, never raw.
     */
    override fun readString(position: SourcePosition, quote: Char): Token {
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
                    skipJson5Escape()
                }
                isLineTerminator(c) -> lexError("unescaped line terminator in string", state.snapshot())
                else -> state.advance()
            }
        }
    }

    /**
     * Consumes one JSON5 escape sequence, having already consumed the leading `\`. JSON5's
     * escape grammar is deliberately permissive: a line terminator here is a line continuation
     * (the string just carries on), `\xHH`/`\uHHHH` need exactly 2/4 valid hex digits, and *any
     * other single character* is accepted as a `NonEscapeCharacter` (`\q` is simply `q`) — unlike
     * strict JSON, an unrecognized escape is not an error.
     */
    private fun skipJson5Escape() {
        if (state.isAtEnd()) lexError("invalid escape sequence", state.snapshot())
        val c = state.advance()
        when {
            isLineTerminator(c) -> Unit // line continuation — \r\n already consumed as one by advance()
            c == 'x' -> if (!skipFixedHexDigits(2)) lexError("invalid hex escape", state.snapshot())
            c == 'u' -> if (!skipFixedHexDigits(4)) lexError("invalid unicode escape", state.snapshot())
            else -> Unit // CharacterEscapeSequence or NonEscapeCharacter — always valid, one char
        }
    }

    /**
     * Reads a JSON5 number: optional leading `+`/`-`, then either `Infinity`/`NaN`, a hex
     * literal (`0x1A`), or a decimal literal with optional leading/trailing decimal point
     * (`.5`, `5.`) and the usual optional exponent.
     */
    override fun readNumber(position: SourcePosition): Token {
        if (state.peek() == '+' || state.peek() == '-') state.advance()

        val c = state.peek()
        return when {
            c != null && isIdentifierStart(c) -> readSpecialNumericValue(position)
            state.peek() == '0' && (state.peek(1) == 'x' || state.peek(1) == 'X') -> readHexNumber(position)
            else -> readDecimalNumber(position)
        }
    }

    /** `Infinity` or `NaN`, having already consumed an optional leading sign. */
    private fun readSpecialNumericValue(position: SourcePosition): Token {
        val start = state.offset
        while (!state.isAtEnd() && isIdentifierPart(state.peek()!!)) state.advance()
        val word = state.slice(start)
        if (word != "Infinity" && word != "NaN") lexError("invalid number", position)
        return Token(TokenType.JNumber, state.slice(position.offset), position)
    }

    private fun readHexNumber(position: SourcePosition): Token {
        state.advance() // '0'
        state.advance() // 'x' or 'X'
        if (state.peek()?.let(::isHexDigit) != true) lexError("invalid hex number: expected a hex digit", position)
        while (state.peek()?.let(::isHexDigit) == true) state.advance()
        return Token(TokenType.JNumber, state.slice(position.offset), position)
    }

    /** Decimal literal, having already consumed an optional leading sign and ruled out hex/special forms. */
    private fun readDecimalNumber(position: SourcePosition): Token {
        var hasDigits = false

        if (state.peek()?.isAsciiDigit() == true) {
            while (state.peek()?.isAsciiDigit() == true) state.advance()
            hasDigits = true
        }

        if (state.peek() == '.') {
            state.advance()
            while (state.peek()?.isAsciiDigit() == true) {
                state.advance()
                hasDigits = true
            }
        }

        if (!hasDigits) lexError("invalid number: expected at least one digit", position)

        if (state.peek() == 'e' || state.peek() == 'E') {
            state.advance()
            if (state.peek() == '+' || state.peek() == '-') state.advance()
            if (state.peek()?.isAsciiDigit() != true) lexError("invalid number: expected a digit in exponent", position)
            while (state.peek()?.isAsciiDigit() == true) state.advance()
        }

        return Token(TokenType.JNumber, state.slice(position.offset), position)
    }

    /**
     * Reads an unquoted identifier and classifies it: `true`/`false`/`null` remain their own
     * token types (same as strict JSON), `Infinity`/`NaN` become [TokenType.JNumber], and
     * anything else is a bare [TokenType.JIdentifier] — only meaningful as an object key, which
     * `Json5Parser` (Milestone 2.4) enforces.
     */
    private fun readIdentifierOrKeyword(position: SourcePosition): Token {
        val start = state.offset
        state.advance() // identifier-start char, already confirmed by the caller
        while (!state.isAtEnd() && isIdentifierPart(state.peek()!!)) state.advance()
        val text = state.slice(start)

        val type =
            when (text) {
                "true" -> TokenType.JTrue
                "false" -> TokenType.JFalse
                "null" -> TokenType.JNull
                "Infinity", "NaN" -> TokenType.JNumber
                else -> TokenType.JIdentifier
            }
        return Token(type, text, position)
    }

    /**
     * Approximates ECMAScript `IdentifierStart`: a Unicode letter, `$`, or `_`. Does not support
     * `\uXXXX`-escaped characters inside an identifier — a known, deliberate simplification.
     */
    private fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '$' || c == '_'

    /** Approximates ECMAScript `IdentifierPart`: [isIdentifierStart] plus any Unicode digit. */
    private fun isIdentifierPart(c: Char): Boolean = isIdentifierStart(c) || c.isDigit()

    private fun isLineTerminator(c: Char): Boolean = c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029'
}
