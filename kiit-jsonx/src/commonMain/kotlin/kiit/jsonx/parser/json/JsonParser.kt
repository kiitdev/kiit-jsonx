/** url: www.kiit.dev */
package kiit.jsonx.parser.json

import kiit.codes.Err
import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXBoolean
import kiit.jsonx.element.JsonXElement.JsonXNull
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.error.JsonXError
import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.options.ParseOptions
import kiit.jsonx.parser.core.DuplicateKeyTracker
import kiit.jsonx.parser.core.SourcePosition
import kiit.jsonx.parser.core.Token
import kiit.jsonx.parser.core.TokenType
import kiit.result.Failure
import kiit.result.Success

/**
 * Recursive-descent parser consuming [JsonLexer] tokens into a [JsonXElement] tree.
 *
 * Like [JsonLexer], this signals failure by throwing [JsonXParseException] internally rather
 * than threading [JsonXResult] through every recursive call. See that class's KDoc for why.
 * [Companion.parse] is the actual public boundary: the one place that catches
 * [JsonXParseException] and converts it into a real [JsonXResult.Failure].
 *
 * Delegates decoding raw [Token.text] into real values to [JsonDecoder]. Tree-shape/grammar
 * and raw-text decoding are independent concerns, and [Json5Parser] (Milestone 2.4) will likely
 * need its own decoder (e.g. for hex numbers) without needing its own copy of this class's
 * grammar logic.
 *
 * `open`/`protected` throughout for [Json5Parser] to extend rather than reimplement, same
 * extension-point philosophy as [JsonLexer].
 */
