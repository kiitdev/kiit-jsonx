/** url: www.kiit.dev */
package kiit.jsonx.parser.json5

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.options.ParseOptions
import kiit.jsonx.parser.core.DuplicateKeyTracker
import kiit.jsonx.parser.core.TokenType
import kiit.jsonx.parser.json.JsonLexer
import kiit.jsonx.parser.json.JsonParser
import kiit.result.Failure
import kiit.result.Success

/**
 * Assembles the full JSON5 grammar on top of [Json5Lexer]'s tokens, extending [JsonParser] for
 * two relaxations: trailing commas in objects and arrays, and unquoted identifier-style object
 * keys (in addition to quoted string keys).
 *
 * 1. Only [parseObject]/[parseArray] are overridden. [JsonParser.parseValue]/`parseString`/
 *    `parseNumber` are inherited unchanged.
 * 2. Number/string decoding already comes out JSON5-correct because [Json5Decoder] is injected
 *    as the `decoder`, not because this class does anything differently.
 * 3. A bare identifier is only valid in key position: `parseValue`'s inherited fallthrough
 *    already rejects one anywhere else, exactly as JSON5 requires.
 */
open class Json5Parser(lexer: JsonLexer, options: ParseOptions = ParseOptions()) :
    JsonParser(lexer, options, Json5Decoder()) {
    /**
     * Same shape as [JsonParser.parseObject], but the loop condition itself (not an explicit
     * branch per separator) is what makes a trailing comma legal: consuming a comma and then
     * finding `}` just ends the loop instead of erroring. This wouldn't be safe for strict JSON,
     * where a trailing comma must be rejected, but JSON5 explicitly allows it.
     */
    override fun parseObject(): JsonXObject {
        enterNesting()
        try {
            expect(TokenType.JLBrace)
            val entries = LinkedHashMap<String, JsonXElement>()
            val tracker = DuplicateKeyTracker()

            while (current.type != TokenType.JRBrace) {
                val key = readKey()
                expect(TokenType.JColon)
                putEntry(entries, tracker, key, parseValue())

                when (current.type) {
                    TokenType.JComma -> advance()
                    TokenType.JRBrace -> Unit
                    else -> parseError("expected ',' or '}'")
                }
            }
            advance() // '}'
            return JsonXObject(entries)
        } finally {
            depth--
        }
    }

    override fun parseArray(): JsonXArray {
        enterNesting()
        try {
            expect(TokenType.JLBracket)
            val items = mutableListOf<JsonXElement>()

            while (current.type != TokenType.JRBracket) {
                items.add(parseValue())
                when (current.type) {
                    TokenType.JComma -> advance()
                    TokenType.JRBracket -> Unit
                    else -> parseError("expected ',' or ']'")
                }
            }
            advance() // ']'
            return JsonXArray(items)
        } finally {
            depth--
        }
    }

    /** An object key: a quoted string (decoded normally) or a bare identifier (used as-is, no decoding needed). */
    private fun readKey(): String =
        when (current.type) {
            TokenType.JString -> decoder.decodeString(advance().text)
            TokenType.JIdentifier -> advance().text
            else -> parseError("expected a string or identifier key")
        }

    companion object {
        /** Parses [text] as JSON5, converting any [JsonXParseException] into a [JsonXResult.Failure]. */
        fun parse(text: CharSequence, options: ParseOptions = ParseOptions()): JsonXResult<JsonXElement> =
            try {
                Success(Json5Parser(Json5Lexer(text), options).parseDocument())
            } catch (e: JsonXParseException) {
                Failure(e.error, e.status)
            }
    }
}
