/** url: www.kiit.dev */
package kiit.jsonx.tags.builtin

/**
 * Reads an environment variable, or null if it isn't set. One `expect`/`actual` pair per
 * platform target, kept this narrow (rather than a general-purpose environment API) since
 * [EnvTagHandler] is the only caller jsonx-core has for one.
 */
internal expect fun readEnvironmentVariable(name: String): String?