open class JsonParser(
    private val lexer: JsonLexer,
    private val options: ParseOptions = ParseOptions(),
    protected val decoder: JsonDecoder = JsonDecoder(),
) {
    protected var current: Token = lexer.nextToken()
        private set

    /** Current object/array nesting depth. See [enterNesting]. */
    protected var depth: Int = 0

    /** Parses the whole document. Empty (or whitespace-only) input yields an empty [JsonXObject]. */
    fun parseDocument(): JsonXElement {
        if (current.type == TokenType.JEndOfInput) return JsonXObject(LinkedHashMap())

        val value = parseValue()
        if (current.type != TokenType.JEndOfInput) {
            parseError("unexpected trailing content after value")
        }
        return value
    }

    protected open fun parseValue(): JsonXElement =
        when (current.type) {
            TokenType.JLBrace -> parseObject()
            TokenType.JLBracket -> parseArray()
            TokenType.JString -> parseString()
            TokenType.JNumber -> parseNumber()
            TokenType.JTrue -> {
                advance()
                JsonXBoolean(true)
            }
            TokenType.JFalse -> {
                advance()
                JsonXBoolean(false)
            }
            TokenType.JNull -> {
                advance()
                JsonXNull
            }
            else -> parseError("expected a value, found '${current.text}'")
        }

    protected open fun parseObject(): JsonXObject {
        enterNesting()
        try {
            expect(TokenType.JLBrace)
            val entries = LinkedHashMap<String, JsonXElement>()

            // "{}": an empty object, no entries to read.
            if (current.type == TokenType.JRBrace) {
                advance()
                return JsonXObject(entries)
            }

            val tracker = DuplicateKeyTracker()
            // One "key: value" pair per iteration; every branch below either consumes a token
            // (comma continues, '}' returns) or throws, so this always terminates.
            while (true) {
                if (current.type != TokenType.JString) parseError("expected a string key")
                val key = decoder.decodeString(advance().text)
                expect(TokenType.JColon)
                putEntry(entries, tracker, key, parseValue())

                when (current.type) {
                    TokenType.JComma -> advance()
                    TokenType.JRBrace -> {
                        advance()
                        return JsonXObject(entries)
                    }
                    else -> parseError("expected ',' or '}'")
                }
            }
        } finally {
            depth--
        }
    }

    protected open fun parseArray(): JsonXArray {
        enterNesting()
        try {
            expect(TokenType.JLBracket)
            val items = mutableListOf<JsonXElement>()

            // "[]": an empty array, no items to read.
            if (current.type == TokenType.JRBracket) {
                advance()
                return JsonXArray(items)
            }

            // One value per iteration; every branch below either consumes a token (comma
            // continues, ']' returns) or throws, so this always terminates.
            while (true) {
                items.add(parseValue())
                when (current.type) {
                    TokenType.JComma -> advance()
                    TokenType.JRBracket -> {
                        advance()
                        return JsonXArray(items)
                    }
                    else -> parseError("expected ',' or ']'")
                }
            }
        } finally {
            depth--
        }
    }

    /**
     * Tracks recursion [depth] on entry to [parseObject]/[parseArray], failing cleanly with a
     * normal [parseError] past [MAX_NESTING_DEPTH], instead of risking an uncatchable
     * `StackOverflowError` on pathologically deep input, which would break the "always returns
     * a [JsonXResult]" contract at [Companion.parse]. Each caller decrements [depth] again in
     * its own `finally` block.
     */
    protected fun enterNesting() {
        depth++
        if (depth > MAX_NESTING_DEPTH) parseError("exceeded max nesting depth of $MAX_NESTING_DEPTH")
    }

    protected open fun parseString(): JsonXString = JsonXString(decoder.decodeString(advance().text))

    protected open fun parseNumber(): JsonXNumber = decoder.decodeNumber(advance().text)

    /**
     * Applies [ParseOptions.duplicateKeyPolicy] when [key] repeats within one object.
     * [CollectIntoArray][ParseOptions.DuplicateKeyPolicy.CollectIntoArray] is inherently
     * ambiguous against a key whose *first* value is itself a real array: both end up
     * indistinguishable as a flat array of values. Accepted as a known limitation of the policy,
     * not something this method can resolve.
     */
    protected open fun putEntry(
        entries: LinkedHashMap<String, JsonXElement>,
        tracker: DuplicateKeyTracker,
        key: String,
        value: JsonXElement,
    ) {
        if (!tracker.offer(key)) {
            entries[key] = value
            return
        }
        when (options.duplicateKeyPolicy) {
            ParseOptions.DuplicateKeyPolicy.Error -> parseError("duplicate key '$key'")
            ParseOptions.DuplicateKeyPolicy.LastWins -> entries[key] = value
            ParseOptions.DuplicateKeyPolicy.FirstWins -> Unit
            ParseOptions.DuplicateKeyPolicy.CollectIntoArray -> {
                val existing = entries.getValue(key)
                entries[key] =
                    if (existing is JsonXArray) {
                        JsonXArray(existing.items + value)
                    } else {
                        JsonXArray(listOf(existing, value))
                    }
            }
        }
    }

    protected fun advance(): Token {
        val token = current
        current = lexer.nextToken()
        return token
    }

    protected fun expect(type: TokenType) {
        if (current.type != type) parseError("expected $type, found ${current.type}")
        advance()
    }

    protected fun parseError(message: String): Nothing = parseError(message, current.start)

    protected fun parseError(message: String, position: SourcePosition): Nothing {
        throw JsonXParseException(
            JsonXError(Err.of(message), line = position.line, column = position.column, offset = position.offset),
        )
    }

    companion object {
        /** Max object/array nesting depth, matching common practice (e.g. Jackson's default). See [enterNesting]. */
        const val MAX_NESTING_DEPTH: Int = 1000

        /** Parses [text] as strict JSON, converting any [JsonXParseException] into a [JsonXResult.Failure]. */
        fun parse(text: CharSequence, options: ParseOptions = ParseOptions()): JsonXResult<JsonXElement> =
            try {
                Success(JsonParser(JsonLexer(text), options).parseDocument())
            } catch (e: JsonXParseException) {
                Failure(e.error, e.status)
            }
    }
}
