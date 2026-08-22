/** url: www.kiit.dev */
package kiit.jsonx.extract

import kiit.codes.Err
import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.error.JsonXError
import kiit.jsonx.error.JsonXException

/**
 * The three distinct throwing-getter failure shapes the PRD calls for: missing, wrong type, and
 * an unresolved tag. Kept apart (rather than one generic "couldn't get value" message) so a
 * caller inspecting [JsonXException.error] can tell them apart structurally, not by
 * string-matching a message. Position (`line`/`column`/`offset`) stays null on every one of
 * these — see [JsonXError]'s KDoc: extraction-stage errors report by path instead.
 */
internal fun missingPathException(path: String): JsonXException =
    JsonXException(JsonXError(Err.on(path, "", "no value found at path '$path'")))

internal fun wrongTypeException(path: String, expected: String, actual: JsonXElement): JsonXException =
    JsonXException(
        JsonXError(Err.on(path, actual::class.simpleName ?: "?", "expected $expected at path '$path'")),
    )

internal fun unresolvedTagException(path: String, tag: JsonXTagged): JsonXException =
    JsonXException(
        JsonXError(Err.on(path, "@${tag.name}", "value at path '$path' is an unresolved tag, not a plain value")),
    )
