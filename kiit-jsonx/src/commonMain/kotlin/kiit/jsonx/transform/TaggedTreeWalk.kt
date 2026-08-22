/** url: www.kiit.dev */
package kiit.jsonx.transform

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.error.JsonXResult
import kiit.result.Failure
import kiit.result.Success

/**
 * Depth-first, bottom-up rewrite of every [JsonXTagged] node in this tree via [resolve] — the
 * shared "walk and replace matching tags" primitive both [resolveEnv] and [resolveRefs] build on,
 * instead of each transform hand-rolling its own traversal.
 *
 * A tag's own arguments are rewritten before [resolve] sees them, so a tag nested inside another
 * tag's arguments (e.g. a `@table` cell holding an `@env(...)` call) is always resolved
 * inside-out without [resolve] itself needing to know anything about nesting.
 */
internal fun JsonXElement.mapTagged(resolve: (JsonXTagged) -> JsonXResult<JsonXElement>): JsonXResult<JsonXElement> {
    return when (this) {
        is JsonXObject -> {
            val entries = LinkedHashMap<String, JsonXElement>()
            for ((key, value) in this.entries) {
                when (val result = value.mapTagged(resolve)) {
                    is Success -> entries[key] = result.value
                    is Failure -> return result
                }
            }
            Success(JsonXObject(entries))
        }
        is JsonXArray -> {
            val items = mutableListOf<JsonXElement>()
            for (item in this.items) {
                when (val result = item.mapTagged(resolve)) {
                    is Success -> items.add(result.value)
                    is Failure -> return result
                }
            }
            Success(JsonXArray(items))
        }
        is JsonXTagged -> {
            val args = mutableListOf<JsonXElement>()
            for (arg in this.args) {
                when (val result = arg.mapTagged(resolve)) {
                    is Success -> args.add(result.value)
                    is Failure -> return result
                }
            }
            resolve(JsonXTagged(this.name, args))
        }
        else -> Success(this)
    }
}
