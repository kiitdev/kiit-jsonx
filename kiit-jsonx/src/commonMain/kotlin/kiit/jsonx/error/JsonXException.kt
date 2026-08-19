/** url: www.kiit.dev */
@file:JvmName("JsonXExceptions")

package kiit.jsonx.error

import kotlin.jvm.JvmName

/**
 * Thin exception wrapper around a [JsonXError].
 *
 * 1. Reserved for the throwing extraction methods added in Phase 3 (`getX`, as opposed to
 *    their non-throwing `getXOrElse`/`getXOrNull` siblings). Those methods will be marked
 *    `@Throws(JsonXException::class)` for Kotlin/Native (iOS) interop once that target lands
 *    in Phase 4.
 * 2. [JsonXResult]-returning operations (parse, tag resolution, transforms) never throw this.
 */
class JsonXException(
    val error: JsonXError,
    cause: Throwable? = null,
) : Exception(error.err.message, cause)
