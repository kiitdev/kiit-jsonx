/** url: www.kiit.dev */
package kiit.jsonx.tags.builtin

internal actual fun readEnvironmentVariable(name: String): String? = System.getenv(name)
