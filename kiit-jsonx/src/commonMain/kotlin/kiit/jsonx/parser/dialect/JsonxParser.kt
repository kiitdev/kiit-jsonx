/** url: www.kiit.dev */
package kiit.jsonx.parser.dialect

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.options.ParseOptions
import kiit.jsonx.parser.core.TokenType
import kiit.jsonx.parser.json.JsonLexer
import kiit.jsonx.parser.json5.Json5Parser
import kiit.result.Failure
import kiit.result.Success

/**
 * Assembles the jsonx dialect grammar on top of [JsonxLexer]'s tokens, extending [Json5Parser]
 * for jsonx's one grammar addition: `@name(args)` tag literals.
 *
 * 1. Only [parseValue] is overridden, to route a [TokenType.JTag] token to [parseTag]. Every
 *    other production (objects, arrays, trailing commas, unquoted/single-quoted/triple-quoted
 *    keys and strings) is inherited unchanged from [Json5Parser]/`JsonParser`.
 * 2. Triple-quoted strings need no parser-side handling at all: [JsonxLexer] already tokenizes
 *    them as an ordinary [TokenType.JString], and [JsonxDecoder] (injected as the `decoder`)
 *    already decodes the `"""`-prefixed raw text correctly, so `parseString` is unmodified too.
 * 3. This is parsing only, not resolution: a `JsonXTagged` node is a leaf here, its `args` are
 *    plain values. Turning `@env('DB_HOST')` into an actual resolved value is a transform-stage
 *    concern (Phase 3), entirely out of scope for this class.
 * 4. `parseValue`/[parseTag] stay `protected open`, the same extension-point pattern used
 *    throughout this parser family, rather than a wider public surface — see the jsonx plan's
 *    note on `kiit-views` reuse for why a broader public API is deliberately not committed to yet.
 */
open class JsonxParser(lexer: JsonLexer, options: ParseOptions = ParseOptions()) :
    Json5Parser(lexer, options, JsonxDecoder()) {
    override fun parseValue(): JsonXElement = if (current.type == TokenType.JTag) parseTag() else super.parseValue()

    /**
     * `@name(args)` or `@namespace.name(args)`, always parenthesized (even zero-arg: `@name()`)
     * — jsonx has no bare-tag syntax. Trailing commas in the argument list are allowed, same as
     * everywhere else in this dialect. Args are ordinary values, parsed via [parseValue], so a
     * tag can appear inside another tag's arguments (simple tags nesting inside simple tags is
     * fine at the parse-syntax level here; whether that's *semantically* allowed is a `TagKind`
     * concern for Phase 3, not this class).
     */
    protected open fun parseTag(): JsonXTagged {
        val name = advance().text.removePrefix("@")
        expect(TokenType.JLParen)

        val args = mutableListOf<JsonXElement>()
        while (current.type != TokenType.JRParen) {
            args.add(parseValue())
            when (current.type) {
                TokenType.JComma -> advance()
                TokenType.JRParen -> Unit
                else -> parseError("expected ',' or ')'")
            }
        }
        advance() // ')'
        return JsonXTagged(name, args)
    }

    companion object {
        /** Parses [text] as the jsonx dialect, converting any [JsonXParseException] into a [JsonXResult.Failure]. */
        fun parse(text: CharSequence, options: ParseOptions = ParseOptions()): JsonXResult<JsonXElement> =
            try {
                Success(JsonxParser(JsonxLexer(text), options).parseDocument())
            } catch (e: JsonXParseException) {
                Failure(e.error, e.status)
            }
    }
}
