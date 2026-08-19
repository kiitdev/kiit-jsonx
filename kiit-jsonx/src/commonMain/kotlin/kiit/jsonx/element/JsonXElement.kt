/** url: www.kiit.dev */
package kiit.jsonx.element

/**
 * The parsed tree representation shared by every kiit-jsonx dialect ([kiit.jsonx.Format.Json],
 * [kiit.jsonx.Format.Json5], [kiit.jsonx.Format.Jsonx], [kiit.jsonx.Format.JsonL]).
 *
 * Node names cosmetically mirror `kotlinx.serialization.json.JsonElement` — there is no
 * functional relationship or dependency between the two.
 *
 * No node carries a source position. Positions live only on `JsonXError` (see
 * `kiit.jsonx.error`), never on the tree itself: a tree is a pure value, freely constructed,
 * merged, and compared independent of where (or whether) it came from parsed text.
 */
sealed class JsonXElement {
    /** A JSON object. Insertion order of [entries] is always preserved — there is no opt-out. */
    data class JsonXObject(val entries: LinkedHashMap<String, JsonXElement>) : JsonXElement()

    data class JsonXArray(val items: List<JsonXElement>) : JsonXElement()

    data class JsonXString(val value: String) : JsonXElement()

    /**
     * A parsed number. Exactly one of [long]/[double] is non-null: integral literals populate
     * [long], anything with a fractional part or exponent populates [double]. Use [of] rather
     * than the raw constructor to avoid picking the wrong branch by hand.
     */
    data class JsonXNumber(val long: Long?, val double: Double?) : JsonXElement() {
        init {
            require((long == null) != (double == null)) {
                "JsonXNumber requires exactly one of long/double to be non-null"
            }
        }

        companion object {
            fun of(value: Long): JsonXNumber = JsonXNumber(value, null)

            fun of(value: Double): JsonXNumber = JsonXNumber(null, value)
        }
    }

    data class JsonXBoolean(val value: Boolean) : JsonXElement()

    object JsonXNull : JsonXElement()

    /**
     * An unresolved `@name(args)` tag, produced by the parser (Phase 2). Resolution into a
     * plain [JsonXElement] happens later, via a `TagHandler` registered on `ParseOptions`
     * (Phase 3) — Phase 1 only defines the node shape, nothing constructs or resolves one yet.
     */
    data class JsonXTagged(val name: String, val args: List<JsonXElement>) : JsonXElement()
}
