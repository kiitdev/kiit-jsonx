/** url: www.kiit.dev */
package kiit.jsonx.error

import kiit.codes.Failed
import kiit.codes.Invalid

/**
 * Internal control-flow signal for a lex/parse-time failure, not part of the public API.
 *
 * Thrown deep inside recursive-descent lexing/parsing (potentially many stack frames down) and
 * caught exactly once, at the module's public entry point (e.g. a future `JsonParser.parse`),
 * where it is converted into a proper [JsonXResult] `Failure`. Kept `internal` so it
 * structurally cannot leak into a consumer's code. Only code inside this module can even
 * reference the type, so if it ever escapes uncaught, that's a bug in this module, not a public
 * contract change.
 *
 * Deliberately avoids [JsonXResult] at this internal layer: allocating a `Success`/`Failure`
 * wrapper on every lexer/parser step is wasted work, since almost every call succeeds.
 * [fillInStackTrace] is overridden to a no-op since this is a control-flow signal, not a
 * diagnostic for a real crash. Capturing a JVM stack trace on every throw would erase the reason
 * exceptions are being used here.
 */
internal class JsonXParseException(
    val error: JsonXError,
    val status: Failed = Invalid.INVALID_VALUE,
) : Exception(error.err.message) {
    override fun fillInStackTrace(): Throwable = this
}
