package kiit.jsonx.parser.dialect

import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.parser.core.Token
import kiit.jsonx.parser.core.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// =================================================================================================
// JsonxLexerTest: hand-written fixture corpus for jsonx-specific grammar (Milestone 2.5, task 5)
// — triple-quoted strings, @tag(args) syntax, # comments, and inherited JSON5 behavior.
// =================================================================================================
class JsonxLexerTest {
    private fun tokenize(text: String): List<Token> {
        val lexer = JsonxLexer(text)
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = lexer.nextToken()
            tokens.add(token)
            if (token.type == TokenType.JEndOfInput) return tokens
        }
    }

    private fun singleToken(text: String): Token = tokenize(text).first()

    // --- inherited JSON5 behavior still works --------------------------------------------------

    @Test
    fun inheritsJson5PunctuationAndTrailingCommaFriendlyTokens() {
        val types = tokenize("{a: 1, 'b': 2,}").map { it.type }
        assertEquals(
            listOf(
                TokenType.JLBrace, TokenType.JIdentifier, TokenType.JColon, TokenType.JNumber, TokenType.JComma,
                TokenType.JString, TokenType.JColon, TokenType.JNumber, TokenType.JComma, TokenType.JRBrace,
                TokenType.JEndOfInput,
            ),
            types,
        )
    }

    @Test
    fun inheritsJson5LineAndBlockComments() {
        val types = tokenize("1 // line\n/* block */ 2").map { it.type }
        assertEquals(listOf(TokenType.JNumber, TokenType.JNumber, TokenType.JEndOfInput), types)
    }

    @Test
    fun inheritsJson5HexAndInfinityNumbers() {
        assertEquals("0x1A", singleToken("0x1A").text)
        assertEquals("Infinity", singleToken("Infinity").text)
    }

    // --- # comments (dialect-only) -----------------------------------------------------------

    @Test
    fun hashComment_isSkipped() {
        val types = tokenize("1 # a comment\n2").map { it.type }
        assertEquals(listOf(TokenType.JNumber, TokenType.JNumber, TokenType.JEndOfInput), types)
    }

    @Test
    fun hashComment_atEndOfInput_isSkipped() {
        val types = tokenize("1 # trailing, no newline").map { it.type }
        assertEquals(listOf(TokenType.JNumber, TokenType.JEndOfInput), types)
    }

    @Test
    fun hashComment_mixedWithSlashComments() {
        val types = tokenize("1 # hash\n2 // slash\n3 /* block */ 4").map { it.type }
        assertEquals(
            listOf(TokenType.JNumber, TokenType.JNumber, TokenType.JNumber, TokenType.JNumber, TokenType.JEndOfInput),
            types,
        )
    }

    // --- triple-quoted strings ------------------------------------------------------------------

    @Test
    fun tripleQuotedString_simple() {
        val source = "\"\"\"hello\"\"\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun tripleQuotedString_empty() {
        val source = "\"\"\"\"\"\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun tripleQuotedString_spansMultipleRawLines() {
        val source = "\"\"\"line one\nline two\"\"\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun tripleQuotedString_allowsUnescapedSingleAndDoubleQuoteRuns() {
        val source = "\"\"\"a \" b \"\" c\"\"\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun tripleQuotedString_lineContinuation_isValidEscape() {
        val source = "\"\"\"a\\\n  b\"\"\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun tripleQuotedString_standardEscapes_stillValid() {
        val source = "\"\"\"a\\nb\\tc\"\"\""
        val token = singleToken(source)
        assertEquals(TokenType.JString, token.type)
        assertEquals(source, token.text)
    }

    @Test
    fun tripleQuotedString_unterminated_fails() {
        assertFailsWith<JsonXParseException> { JsonxLexer("\"\"\"never closed").nextToken() }
    }

    @Test
    fun tripleQuotedString_threeQuoteRunAlwaysCloses_evenMidLiteralIntent() {
        // TOML disallows an unescaped 3+ quote run as content, so the first complete run of
        // three always closes the string. A dangling 4th quote is left as its own (invalid)
        // token — the documented, unambiguous behavior, not a bug.
        assertFailsWith<JsonXParseException> { tokenize("\"\"\"abc\"\"\"\"") }
    }

    @Test
    fun doubleQuotedString_stillWorksAlongsideTripleQuoted() {
        val token = singleToken("\"hello\"")
        assertEquals(TokenType.JString, token.type)
        assertEquals("\"hello\"", token.text)
    }

    @Test
    fun singleQuotedString_stillWorksAlongsideTripleQuoted() {
        val token = singleToken("'hello'")
        assertEquals(TokenType.JString, token.type)
        assertEquals("'hello'", token.text)
    }

    // --- @tag(args) syntax -----------------------------------------------------------------------

    @Test
    fun tag_simpleName() {
        val token = singleToken("@env")
        assertEquals(TokenType.JTag, token.type)
        assertEquals("@env", token.text)
    }

    @Test
    fun tag_namespacedName() {
        val token = singleToken("@acmecorp.date")
        assertEquals(TokenType.JTag, token.type)
        assertEquals("@acmecorp.date", token.text)
    }

    @Test
    fun tag_multiSegmentName() {
        val token = singleToken("@a.b.c")
        assertEquals(TokenType.JTag, token.type)
        assertEquals("@a.b.c", token.text)
    }

    @Test
    fun tag_followedByParenIsTokenizedSeparately() {
        val types = tokenize("@env('DB_HOST')").map { it.type }
        assertEquals(
            listOf(TokenType.JTag, TokenType.JLParen, TokenType.JString, TokenType.JRParen, TokenType.JEndOfInput),
            types,
        )
    }

    @Test
    fun tag_withNoNameAfterAt_fails() {
        assertFailsWith<JsonXParseException> { JsonxLexer("@(").nextToken() }
    }

    @Test
    fun tag_dotNotFollowedByIdentifier_stopsAtTheDot() {
        // "@a." — the trailing dot isn't part of a valid next segment, so the tag name token
        // ends at "a"; "." is left for the next lex call, which fails on its own (a lone "."
        // needs a following digit to be a valid JSON5 number) — checked here via a single
        // nextToken() call, not the full tokenize() loop, since that second call throws.
        val firstToken = JsonxLexer("@a.").nextToken()
        assertEquals(TokenType.JTag, firstToken.type)
        assertEquals("@a", firstToken.text)
    }

    // --- full document ---------------------------------------------------------------------------

    @Test
    fun fullJsonxDocument_tokenizesInOrder() {
        val source =
            """
            {
              # env-style comment
              host: @env('DB_HOST'),
              bio: '''multi
              line''',
            }
            """.trimIndent().replace("'''", "\"\"\"")
        val types = tokenize(source).map { it.type }
        assertEquals(
            listOf(
                TokenType.JLBrace,
                TokenType.JIdentifier, TokenType.JColon, TokenType.JTag, TokenType.JLParen, TokenType.JString,
                TokenType.JRParen, TokenType.JComma,
                TokenType.JIdentifier, TokenType.JColon, TokenType.JString, TokenType.JComma,
                TokenType.JRBrace,
                TokenType.JEndOfInput,
            ),
            types,
        )
    }
}
