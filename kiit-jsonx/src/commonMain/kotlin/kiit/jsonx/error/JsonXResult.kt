/** url: www.kiit.dev */
package kiit.jsonx.error

import kiit.result.Result

/**
 * The result type returned by every parse/tag-resolution/transform-stage operation in
 * kiit-jsonx.
 *
 * 1. Alias for [kiit.result.Result], defaulting the error branch to [JsonXError]. Mirrors the
 *    alias pattern kiit-result itself uses for `Outcome<T>` (`Result<T, Err>`).
 * 2. [JsonXException] is the throwing counterpart, used only by the throwing extraction
 *    methods added in Phase 3 (`getX`, as opposed to their non-throwing
 *    `getXOrElse`/`getXOrNull` siblings). [JsonXResult] itself is never thrown.
 */
typealias JsonXResult<T> = Result<T, JsonXError>
