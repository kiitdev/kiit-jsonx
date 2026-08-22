/** url: www.kiit.dev */
package kiit.jsonx.transform

/**
 * Platform hook for reading a single process environment variable, used only by [resolveEnv].
 * Never called during parsing itself — see [kiit.jsonx.options.EnvAccessPolicy] for why.
 */
internal expect fun readEnvironmentVariable(name: String): String?
