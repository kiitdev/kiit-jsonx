/** url: www.kiit.dev */
package kiit.jsonx.transform

import kiit.codes.Err
import kiit.codes.Invalid
import kiit.codes.Rejected
import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.error.JsonXError
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.extract.find
import kiit.result.Failure
import kiit.result.Success

private const val REF_TAG_NAME = "ref"

/**
 * Resolves every `@ref('path.to.value')` occurrence in [tree] via whole-tree lookup against
 * [find] — the same path grammar the extraction API uses — with cycle detection: a chain that
 * revisits a path it's already in the middle of resolving fails with [Rejected.CONFLICT] instead
 * of recursing forever.
 *
 * 1. Resolved targets are memoized by path, so a value referenced from multiple places is only
 *    looked up and resolved once.
 * 2. A target that is itself another `@ref` is chased directly. A target that is a compound
 *    object/array is resolved recursively too (including any further nested `@ref`s inside it),
 *    so the value spliced in at the reference site is always fully resolved, never a
 *    partially-resolved copy of the target subtree.
 */
fun resolveRefs(tree: JsonXElement): JsonXResult<JsonXElement> {
    return resolveRefsNode(tree, tree, emptyList(), HashMap())
}

private fun resolveRefsNode(
    tree: JsonXElement,
    node: JsonXElement,
    stack: List<String>,
    cache: HashMap<String, JsonXElement>,
): JsonXResult<JsonXElement> {
    return node.mapTagged { tagged ->
        if (tagged.name != REF_TAG_NAME) return@mapTagged Success(tagged)
        val path =
            (tagged.args.singleOrNull() as? JsonXString)?.value
                ?: return@mapTagged invalidRefArg()
        resolveRefsPath(tree, path, stack, cache)
    }
}

private fun resolveRefsPath(
    tree: JsonXElement,
    path: String,
    stack: List<String>,
    cache: HashMap<String, JsonXElement>,
): JsonXResult<JsonXElement> {
    cache[path]?.let { return Success(it) }
    if (path in stack) {
        val cycle = (stack + path).joinToString(" -> ")
        return Failure(JsonXError(Err.of("cycle detected while resolving @ref: $cycle")), Rejected.CONFLICT)
    }

    val raw =
        try {
            tree.find(path)
        } catch (e: IllegalArgumentException) {
            return Failure(JsonXError(Err.of("@ref has a malformed path '$path': ${e.message}")), Invalid.INVALID_VALUE)
        } ?: return Failure(JsonXError(Err.of("@ref target not found: '$path'")), Rejected.NOT_EXISTS)

    val nextStack = stack + path
    val resolved =
        if (raw is JsonXTagged && raw.name == REF_TAG_NAME) {
            val nextPath = (raw.args.singleOrNull() as? JsonXString)?.value ?: return invalidRefArg()
            resolveRefsPath(tree, nextPath, nextStack, cache)
        } else {
            resolveRefsNode(tree, raw, nextStack, cache)
        }

    if (resolved is Success) cache[path] = resolved.value
    return resolved
}

private fun invalidRefArg(): JsonXResult<Nothing> =
    Failure(JsonXError(Err.of("@ref requires a single string path argument")), Invalid.INVALID_VALUE)
