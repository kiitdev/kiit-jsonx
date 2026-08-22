/** url: www.kiit.dev */
package kiit.jsonx.tags.builtin

import kiit.codes.Err
import kiit.codes.Invalid
import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.error.JsonXError
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.tags.ExperimentalJsonxTagApi
import kiit.jsonx.tags.TagHandler
import kiit.jsonx.tags.TagKind
import kiit.result.Failure
import kiit.result.Success

/**
 * `@table({ names: [...], rows: [...] })`: sugar over an ordinary object, not a structural tag.
 *
 * 1. Its one argument is a plain [JsonXObject] parsed through the normal value grammar. All the
 *    real work happens here, as ordinary logic over already-parsed [JsonXElement]s: zip each
 *    row in `rows` against `names` (or positional `col0`, `col1`, ... if `names` is omitted) to
 *    build one [JsonXObject] per row.
 * 2. Every row's length must match `names.size` (when supplied) or the width of the first row
 *    (when it isn't). A mismatched row is a resolution failure, never silently truncated or
 *    padded.
 * 3. This is the eager-resolution proof of concept: since this handler is registered by default
 *    (see `TagRegistry`), `JsonXParser` calls [resolve] immediately during parsing and splices
 *    the result into the tree in place of the raw `JsonXTagged` node. Unregistered tags (`@env`,
 *    `@ref`) skip this entirely and stay as `JsonXTagged` for the transform pipeline instead.
 */
@ExperimentalJsonxTagApi
class TableTagHandler : TagHandler {
    override val name: String = NAME
    override val kind: TagKind = TagKind.Simple

    override fun resolve(args: List<JsonXElement>): JsonXResult<JsonXElement> {
        val table =
            args.singleOrNull() as? JsonXObject
                ?: return failure("@table expects exactly one object argument, got $args")

        val rows =
            table.entries["rows"] as? JsonXArray
                ?: return failure("@table's argument must have a 'rows' array")

        val namesElement = table.entries["names"]
        val names: List<String>? =
            when (namesElement) {
                null -> null
                is JsonXArray -> {
                    val extracted = ArrayList<String>(namesElement.items.size)
                    for (item in namesElement.items) {
                        val entry =
                            item as? JsonXString
                                ?: return failure("'names' must be an array of strings, found $item")
                        extracted.add(entry.value)
                    }
                    extracted
                }
                else -> return failure("'names' must be an array of strings")
            }

        return buildRows(rows, names)
    }

    private fun buildRows(rows: JsonXArray, names: List<String>?): JsonXResult<JsonXElement> {
        var expectedWidth = names?.size
        val objects = mutableListOf<JsonXElement>()

        rows.items.forEachIndexed { index, rowElement ->
            val row = rowElement as? JsonXArray ?: return failure("row $index must be an array, got $rowElement")
            if (expectedWidth == null) expectedWidth = row.items.size
            if (row.items.size != expectedWidth) {
                return failure("row $index has ${row.items.size} value(s), expected $expectedWidth")
            }

            val columnNames = names ?: List(expectedWidth) { "col$it" }
            val entries = LinkedHashMap<String, JsonXElement>()
            columnNames.forEachIndexed { column, columnName -> entries[columnName] = row.items[column] }
            objects.add(JsonXObject(entries))
        }

        return Success(JsonXArray(objects))
    }

    private fun failure(message: String): JsonXResult<Nothing> {
        return Failure(JsonXError(Err.of(message)), Invalid.INVALID_VALUE)
    }

    companion object {
        const val NAME: String = "table"
    }
}
