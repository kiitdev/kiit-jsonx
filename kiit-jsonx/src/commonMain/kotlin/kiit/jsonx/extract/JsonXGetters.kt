/** url: www.kiit.dev */
package kiit.jsonx.extract

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXBoolean
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged

/**
 * Typed getters per scalar/collection type, each following the three-suffix convention: `getX`
 * throws [kiit.jsonx.error.JsonXException] (missing, wrong type, and an unresolved tag are three
 * distinct failures, see `JsonXExtractionErrors.kt`), `getXOrElse`/`getXOrNull` never throw and
 * share this same [find]-based lookup.
 *
 * `getInt`/`getLong`/`getDouble` are three separate accessors, not one: [JsonXNumber] carries a
 * `long` or a `double`, never both, so a config value written as `8080` behaves differently from
 * `8080.5` depending which accessor reads it. `getLong`/`getInt` accept a double-backed number
 * only if it has no fractional part (e.g. `8080.0` reads fine as a `Long`); `getDouble` always
 * widens either branch and never fails on type grounds.
 */
@Suppress("ThrowsCount") // three distinct throw sites are the point of this function, not a smell
private inline fun <reified T : JsonXElement> JsonXElement.getElement(path: String, typeName: String): T {
    val found = find(path) ?: throw missingPathException(path)
    if (found is JsonXTagged) throw unresolvedTagException(path, found)
    return found as? T ?: throw wrongTypeException(path, typeName, found)
}

private inline fun <reified T : JsonXElement> JsonXElement.getElementOrNull(path: String): T? = find(path) as? T

// --- String ----------------------------------------------------------------------------------

fun JsonXElement.getString(path: String): String = getElement<JsonXString>(path, "a string").value

fun JsonXElement.getStringOrElse(path: String, default: String): String = getStringOrNull(path) ?: default

fun JsonXElement.getStringOrNull(path: String): String? = getElementOrNull<JsonXString>(path)?.value

// --- Boolean -------------------------------------------------------------------------------

fun JsonXElement.getBoolean(path: String): Boolean = getElement<JsonXBoolean>(path, "a boolean").value

fun JsonXElement.getBooleanOrElse(path: String, default: Boolean): Boolean = getBooleanOrNull(path) ?: default

fun JsonXElement.getBooleanOrNull(path: String): Boolean? = getElementOrNull<JsonXBoolean>(path)?.value

// --- Object / Array: return the node itself, not an unwrapped scalar ---------------------------

fun JsonXElement.getObject(path: String): JsonXObject = getElement(path, "an object")

fun JsonXElement.getObjectOrElse(path: String, default: JsonXObject): JsonXObject = getObjectOrNull(path) ?: default

fun JsonXElement.getObjectOrNull(path: String): JsonXObject? = getElementOrNull(path)

fun JsonXElement.getArray(path: String): JsonXArray = getElement(path, "an array")

fun JsonXElement.getArrayOrElse(path: String, default: JsonXArray): JsonXArray = getArrayOrNull(path) ?: default

fun JsonXElement.getArrayOrNull(path: String): JsonXArray? = getElementOrNull(path)

// --- Numbers: getLong/getInt narrow (fail on a non-integral double), getDouble always widens ----

fun JsonXElement.getLong(path: String): Long = numberToLong(path, getElement(path, "a number"))

fun JsonXElement.getLongOrElse(path: String, default: Long): Long = getLongOrNull(path) ?: default

fun JsonXElement.getLongOrNull(path: String): Long? {
    val number = getElementOrNull<JsonXNumber>(path) ?: return null
    return number.long ?: number.double?.exactLongOrNull()
}

fun JsonXElement.getInt(path: String): Int {
    val long = getLong(path)
    return long.exactIntOrNull() ?: throw wrongTypeException(path, "a number that fits in an Int", JsonXNumber.of(long))
}

fun JsonXElement.getIntOrElse(path: String, default: Int): Int = getIntOrNull(path) ?: default

fun JsonXElement.getIntOrNull(path: String): Int? = getLongOrNull(path)?.exactIntOrNull()

fun JsonXElement.getDouble(path: String): Double {
    val number = getElement<JsonXNumber>(path, "a number")
    return number.double ?: number.long!!.toDouble()
}

fun JsonXElement.getDoubleOrElse(path: String, default: Double): Double = getDoubleOrNull(path) ?: default

fun JsonXElement.getDoubleOrNull(path: String): Double? {
    val number = getElementOrNull<JsonXNumber>(path) ?: return null
    return number.double ?: number.long?.toDouble()
}

/** Shared by the scalar and list `getLong` variants — see this file's class-level KDoc for the exactness rule. */
internal fun numberToLong(path: String, number: JsonXNumber): Long =
    number.long ?: number.double?.exactLongOrNull() ?: throw wrongTypeException(path, "a whole number", number)

internal fun Double.exactLongOrNull(): Long? =
    if (isFinite() && this == kotlin.math.floor(this) && this in MIN_EXACT_LONG..MAX_EXACT_LONG) toLong() else null

internal fun Long.exactIntOrNull(): Int? = if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else null

private const val MIN_EXACT_LONG = Long.MIN_VALUE.toDouble()
private const val MAX_EXACT_LONG = Long.MAX_VALUE.toDouble()
