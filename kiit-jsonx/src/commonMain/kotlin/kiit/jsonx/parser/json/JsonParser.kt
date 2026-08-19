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
import kiit.jsonx.parser.state.DuplicateKeyTracker
import kiit.jsonx.parser.state.SourcePosition
import kiit.result.Failure
import kiit.result.Success

/**
 * Recursive-descent parser consuming [JsonLexer] tokens into a [JsonXElement] tree.
 *
 * Like [JsonLexer], this signals failure by throwing [JsonXParseException] internally rather
 * than threading [JsonXResult] through every recursive call — see that class's KDoc for why.
 * [Companion.parse] is the actual public boundary: the one place that catches
 * [JsonXParseException] and converts it into a real [JsonXResult.Failure].
 *
 * Also owns decoding raw [Token.text] into real values — [JsonLexer] validates a string's
 * escapes and a number's grammar but deliberately never decodes them (see [Token]'s KDoc); that
 * happens here, in [decodeString]/[decodeNumber], which can assume well-formed input since the
 * lexer already rejected anything malformed.
 *
 * `open`/`protected` throughout for [Json5Parser] (Milestone 2.4) to extend rather than
 * reimplement — same extension-point philosophy as [JsonLexer].
 */
open class JsonParser(private val lexer: JsonLexer, private val options: ParseOptions = ParseOptions()) {
    protected var current: Token = lexer.nextToken()
        private set

    /** Current object/array nesting depth — see [withNestingGuard]. */
    private var depth: Int = 0

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

            // "{}" — an empty object, no entries to read.
            if (current.type == TokenType.JRBrace) {
                advance()
                return JsonXObject(entries)
            }

            val tracker = DuplicateKeyTracker()
            // One "key: value" pair per iteration; every branch below either consumes a token
            // (comma continues, '}' returns) or throws, so this always terminates.
            while (true) {
                if (current.type != TokenType.JString) parseError("expected a string key")
                val key = decodeString(advance().text)
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

            // "[]" — an empty array, no items to read.
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
     * normal [parseError] past [MAX_NESTING_DEPTH] — instead of risking an uncatchable
     * `StackOverflowError` on pathologically deep input, which would break the "always returns
     * a [JsonXResult]" contract at [Companion.parse]. Each caller decrements [depth] again in
     * its own `finally` block.
     */
    private fun enterNesting() {
        depth++
        if (depth > MAX_NESTING_DEPTH) parseError("exceeded max nesting depth of $MAX_NESTING_DEPTH")
    }

    protected open fun parseString(): JsonXString = JsonXString(decodeString(advance().text))

    protected open fun parseNumber(): JsonXNumber = decodeNumber(advance().text)

    /**
     * Applies [ParseOptions.duplicateKeyPolicy] when [key] repeats within one object.
     * [CollectIntoArray][ParseOptions.DuplicateKeyPolicy.CollectIntoArray] is inherently
     * ambiguous against a key whose *first* value is itself a real array — both end up
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

    /** Decodes a raw [JsonLexer]-produced string slice (quotes + escapes) into its real value. */
    protected open fun decodeString(rawText: String): String {
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
    private fun decodeEscape(inner: String, index: Int, out: StringBuilder): Int {
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

    /** Decodes a raw [JsonLexer]-produced number slice, falling back to [Double] on [Long] overflow. */
    protected open fun decodeNumber(rawText: String): JsonXNumber {
        val isDouble = rawText.any { it == '.' || it == 'e' || it == 'E' }
        if (!isDouble) rawText.toLongOrNull()?.let { return JsonXNumber.of(it) }
        return JsonXNumber.of(rawText.toDouble())
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
        /** Max object/array nesting depth — see [enterNesting]. Matches common practice (e.g. Jackson's default). */
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
