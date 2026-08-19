/** url: www.kiit.dev */
package kiit.jsonx.parser.core

/**
 * Tracks which object keys have been seen so far while parsing a single JSON object.
 *
 * This only detects duplicates. Deciding what to do about one (error, keep first, keep last,
 * collect into an array) is the parser's job (Milestone 2.2), driven by
 * `ParseOptions.duplicateKeyPolicy`. Detection is intentionally kept separate from policy so
 * every dialect's parser (JSON, JSON5, jsonx) can share this one mechanism.
 *
 * One instance per object being parsed. Don't share an instance across sibling or nested
 * objects, since each has its own key namespace.
 */
class DuplicateKeyTracker {
    private val seen = mutableSetOf<String>()

    /** Records [key] as seen. Returns true if [key] had already been recorded before this call. */
    fun offer(key: String): Boolean = !seen.add(key)
}
