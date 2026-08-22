/** url: www.kiit.dev */
package kiit.jsonx.extract

/**
 * Parses a dotted extraction path with optional bracket array indices, e.g.
 * `"database.servers[0].host"` or the bare `"[0]"`. Used by `find` (`JsonXFind.kt`) and every
 * typed getter built on it.
 *
 * 1. Grammar: `path := segment ("." segment)*`, `segment := key? ("[" digits "]")*`.
 * 2. A malformed path (an unterminated `[`, a non-digit index, or a `.` with nothing before/
 *    after it) throws [IllegalArgumentException] immediately. That's a bug in the path string
 *    itself, not a "nothing there" outcome, which is what a null `find` result means instead.
 */
internal fun parseJsonXPath(path: String): List<JsonXPathSegment> {
    if (path.isEmpty()) return emptyList()

    val segments = mutableListOf<JsonXPathSegment>()
    var i = 0
    while (i < path.length) {
        val keyStart = i
        while (i < path.length && path[i] != '.' && path[i] != '[') i++
        val key = path.substring(keyStart, i)
        val indices = readIndices(path, i) { i = it }

        require(key.isNotEmpty() || indices.isNotEmpty()) {
            "invalid path '$path': empty segment at index $keyStart"
        }
        segments.add(JsonXPathSegment(key, indices))

        if (i < path.length) {
            require(path[i] == '.') { "invalid path '$path': expected '.' at index $i" }
            i++
            require(i < path.length) { "invalid path '$path': trailing '.' with nothing after it" }
        }
    }
    return segments
}

/** Reads zero or more `[digits]` suffixes starting at [start], reporting the new position via [onAdvance]. */
private inline fun readIndices(path: String, start: Int, onAdvance: (Int) -> Unit): List<Int> {
    var i = start
    val indices = mutableListOf<Int>()
    while (i < path.length && path[i] == '[') {
        i++
        val digitsStart = i
        while (i < path.length && path[i] in '0'..'9') i++
        require(i > digitsStart) { "invalid path '$path': expected digits after '[' at index $digitsStart" }
        indices.add(path.substring(digitsStart, i).toInt())
        require(i < path.length && path[i] == ']') { "invalid path '$path': expected ']' at index $i" }
        i++
    }
    onAdvance(i)
    return indices
}
