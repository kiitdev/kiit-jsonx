/** url: www.kiit.dev */
package kiit.jsonx.parser.jsonx

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.options.ParseOptions
import kiit.jsonx.parser.core.SourcePosition
import kiit.jsonx.parser.core.TokenType
import kiit.jsonx.parser.json.JsonLexer
import kiit.jsonx.parser.json5.Json5Parser
import kiit.jsonx.tags.ExperimentalJsonxTagApi
import kiit.result.Failure
import kiit.result.Success

/**
 * Assembles the jsonx dialect grammar on top of [JsonXLexer]'s tokens, extending [Json5Parser]
 * for jsonx's one grammar addition: `@name(args)` tag literals.
 *
 * 1. Only [parseValue] is overridden, to route a [TokenType.JTag] token to [parseTag]. Every
 *    other production (objects, arrays, trailing commas, unquoted/single-quoted/triple-quoted
 *    keys and strings) is inherited unchanged from [Json5Parser]/`JsonParser`.
 * 2. Triple-quoted strings need no parser-side handling at all: [JsonXLexer] already tokenizes
 *    them as an ordinary [TokenType.JString], and [JsonXDecoder] (injected as the `decoder`)
 *    already decodes the `"""`-prefixed raw text correctly, so `parseString` is unmodified too.
 * 3. `JsonXTagged` is the universal parse-time representation of every `@name(args)` occurrence,
 *    built unconditionally in [parseTag]. What happens next depends entirely on
 *    [ParseOptions.tagRegistry]: a registered name (`@table`, or any externally-registered tag)
 *    is resolved immediately and its result replaces the tagged node right there in the tree; an
 *    unregistered name (`@env`, `@ref` — deliberately never registered, see `TagRegistry`'s
 *    KDoc) is left as a plain `JsonXTagged` leaf for the transform pipeline to pick up later.
 *    `TagHandler.resolve` itself does nothing but convert already-parsed `args` into a
 *    replacement value; it has no say in whether or when it's called.
 * 4. `parseValue`/[parseTag] stay `protected open`, the same extension-point pattern used
 *    throughout this parser family, rather than a wider public surface — see the jsonx plan's
 *    note on `kiit-views` reuse for why a broader public API is deliberately not committed to yet.
 */
open class JsonXParser(lexer: JsonLexer, options: ParseOptions = ParseOptions()) :
    Json5Parser(lexer, options, JsonXDecoder()) {
    override fun parseValue(): JsonXElement = if (current.type == TokenType.JTag) parseTag() else super.parseValue()

    /**
     * `@name(args)` or `@namespace.name(args)`, always parenthesized (even zero-arg: `@name()`)
     * — jsonx has no bare-tag syntax. Trailing commas in the argument list are allowed, same as
     * everywhere else in this dialect. Args are ordinary values, parsed via [parseValue], so a
     * tag can appear inside another tag's arguments (e.g. a `@table` cell using `@env(...)`) with
     * no special-casing needed here.
     */
    @OptIn(ExperimentalJsonxTagApi::class)
    protected open fun parseTag(): JsonXElement {
        val tagStart = current.start
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

        return resolveEagerly(name, args, tagStart)
    }

    /** Splices in a registered handler's result immediately, or leaves an unregistered tag as-is. */
    @OptIn(ExperimentalJsonxTagApi::class)
    private fun resolveEagerly(name: String, args: List<JsonXElement>, tagStart: SourcePosition): JsonXElement {
        val handler = options.tagRegistry.find(name) ?: return JsonXTagged(name, args)
        return when (val result = handler.resolve(args)) {
            is Success -> result.value
            is Failure -> parseError(result.error, result.status, tagStart)
        }
    }

    companion object {
        /** Parses [text] as the jsonx dialect, converting any [JsonXParseException] into a [JsonXResult.Failure]. */
        fun parse(text: CharSequence, options: ParseOptions = ParseOptions()): JsonXResult<JsonXElement> =
            try {
                Success(JsonXParser(JsonXLexer(text), options).parseDocument())
            } catch (e: JsonXParseException) {
                Failure(e.error, e.status)
            }
    }
}
