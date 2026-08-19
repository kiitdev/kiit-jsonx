package kiit.jsonx.parser.json5

import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.parser.core.Token
import kiit.jsonx.parser.core.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// =================================================================================================
// Json5LexerTest: comments, unquoted keys, single-quoted strings, hex/relaxed numbers,
// Infinity/NaN, line continuation, and inherited strict-JSON behavior
// =================================================================================================
class Json5LexerTest {
    private fun tokenize(text: String): List<Token> {
        val lexer = Json5Lexer(text)
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = lexer.nextToken()
            tokens.add(token)
            if (token.type == TokenType.JEndOfInput) return tokens
        }
    }

    private fun singleToken(text: String): Token = tokenize(text).first()

    // --- inherited strict-JSON behavior still works --------------------------------------------

    @Test
    fun inheritsStrictJsonPunctuationAndKeywords() {
        val types = tokenize("{}[]:,true false null").map { it.type }
        assertEquals(
            listOf(
                TokenType.JLBrace, TokenType.JRBrace, TokenType.JLBracket, TokenType.JRBracket,
                TokenType.JColon, TokenType.JComma, TokenType.JTrue, TokenType.JFalse, TokenType.JNull,
                TokenType.JEndOfInput,
            ),
            types,
        )
    }

    @Test
    fun inheritsDoubleQuotedStringsAndStandardNumbers() {
        val token = singleToken("\"hello\"")
        assertEquals(TokenType.JString, token.type)
        assertEquals("\"hello\"", token.text)

        val number = singleToken("42.5")
        assertEquals(TokenType.JNumber, number.type)
        assertEquals("42.5", number.text)
    }

    // --- comments --------------------------------------------------------------------------

    @Test
    fun lineComment_isSkipped() {
        val types = tokenize("1 // a comment\n2").map { it.type }
        assertEquals(listOf(TokenType.JNumber, TokenType.JNumber, TokenType.JEndOfInput), types)
    }

    @Test
    fun lineComment_atEndOfInput_isSkipped() {
        val types = tokenize("1 // trailing, no newline").map { it.type }
        assertEquals(listOf(TokenType.JNumber, TokenType.JEndOfInput), types)
    }

    @Test
    fun blockComment_isSkipped() {
        val types = tokenize("1 /* a\nmulti-line\ncomment */ 2").map { it.type }
        assertEquals(listOf(TokenType.JNumber, TokenType.JNumber, TokenType.JEndOfInput), types)
    }

    @Test
    fun blockComment_unterminated_fails() {
        assertFailsWith<JsonXParseException> { Json5Lexer("/* never closed").nextToken() }
    }

    @Test
    fun commentsInterspersedWithStructure() {
        val types = tokenize("{ // key follows\n\"a\": /* value */ 1 }").map { it.type }
        assertEquals(
            listOf(TokenType.JLBrace, TokenType.JString, TokenType.JColon, TokenType.JNumber, TokenType.JRBrace, TokenType.JEndOfInput),
            types,
        )
    }

    // --- unquoted identifiers ----------------------------------------------------------------

    @Test
    fun unquotedIdentifier_recognized() {
        val token = singleToken("fooBar")
        assertEquals(TokenType.JIdentifier, token.type)
        assertEquals("fooBar", token.text)
    }

    @Test
    fun unquotedIdentifier_withDollarAndUnderscore() {
        val token = singleToken("_foo\$bar")
        assertEquals(TokenType.JIdentifier, token.type)
        assertEquals("_foo\$bar", token.text)
    }

    @Test
    fun unquotedIdentifier_withDigitsAfterFirstChar() {
        val token = singleToken("a1b2")
        assertEquals(TokenType.JIdentifier, token.type)
        assertEquals("a1b2", token.text)
    }

    @Test
    fun trueFalseNull_stillRecognizedAsKeywords_notIdentifiers() {
        assertEquals(TokenType.JTrue, singleToken("true").type)
        assertEquals(TokenType.JFalse, singleToken("false").type)
        assertEquals(TokenType.JNull, singleToken("null").type)
    }

    // --- single-quoted strings ------------------------------------------------------------------

    @Test
    fun singleQuotedString_recognized() {
        val token = singleToken("'hello'")
        assertEquals(TokenType.JString, token.type)
        assertEquals("'hello'", token.text)
    }

    @Test
    fun singleQuotedString_canContainDoubleQuoteUnescaped() {
        val token = singleToken("'say \"hi\"'")
        assertEquals(TokenType.JString, token.type)
        assertEquals("'say \"hi\"'", token.text)
    }

    @Test
    fun doubleQuotedString_canContainSingleQuoteUnescaped() {
        val token = singleToken("\"it's fine\"")
        assertEquals(TokenType.JString, token.type)
        assertEquals("\"it's fine\"", token.text)
    }

    // --- string relaxations: raw control chars, line continuation ------------------------------

    @Test
    fun string_rawTabCharacter_isAllowed() {
        // unlike strict JSON, JSON5 allows raw (non-line-terminator) control characters unescaped
        val source = "\"a\tb\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun string_rawLineTerminator_stillFails() {
        assertFailsWith<JsonXParseException> { Json5Lexer("\"a\nb\"").nextToken() }
    }

    @Test
    fun string_lineContinuation_spansLines() {
        val source = "\"a\\\nb\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun string_unrecognizedEscape_isAcceptedAsNonEscapeCharacter() {
        // JSON5's permissive escape grammar: an unrecognized \X is just X, not an error
        val source = "\"\\q\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun string_hexEscape_validTwoDigits() {
        val source = "\"\\x41\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun string_hexEscape_truncated_fails() {
        assertFailsWith<JsonXParseException> { Json5Lexer("\"\\x4\"").nextToken() }
    }

    // --- numbers: hex, leading/trailing decimal point, leading +, Infinity/NaN -----------------

    @Test
    fun number_hexLiteral() {
        val token = singleToken("0x1A")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("0x1A", token.text)
    }

    @Test
    fun number_hexLiteral_uppercasePrefix() {
        val token = singleToken("0X1a")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("0X1a", token.text)
    }

    @Test
    fun number_hexLiteral_noDigits_fails() {
        assertFailsWith<JsonXParseException> { Json5Lexer("0x").nextToken() }
    }

    @Test
    fun number_leadingDecimalPoint() {
        val token = singleToken(".5")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals(".5", token.text)
    }

    @Test
    fun number_trailingDecimalPoint() {
        val token = singleToken("5.")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("5.", token.text)
    }

    @Test
    fun number_bareDecimalPoint_fails() {
        assertFailsWith<JsonXParseException> { Json5Lexer(".").nextToken() }
    }

    @Test
    fun number_leadingPlusSign() {
        val token = singleToken("+5")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("+5", token.text)
    }

    @Test
    fun number_leadingPlusWithDecimal() {
        val token = singleToken("+.5")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("+.5", token.text)
    }

    @Test
    fun number_infinity() {
        val token = singleToken("Infinity")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("Infinity", token.text)
    }

    @Test
    fun number_negativeInfinity() {
        val token = singleToken("-Infinity")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("-Infinity", token.text)
    }

    @Test
    fun number_positiveInfinity() {
        val token = singleToken("+Infinity")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("+Infinity", token.text)
    }

    @Test
    fun number_nan() {
        val token = singleToken("NaN")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("NaN", token.text)
    }

    @Test
    fun number_invalidWordAfterSign_fails() {
        assertFailsWith<JsonXParseException> { Json5Lexer("+Nope").nextToken() }
    }

    @Test
    fun number_soleZero_isACompleteNumberToken() {
        val token = singleToken("0")
        assertEquals(TokenType.JNumber, token.type)
        assertEquals("0", token.text)
    }

    @Test
    fun number_leadingZeroFollowedByDigit_stopsAtTheZero() {
        // "010" is legacy octal syntax, disallowed even in JSON5 (ECMAScript's
        // DecimalIntegerLiteral forbids a leading zero followed by another digit). The lexer
        // stops at the lone "0"; it's Json5Parser that then rejects the leftover "10" as
        // unexpected trailing content — same split responsibility as strict JSON.
        val tokens = tokenize("010")
        assertEquals(listOf(TokenType.JNumber, TokenType.JNumber, TokenType.JEndOfInput), tokens.map { it.type })
        assertEquals("0", tokens[0].text)
        assertEquals("10", tokens[1].text)
    }

    // --- expanded whitespace -----------------------------------------------------------------

    @Test
    fun expandedWhitespace_isSkipped() {
        val source = "1\u000B\u000C\u00A0\uFEFF\u2028\u20292"
        val types = tokenize(source).map { it.type }
        assertEquals(listOf(TokenType.JNumber, TokenType.JNumber, TokenType.JEndOfInput), types)
    }

    // --- full document ---------------------------------------------------------------------------

    @Test
    fun fullJson5Document_tokenizesInOrder() {
        val source =
            """
            {
              // a comment
              unquoted: 'single quoted',
              hex: 0x1A,
              trailing: .5,
              inf: Infinity,
            }
            """.trimIndent()
        val types = tokenize(source).map { it.type }
        assertEquals(
            listOf(
                TokenType.JLBrace,
                TokenType.JIdentifier, TokenType.JColon, TokenType.JString, TokenType.JComma,
                TokenType.JIdentifier, TokenType.JColon, TokenType.JNumber, TokenType.JComma,
                TokenType.JIdentifier, TokenType.JColon, TokenType.JNumber, TokenType.JComma,
                TokenType.JIdentifier, TokenType.JColon, TokenType.JNumber, TokenType.JComma,
                TokenType.JRBrace,
                TokenType.JEndOfInput,
            ),
            types,
        )
    }
}
