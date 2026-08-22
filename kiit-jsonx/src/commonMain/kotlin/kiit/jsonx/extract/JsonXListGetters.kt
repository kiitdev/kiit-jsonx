/** url: www.kiit.dev */
package kiit.jsonx.extract

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXBoolean
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged

/**
 * List-of-scalar getters: [path] must resolve to an array, and every item in it must itself be
 * the target type — a mixed-type array fails the whole extraction (never a partial list),
 * the same all-or-nothing philosophy as the scalar getters in `JsonXGetters.kt`.
 */
private inline fun <reified T : JsonXElement> JsonXElement.getElementList(path: String, itemTypeName: String): List<T> =
    getArray(path).items.mapIndexed { index, item ->
        val itemPath = "$path[$index]"
        if (item is JsonXTagged) throw unresolvedTagException(itemPath, item)
        item as? T ?: throw wrongTypeException(itemPath, itemTypeName, item)
    }

private inline fun <reified T : JsonXElement> JsonXElement.getElementListOrNull(path: String): List<T>? {
    val items = getArrayOrNull(path)?.items ?: return null
    val result = ArrayList<T>(items.size)
    for (item in items) {
        result.add(item as? T ?: return null)
    }
    return result
}

// --- String list -----------------------------------------------------------------------------

fun JsonXElement.getStringList(path: String): List<String> {
    return getElementList<JsonXString>(path, "a string").map { it.value }
}

fun JsonXElement.getStringListOrElse(path: String, default: List<String>): List<String> {
    return getStringListOrNull(path) ?: default
}

fun JsonXElement.getStringListOrNull(path: String): List<String>? {
    return getElementListOrNull<JsonXString>(path)?.map { it.value }
}

// --- Boolean list ----------------------------------------------------------------------------

fun JsonXElement.getBooleanList(path: String): List<Boolean> {
    return getElementList<JsonXBoolean>(path, "a boolean").map { it.value }
}

fun JsonXElement.getBooleanListOrElse(path: String, default: List<Boolean>): List<Boolean> {
    return getBooleanListOrNull(path) ?: default
}

fun JsonXElement.getBooleanListOrNull(path: String): List<Boolean>? {
    return getElementListOrNull<JsonXBoolean>(path)?.map { it.value }
}

// --- Object list -----------------------------------------------------------------------------

fun JsonXElement.getObjectList(path: String): List<JsonXObject> = getElementList(path, "an object")

fun JsonXElement.getObjectListOrElse(path: String, default: List<JsonXObject>): List<JsonXObject> {
    return getObjectListOrNull(path) ?: default
}

fun JsonXElement.getObjectListOrNull(path: String): List<JsonXObject>? = getElementListOrNull(path)

// --- Number lists: same long/int/double exactness rules as the scalar getters ------------------

fun JsonXElement.getLongList(path: String): List<Long> =
    getArray(path).items.mapIndexed { index, item ->
        val itemPath = "$path[$index]"
        if (item is JsonXTagged) throw unresolvedTagException(itemPath, item)
        val number = item as? JsonXNumber ?: throw wrongTypeException(itemPath, "a number", item)
        numberToLong(itemPath, number)
    }

fun JsonXElement.getLongListOrElse(path: String, default: List<Long>): List<Long> = getLongListOrNull(path) ?: default

fun JsonXElement.getLongListOrNull(path: String): List<Long>? {
    val items = getArrayOrNull(path)?.items ?: return null
    val result = ArrayList<Long>(items.size)
    for (item in items) {
        val number = item as? JsonXNumber ?: return null
        result.add(number.long ?: number.double?.exactLongOrNull() ?: return null)
    }
    return result
}

fun JsonXElement.getIntList(path: String): List<Int> =
    getLongList(path).mapIndexed { index, value ->
        val itemPath = "$path[$index]"
        val expected = "a number that fits in an Int"
        value.exactIntOrNull() ?: throw wrongTypeException(itemPath, expected, JsonXNumber.of(value))
    }

fun JsonXElement.getIntListOrElse(path: String, default: List<Int>): List<Int> = getIntListOrNull(path) ?: default

fun JsonXElement.getIntListOrNull(path: String): List<Int>? {
    return getLongListOrNull(path)?.map { it.exactIntOrNull() ?: return null }
}

fun JsonXElement.getDoubleList(path: String): List<Double> =
    getArray(path).items.mapIndexed { index, item ->
        val itemPath = "$path[$index]"
        if (item is JsonXTagged) throw unresolvedTagException(itemPath, item)
        val number = item as? JsonXNumber ?: throw wrongTypeException(itemPath, "a number", item)
        number.double ?: number.long!!.toDouble()
    }

fun JsonXElement.getDoubleListOrElse(path: String, default: List<Double>): List<Double> {
    return getDoubleListOrNull(path) ?: default
}

fun JsonXElement.getDoubleListOrNull(path: String): List<Double>? {
    val items = getArrayOrNull(path)?.items ?: return null
    val result = ArrayList<Double>(items.size)
    for (item in items) {
        val number = item as? JsonXNumber ?: return null
        result.add(number.double ?: number.long?.toDouble() ?: return null)
    }
    return result
}
