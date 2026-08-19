package kiit.jsonx.parser.json

import kiit.jsonx.error.JsonXParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// =================================================================================================
// JsonLexerTest: JsonLexer tokens, string escapes, numeric grammar
// =================================================================================================
class JsonLexerTest {
    private fun tokenize(text: String): List<Token> {
        val lexer = JsonLexer(text)
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = lexer.nextToken()
            tokens.add(token)
            if (token.type == TokenType.JEndOfInput) return tokens
        }
    }

    private fun singleToken(text: String): Token = tokenize(text).first()

    // --- punctuation -----------------------------------------------------------------------

    @Test
    fun punctuation_tokensRecognized() {
        val tokens = tokenize("{}[]:,")
        assertEquals(
            listOf(
                TokenType.JLBrace,
                TokenType.JRBrace,
                TokenType.JLBracket,
                TokenType.JRBracket,
                TokenType.JColon,
                TokenType.JComma,
                TokenType.JEndOfInput,
            ),
            tokens.map { it.type },
        )
    }

    @Test
    fun whitespace_isSkippedBetweenTokens() {
        val tokens = tokenize(" \t{\n  }\r\n")
        assertEquals(
            listOf(TokenType.JLBrace, TokenType.JRBrace, TokenType.JEndOfInput),
            tokens.map { it.type },
        )
    }

    @Test
    fun emptyInput_yieldsOnlyEndOfInput() {
        assertEquals(listOf(TokenType.JEndOfInput), tokenize("").map { it.type })
    }

    @Test
    fun endOfInput_isStableAcrossRepeatedCalls() {
        val lexer = JsonLexer("")
        val first = lexer.nextToken()
        val second = lexer.nextToken()
        assertEquals(first, second)
    }

    // --- keywords ----------------------------------------------------------------------------

    @Test
    fun keywords_trueFalseNull_recognized() {
        val trueToken = singleToken("true")
        assertEquals(TokenType.JTrue, trueToken.type)
        assertEquals("true", trueToken.text)

        val falseToken = singleToken("false")
        assertEquals(TokenType.JFalse, falseToken.type)
        assertEquals("false", falseToken.text)

        val nullToken = singleToken("null")
        assertEquals(TokenType.JNull, nullToken.type)
        assertEquals("null", nullToken.text)
    }

    @Test
    fun keyword_malformed_fails() {
        assertFailsWith<JsonXParseException> { JsonLexer("tru3").nextToken() }
    }

    @Test
    fun keyword_unrecognizedLetterStart_fails() {
        // starts with a letter (routed into readKeyword) but doesn't match true/false/null's
        // first character at all — a different failure path than a partial/malformed match.
        assertFailsWith<JsonXParseException> { JsonLexer("xyz").nextToken() }
    }

    // --- strings -------------------------------------------------------------------------------

    @Test
    fun string_plain() {
        val source = "\"hello\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun string_emptyString() {
        val source = "\"\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun string_standardEscapes_capturesRawText() {
        val source = """"\"\\\/\b\f\n\r\t""""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun string_unicodeEscape_capturesRawText() {
        val source = "\"\\u0041\\u00e9\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun string_unicodeSurrogatePair_capturesRawText() {
        val source = "\"\\uD83D\\uDE00\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun string_unterminated_fails() {
        assertFailsWith<JsonXParseException> { JsonLexer("\"abc").nextToken() }
    }

    @Test
    fun string_rawControlCharacter_fails() {
        assertFailsWith<JsonXParseException> { JsonLexer("\"a\nb\"").nextToken() }
    }

    @Test
    fun string_invalidEscape_fails() {
        assertFailsWith<JsonXParseException> { JsonLexer("\"\\x\"").nextToken() }
    }

    @Test
    fun string_truncatedUnicodeEscape_fails() {
        assertFailsWith<JsonXParseException> { JsonLexer("\"\\u12\"").nextToken() }
    }

    // --- numbers -------------------------------------------------------------------------------

    @Test
    fun number_zero() {
        val token = singleToken("0")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("0", token.text)
    }

    @Test
    fun number_positiveInteger() {
        val token = singleToken("42")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("42", token.text)
    }

    @Test
    fun number_negativeInteger() {
        val token = singleToken("-17")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("-17", token.text)
    }

    @Test
    fun number_decimal() {
        val token = singleToken("3.14")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("3.14", token.text)
    }

    @Test
    fun number_exponentLowercase() {
        val token = singleToken("1e3")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("1e3", token.text)
    }

    @Test
    fun number_exponentUppercaseWithSign() {
        val token = singleToken("2E+2")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("2E+2", token.text)
    }

    @Test
    fun number_exponentNegative() {
        val token = singleToken("5e-2")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("5e-2", token.text)
    }

    @Test
    fun number_fractionalWithExponent() {
        val token = singleToken("1.5e2")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("1.5e2", token.text)
    }

    @Test
    fun number_soleZero_isACompleteNumberToken() {
        // strict JSON forbids leading zeros ("01" is invalid) — that grammar rule lives at the
        // digit level (readIntegerPart), not as a lexer-vs-parser boundary concern; a lone "0" is
        // simply a complete, valid number token on its own.
        val token = singleToken("0")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("0", token.text)
    }

    @Test
    fun number_missingDigitAfterDecimalPoint_fails() {
        assertFailsWith<JsonXParseException> { JsonLexer("1.").nextToken() }
    }

    @Test
    fun number_missingDigitInExponent_fails() {
        assertFailsWith<JsonXParseException> { JsonLexer("1e").nextToken() }
    }

    @Test
    fun number_bareMinusSign_fails() {
        assertFailsWith<JsonXParseException> { JsonLexer("-").nextToken() }
    }

    @Test
    fun number_veryLongDigitSequence_capturedAsRawText() {
        // the lexer no longer interprets numeric value at all (no Long/Double, no overflow
        // handling) — that's the parser's job (Milestone 2.2), reading Token.text itself.
        val source = "99999999999999999999999999"
        val token = singleToken(source)
        assertEquals(TokenType.JNumber, token.type)
        assertEquals(source, token.text)
    }

    // --- errors --------------------------------------------------------------------------------

    @Test
    fun unexpectedCharacter_fails() {
        assertFailsWith<JsonXParseException> { JsonLexer("#").nextToken() }
    }

    @Test
    fun lexError_carriesSourcePosition() {
        val exception = assertFailsWith<JsonXParseException> { JsonLexer("  #").nextToken() }
        assertEquals(1, exception.error.line)
        assertEquals(3, exception.error.column)
        assertEquals(2, exception.error.offset)
    }

    // --- full document ---------------------------------------------------------------------------

    @Test
    fun fullDocument_tokenizesInOrder() {
        val source = """{"a": 1, "b": [true, false, null]}"""
        val types = tokenize(source).map { it.type }
        assertEquals(
            listOf(
                TokenType.JLBrace,
                TokenType.JString,
                TokenType.JColon,
                TokenType.JNumber,
                TokenType.JComma,
                TokenType.JString,
                TokenType.JColon,
                TokenType.JLBracket,
                TokenType.JTrue,
                TokenType.JComma,
                TokenType.JFalse,
                TokenType.JComma,
                TokenType.JNull,
                TokenType.JRBracket,
                TokenType.JRBrace,
                TokenType.JEndOfInput,
            ),
            types,
        )
    }
}
