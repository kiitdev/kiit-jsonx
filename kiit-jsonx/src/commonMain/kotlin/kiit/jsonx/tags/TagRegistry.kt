/** url: www.kiit.dev */
package kiit.jsonx.tags

import kiit.jsonx.tags.builtin.TableTagHandler

/**
 * Which [TagHandler] resolves which tag name, scoped to one instance rather than a shared
 * global: each [kiit.jsonx.options.ParseOptions] carries its own registry, so registering a tag
 * for one parse can never leak into another's.
 *
 * 1. Every instance starts pre-seeded with the always-on built-ins that are resolved eagerly
 *    (`@table`, for now) — a consumer registering one external tag shouldn't also have to
 *    re-register those to get them back. `@env` and `@ref` are deliberately *not* in this
 *    registry at all: they're always-on tags too, but resolving them is a post-parse transform's
 *    job (`resolveEnv`/`resolveRefs`, security- and whole-tree-context-gated respectively), never
 *    an eager parse-time lookup. `JsonXParser` treats "unregistered" as the signal to leave a
 *    tag as a raw `JsonXTagged` node for that later stage, so `@env`/`@ref` simply never being
 *    registered here is what makes them deferred, no separate flag needed.
 * 2. [register] is mutating, not copy-returning: `TagRegistry().apply { register(MyTag()) }` is
 *    the intended shape, matching the PRD's own example.
 * 3. [find]/[register] require [ExperimentalJsonxTagApi] opt-in since both surface a
 *    [TagHandler]; [contains] doesn't, since it only answers a yes/no question by name.
 */
class TagRegistry {
    @OptIn(ExperimentalJsonxTagApi::class)
    private val handlers: MutableMap<String, TagHandler> = mutableMapOf(TableTagHandler.NAME to TableTagHandler())

    fun contains(name: String): Boolean = name in handlers

    @ExperimentalJsonxTagApi
    fun find(name: String): TagHandler? = handlers[name]

    /**
     * Registers an external tag [handler]. [TagHandler.name] must be `namespace.tag`; `kiit` and
     * `jsonx` are reserved namespaces built-ins already use, so external registrations can't
     * shadow or collide with them. Re-registering the same name replaces the previous handler.
     *
     * @throws IllegalArgumentException if [TagHandler.name] has no namespace prefix, or its
     * namespace is reserved.
     */
    @ExperimentalJsonxTagApi
    fun register(handler: TagHandler) {
        val name = handler.name
        require('.' in name) { "external tag '$name' must be namespaced as 'namespace.tag'" }

        val namespace = name.substringBefore('.')
        require(namespace !in RESERVED_NAMESPACES) {
            "namespace '$namespace' is reserved and can't be used for an external tag"
        }

        handlers[name] = handler
    }

    companion object {
        private val RESERVED_NAMESPACES = setOf("kiit", "jsonx")
    }
}
