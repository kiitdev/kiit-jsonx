/** url: www.kiit.dev */
package kiit.jsonx.extract

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXObject

/**
 * Resolves [path] against this tree, walking object keys and array indices.
 *
 * 1. Returns null if any step of the path is missing or type-mismatched (e.g. indexing into a
 *    string) — the single shared lookup primitive every typed getter in this package builds on.
 * 2. An empty [path] returns the receiver itself.
 * 3. A syntactically malformed path (see [parseJsonXPath]) throws [IllegalArgumentException]
 *    rather than returning null, since that's a bug in the path string, not a "nothing there"
 *    outcome.
 */
fun JsonXElement.find(path: String): JsonXElement? {
    var current: JsonXElement = this
    for (segment in parseJsonXPath(path)) {
        if (segment.key.isNotEmpty()) {
            val obj = current as? JsonXObject ?: return null
            current = obj.entries[segment.key] ?: return null
        }
        for (index in segment.indices) {
            val array = current as? JsonXArray ?: return null
            current = array.items.getOrNull(index) ?: return null
        }
    }
    return current
}

/**
 * True if [path] resolves to any node, including [JsonXElement.JsonXNull] — an explicit JSON
 * `null` still counts as "exists," it's a real, resolved value, not an absence.
 */
fun JsonXElement.exists(path: String): Boolean = find(path) != null
