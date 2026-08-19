/** url: www.kiit.dev */
package kiit.jsonx.parser.core

/**
 * A single point in source text, captured via [LexerState.snapshot].
 *
 * @param offset zero-based character offset from the start of the source
 * @param line one-based line number
 * @param column one-based column number within [line]
 */
data class SourcePosition(val offset: Int, val line: Int, val column: Int)
