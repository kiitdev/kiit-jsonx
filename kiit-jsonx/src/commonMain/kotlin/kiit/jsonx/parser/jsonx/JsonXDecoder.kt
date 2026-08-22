/** url: www.kiit.dev */
package kiit.jsonx.parser.jsonx

import kiit.jsonx.parser.json5.Json5Decoder

/**
 * Decodes raw [JsonXLexer]-produced `Token.text` slices for the jsonx dialect's one addition
 * beyond JSON5: triple-quoted strings. Numbers need no dialect-specific decoding, so
 * [decodeNumber] is inherited from [Json5Decoder] unchanged.
 *
 * The two TOML-aligned rules that are genuinely decode-time (not lex-time validation) live here:
 * trimming a newline immediately after the opening delimiter, and `\`-line-continuation eating
 * the following line's leading whitespace (JSON5's own line continuation only eats the
 * terminator itself; this is a jsonx-specific widening, kept local to this class rather than
 * touching [Json5Decoder]).
 */
open class JsonXDecoder : Json5Decoder() {
    override fun decodeString(rawText: String): String {
        if (!rawText.startsWith("\"\"\"")) return super.decodeString(rawText)

        val inner = rawText.substring(3, rawText.length - 3)
        val content = inner.substring(leadingNewlineLength(inner))

        val decoded = StringBuilder(content.length)
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c != '\\') {
                decoded.append(c)
                i++
                continue
            }
            i++
            i = decodeTripleQuoteEscape(content, i, decoded)
        }
        return decoded.toString()
    }

    /** Length of a single leading line terminator (`\r\n` counts as one), or 0 if [inner] doesn't start with one. */
    private fun leadingNewlineLength(inner: String): Int =
        when {
            inner.startsWith("\r\n") -> 2
            inner.startsWith("\n") || inner.startsWith("\r") -> 1
            inner.startsWith("\u2028") || inner.startsWith("\u2029") -> 1
            else -> 0
        }

    /**
     * Same escape grammar as [Json5Decoder.decodeEscape] (delegated to via `super` for every
     * case except this one), except a line-terminator escape also eats the following line's
     * leading whitespace, matching TOML's `\`-line-ending-trim rule for multiline strings.
     */
    private fun decodeTripleQuoteEscape(content: String, index: Int, out: StringBuilder): Int {
        val c = content[index]
        if (!isLineTerminatorChar(c)) return super.decodeEscape(content, index, out)

        var next = index + if (c == '\r' && index + 1 < content.length && content[index + 1] == '\n') 2 else 1
        while (next < content.length && isLineContinuationWhitespace(content[next])) next++
        return next
    }

    private fun isLineTerminatorChar(c: Char): Boolean = c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029'

    private fun isLineContinuationWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || isLineTerminatorChar(c)
}
