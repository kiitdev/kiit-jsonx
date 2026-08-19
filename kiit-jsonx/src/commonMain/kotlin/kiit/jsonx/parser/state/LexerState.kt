/** url: www.kiit.dev */
package kiit.jsonx.parser.state

/**
 * Tracks offset/line/column position while walking a source [CharSequence] one character at a
 * time. Shared by every dialect's lexer (JSON, JSON5, jsonx, JsonL) — each layers its own token
 * grammar on top rather than re-implementing position tracking.
 *
 * State is exposed through public read-only properties and methods rather than hidden behind a
 * `private`/`internal`-only surface, so a future consumer outside this module (a planned
 * `kiit-views` module reusing jsonx's value/tag-level lexing) can build on this directly without
 * requiring a rework of this class.
 *
 * Line-terminator handling follows the ECMAScript/JSON5 definition of a line terminator: `\n`,
 * `\r`, `\r\n` (counted as a single terminator), U+2028 (LINE SEPARATOR), and U+2029 (PARAGRAPH
 * SEPARATOR).
 */
class LexerState(private val text: CharSequence) {
    var offset: Int = 0
        private set

    var line: Int = 1
        private set

    var column: Int = 1
        private set

    val length: Int get() = text.length

    fun isAtEnd(): Boolean = offset >= length

    /** Returns the character [lookahead] positions past the current offset, or null past the end. */
    fun peek(lookahead: Int = 0): Char? {
        val index = offset + lookahead
        return if (index < length) text[index] else null
    }

    /**
     * Consumes and returns the current character, advancing [offset] and updating [line]/
     * [column]. A `\r\n` pair is consumed together as a single line terminator — the `\n` half
     * of a pair is never left for a separate [advance] call to consume on its own.
     */
    fun advance(): Char {
        check(!isAtEnd()) { "advance() called at end of input" }
        val current = text[offset]
        offset++
        when (current) {
            '\r' -> {
                if (peek() == '\n') {
                    offset++
                }
                line++
                column = 1
            }
            '\n', ' ', ' ' -> {
                line++
                column = 1
            }
            else -> column++
        }
        return current
    }

    /** Captures the current position as an immutable [SourcePosition]. */
    fun snapshot(): SourcePosition = SourcePosition(offset, line, column)

    /** Returns the raw source text from [from] (inclusive) to [to] (exclusive), defaulting to the current [offset]. */
    fun slice(from: Int, to: Int = offset): String = text.subSequence(from, to).toString()
}
