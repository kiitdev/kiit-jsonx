/** url: www.kiit.dev */
package kiit.jsonx.error

import kiit.codes.Err

/**
 * The single error type threaded through every kiit-jsonx operation: parsing, tag resolution,
 * transforms, and extraction.
 *
 * Wraps [Err] (kiit-codes' error representation) rather than forking it, plus an optional source
 * position. Position is only ever populated for parse-time or tag-resolution-time failures.
 * Transform- and extraction-stage errors report by path instead, carried on [err] itself (e.g.
 * via [Err.ErrorField.field]), not on [line]/[column]/[offset].
 */
data class JsonXError(
    val err: Err,
    val line: Int? = null,
    val column: Int? = null,
    val offset: Int? = null,
    val file: String? = null,
)
