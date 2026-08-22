package kiit.jsonx.transform

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.options.EnvAccessPolicy
import kiit.jsonx.options.ParseOptions
import kiit.jsonx.parser.jsonx.JsonXParser
import kiit.result.Failure
import kiit.result.Success
import kiit.result.flatMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =================================================================================================
// TransformPipelineTest: end-to-end parse -> resolveEnv -> resolveRefs -> merge, in that order
// =================================================================================================
class TransformPipelineTest {
    private fun parse(text: String): JsonXResult<JsonXElement> = JsonXParser.parse(text, ParseOptions())

    @Test
    fun pipeline_refPointingAtAnEnvResolvedValue_onlyWorksInEnvThenRefOrder() {
        // "alias" only resolves correctly if @env has already turned "host" into a plain string
        // by the time resolveRefs runs @ref's whole-tree lookup against it.
        val source = "{host: @env('DB_HOST'), alias: @ref('host')}"
        val lookup: (String) -> String? = { name -> if (name == "DB_HOST") "prod-db.internal" else null }

        val result =
            parse(source)
                .flatMap { resolveEnv(it, EnvAccessPolicy.AllowAll, lookup) }
                .flatMap { resolveRefs(it) }

        val expected =
            JsonXObject(
                linkedMapOf(
                    "host" to JsonXString("prod-db.internal"),
                    "alias" to JsonXString("prod-db.internal"),
                ),
            )
        assertEquals(Success(expected), result)
    }

    @Test
    fun pipeline_envDenied_shortCircuitsBeforeResolveRefsRuns() {
        val source = "{host: @env('DB_HOST'), alias: @ref('host')}"
        val result =
            parse(source)
                .flatMap { resolveEnv(it, EnvAccessPolicy.Deny) }
                .flatMap { resolveRefs(it) }
        assertTrue(result is Failure)
    }

    @Test
    fun pipeline_mergeRunsLastAcrossTwoResolvedFiles() {
        val base = "{database: {host: @env('DB_HOST'), port: 5432}}"
        val override = "{database: {host: @env('DB_HOST_OVERRIDE')}}"
        val lookup: (String) -> String? =
            { name -> mapOf("DB_HOST" to "base-host", "DB_HOST_OVERRIDE" to "override-host")[name] }

        val resolvedBase = parse(base).flatMap { resolveEnv(it, EnvAccessPolicy.AllowAll, lookup) }.flatMap { resolveRefs(it) }
        val resolvedOverride = parse(override).flatMap { resolveEnv(it, EnvAccessPolicy.AllowAll, lookup) }.flatMap { resolveRefs(it) }

        val merged = (resolvedBase as Success).value.let { b -> (resolvedOverride as Success).value.let { o -> merge(b, o) } }
        val expected =
            JsonXObject(
                linkedMapOf(
                    "database" to
                        JsonXObject(
                            linkedMapOf(
                                "host" to JsonXString("override-host"),
                                "port" to JsonXNumber.of(5432L),
                            ),
                        ),
                ),
            )
        assertEquals(expected, merged)
    }
}
