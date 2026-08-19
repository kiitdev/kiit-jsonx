/** url: www.kiit.dev */
package kiit.jsonx.parser.json5

import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.parser.json.JsonDecoder

/**
 * Decodes raw [Json5Lexer]-produced `Token.text` slices for JSON5's relaxations: hex numbers,
 * `Infinity`/`NaN`, and the wider string escape set (line continuation, `\xHH`, `\v`, `\0`, and
 * `NonEscapeCharacter` passthrough). Everything not overridden here (plain decimal numbers, the
 * standard escapes) reuses [JsonDecoder] as-is, since `Json5Lexer` produces the same raw shape
 * for those.
 */
open class Json5Decoder : JsonDecoder() {
    override fun decodeNumber(rawText: String): JsonXNumber {
        val negative = rawText.startsWith("-")
        val unsigned = rawText.removePrefix("+").removePrefix("-")

        if (unsigned == "Infinity") {
            return JsonXNumber.of(if (negative) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY)
        }
        if (unsigned == "NaN") {
            return JsonXNumber.of(Double.NaN)
        }
        if (unsigned.startsWith("0x") || unsigned.startsWith("0X")) {
            return decodeHex(unsigned.substring(2), negative)
        }

        return super.decodeNumber(rawText)
    }

    // Kotlin's Long/Double parsing already accepts a leading '+' and both ".5"/"5.", so plain
    // decimals fall through to super.decodeNumber unmodified — nothing JSON5-specific to add.
    private fun decodeHex(hexDigits: String, negative: Boolean): JsonXNumber {
        hexDigits.toLongOrNull(radix = 16)?.let { return JsonXNumber.of(if (negative) -it else it) }

        // Falls back to a Double approximation on overflow, same tradeoff JsonDecoder makes for
        // an oversized decimal integer. No java.math.BigInteger here, this module stays KMP-common.
        var magnitude = 0.0
        for (c in hexDigits) magnitude = magnitude * 16 + hexDigitValue(c)
        return JsonXNumber.of(if (negative) -magnitude else magnitude)
    }

    private fun hexDigitValue(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> error("unreachable: Json5Lexer already validated this is a hex digit")
        }

    /**
     * JSON5's escape grammar, replacing [JsonDecoder]'s strict-JSON version entirely rather than
     * calling `super`: a line terminator here is a line continuation and decodes to nothing, `\v`
     * and `\0` are new, and any character not otherwise recognized decodes to itself
     * (`NonEscapeCharacter`) instead of being a lex-time error, since `Json5Lexer` already
     * accepted it.
     */
    override fun decodeEscape(inner: String, index: Int, out: StringBuilder): Int {
        val c = inner[index]

        singleCharEscapes[c]?.let {
            out.append(it)
            return index + 1
        }

        return when {
            c == '\r' -> if (index + 1 < inner.length && inner[index + 1] == '\n') index + 2 else index + 1
            c == '\n' || c == '\u2028' || c == '\u2029' -> index + 1
            c == 'x' -> {
                out.append(inner.substring(index + 1, index + 3).toInt(radix = 16).toChar())
                index + 3
            }
            c == 'u' -> {
                out.append(inner.substring(index + 1, index + 5).toInt(radix = 16).toChar())
                index + 5
            }
            // NonEscapeCharacter: any character JSON5 doesn't special-case decodes to itself.
            else -> {
                out.append(c)
                index + 1
            }
        }
    }

    companion object {
        private val singleCharEscapes: Map<Char, Char> =
            mapOf(
                'v' to '\u000B',
                '0' to '\u0000',
                'b' to '\b',
                'f' to '\u000C',
                'n' to '\n',
                'r' to '\r',
                't' to '\t',
            )
    }
}
