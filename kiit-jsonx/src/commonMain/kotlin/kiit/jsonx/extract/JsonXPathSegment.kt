/** url: www.kiit.dev */
package kiit.jsonx.extract

/**
 * One `key[i1][i2]...` step in a parsed extraction path, produced by [parseJsonXPath]. [key] is
 * empty only for a leading segment that's pure array indexing (e.g. the first segment of
 * `"[0].host"`), meaning "index directly into the current node, no key lookup first."
 */
internal data class JsonXPathSegment(val key: String, val indices: List<Int>)
