/** url: www.kiit.dev */
package kiit.jsonx.parser.json

import kiit.jsonx.element.JsonXElement.JsonXNumber

/**
 * Decodes raw [JsonLexer]-produced [Token.text] slices into real values — a string token's
 * quotes and escape sequences, a number token's digits.
 *
 * Pure functions of their input text: no dependency on lexer/parser state, since [JsonLexer]
 * already validated the text is well-formed before a [JsonParser] ever calls this. Kept separate
 * from [JsonParser] so tree-shape/grammar and raw-text decoding are two independent concerns.
 *
 * `open` so a future `Json5Parser` (Milestone 2.4) can supply its own decoder for JSON5-specific
 * rules (e.g. hex numbers) without touching [JsonParser]'s tree-shape logic.
 */
open class JsonDecoder {
    /** Decodes a raw string slice (quotes + escapes) into its real value. */
    open fun decodeString(rawText: String): String {
        val inner = rawText.substring(1, rawText.length - 1)
        val decoded = StringBuilder(inner.length)
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c != '\\') {
                decoded.append(c)
                i++
                continue
            }
            i++
            i = decodeEscape(inner, i, decoded)
        }
        return decoded.toString()
    }

    /** Decodes one escape sequence starting at [index] (the leading `\` already skipped). Returns the next index. */
    protected open fun decodeEscape(inner: String, index: Int, out: StringBuilder): Int {
        when (val escaped = inner[index]) {
            '"', '\\', '/' -> out.append(escaped)
            'b' -> out.append('\b')
            'f' -> out.append('\u000C')
            'n' -> out.append('\n')
            'r' -> out.append('\r')
            't' -> out.append('\t')
            'u' -> {
                out.append(inner.substring(index + 1, index + 5).toInt(radix = 16).toChar())
                return index + 5
            }
            else -> error("unreachable: JsonLexer already validated this escape")
        }
        return index + 1
    }

    /** Decodes a raw number slice, falling back to [Double] on [Long] overflow. */
    open fun decodeNumber(rawText: String): JsonXNumber {
        val isDouble = rawText.any { it == '.' || it == 'e' || it == 'E' }
        if (!isDouble) rawText.toLongOrNull()?.let { return JsonXNumber.of(it) }
        return JsonXNumber.of(rawText.toDouble())
    }
}
