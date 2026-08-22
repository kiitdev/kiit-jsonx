/** url: www.kiit.dev */
package kiit.jsonx.transform

internal actual fun readEnvironmentVariable(name: String): String? = System.getenv(name)
