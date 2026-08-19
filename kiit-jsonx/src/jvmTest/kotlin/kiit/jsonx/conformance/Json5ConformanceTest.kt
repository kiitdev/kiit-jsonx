package kiit.jsonx.conformance

import kiit.jsonx.parser.json5.Json5Parser
import kiit.result.Failure
import kiit.result.Success
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.AbstractConstruct
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.Tag
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Runs [Json5Parser] against two independently authored JSON5 conformance corpora, vendored
 * under `src/jvmTest/resources/conformance/json5/` (see each subdirectory's `VENDORED.md`):
 *
 * 1. [json5Tests_conformance] — `json5/json5-tests`, whose file *extension* signals expected
 *    behavior (`.json`/`.json5` must parse, `.js`/`.txt` must fail; `.errorSpec` is metadata,
 *    not a fixture).
 * 2. [jjuPortableBasicTier_conformance] — `rlidwka/jju`'s portable YAML suite, filtered to its
 *    `basic` tier (every JSON5 parser should pass these; `advanced`/`extra` are out of scope).
 *
 * Both harnesses only assert accept/reject, the same bar [JsonConformanceTest] uses, not exact
 * decoded values — matching upstream's own "should this parse or not" framing.
 */
class Json5ConformanceTest {
    @Test
    fun json5Tests_conformance() {
        val fixtures =
            resourceDir("conformance/json5/json5-tests")
                .walkTopDown()
                .filter { it.isFile }
                .toList()

        val failures = mutableListOf<String>()
        for (file in fixtures.sortedBy { it.path }) {
            val relativeName = file.name
            if (relativeName in json5TestsKnownDeviations) continue

            val outcome =
                when (file.extension) {
                    "json", "json5" -> Expect.Success
                    "js", "txt" -> Expect.Failure
                    else -> null // .errorSpec, LICENSE.md, README.md, VENDORED.md, .editorconfig, ...
                } ?: continue

            val result = Json5Parser.parse(file.readText(Charsets.UTF_8))
            when (outcome) {
                Expect.Success -> if (result !is Success) failures += "$relativeName: expected to parse but failed"
                Expect.Failure -> if (result !is Failure) failures += "$relativeName: expected to fail but parsed"
            }
        }

        if (failures.isNotEmpty()) {
            fail("${failures.size}/${fixtures.size} json5-tests conformance failures:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun jjuPortableBasicTier_conformance() {
        val yamlText = resourceDir("conformance/json5/jju-portable").resolve("portable-json5-tests.yaml").readText()
        val cases = loadPortableSuite(sanitizeForStandardYaml(yamlText))
        val basicCases = cases.filter { it.type == "basic" }
        check(basicCases.isNotEmpty()) { "expected at least one 'basic' tier case in the portable suite" }

        val failures = mutableListOf<String>()
        for (case in basicCases) {
            val result = Json5Parser.parse(case.input)
            when {
                case.expectsError && result !is Failure -> failures += "${case.name}: expected to fail but parsed"
                !case.expectsError && result !is Success -> failures += "${case.name}: expected to parse but failed"
            }
        }

        if (failures.isNotEmpty()) {
            fail("${failures.size}/${basicCases.size} jju portable-suite (basic) failures:\n${failures.joinToString("\n")}")
        }
    }

    private enum class Expect { Success, Failure }

    private data class PortableCase(val name: String, val type: String?, val input: String, val expectsError: Boolean)

    /** The `!error` marker distinguishes "should fail" from a legitimate `output: null` case. */
    private object ErrorMarker

    /**
     * `yamlConstructors` is `protected` on [Constructor], so registering `!error` has to happen
     * from inside an actual subclass body, not via `.apply { }` on an instance from outside.
     */
    private class PortableSuiteConstructor : Constructor(LoaderOptions()) {
        init {
            yamlConstructors[Tag("!error")] =
                object : AbstractConstruct() {
                    override fun construct(node: Node): Any = ErrorMarker
                }
        }
    }

    /**
     * SnakeYAML (unlike jju's own js-yaml-based test runner) rejects `\/` as an unrecognized
     * escape inside a double-quoted YAML scalar — valid in JSON, not standard YAML. The only
     * place this appears in the vendored file is a single `output:` value; `input:` fields use
     * YAML's `>` block-scalar style, which isn't escape-processed at all, so this fix is scoped
     * to `output:` lines only and can never touch a test's actual input content. It only affects
     * what the (unused, for accept/reject purposes) `output` value decodes to in memory, never
     * the file on disk.
     */
    private fun sanitizeForStandardYaml(yamlText: String): String =
        yamlText.lineSequence().joinToString("\n") { line ->
            if (line.trimStart().startsWith("output:")) line.replace("\\/", "/") else line
        }

    private fun loadPortableSuite(yamlText: String): List<PortableCase> {
        val yaml = Yaml(PortableSuiteConstructor())

        @Suppress("UNCHECKED_CAST")
        val root = yaml.load<Map<String, Map<String, Any?>>>(yamlText)

        return root.map { (name, entry) ->
            PortableCase(
                name = name,
                type = entry["type"] as String?,
                input = entry["input"] as String,
                expectsError = entry["output"] === ErrorMarker,
            )
        }
    }

    /**
     * Fixtures where kiit-jsonx deliberately disagrees with upstream's expected outcome, each a
     * decided design choice rather than a bug:
     *
     * 1. `empty.txt`, `top-level-block-comment.txt`, `top-level-inline-comment.txt`: a comment
     *    is insignificant to the lexer, so a document that's only a comment (or nothing at all)
     *    reduces to empty input, which parses as an empty object here (Milestone 2.2's design),
     *    not a syntax error. Same deviation category as [JsonConformanceTest]'s
     *    `n_structure_no_data.json`.
     * 2. `duplicate-keys.json`: the default `ParseOptions.duplicateKeyPolicy` is `Error`,
     *    rejecting duplicate object keys. Same deviation as [JsonConformanceTest]'s
     *    `y_object_duplicated_key.json` — a caller wanting RFC-permissive handling can pass a
     *    different policy.
     * 3. `unicode-escaped-unquoted-key.json5`: a `\uXXXX` escape inside an unquoted identifier
     *    (e.g. `sigΣma:`) isn't supported. Already documented as a deliberate simplification
     *    in `Json5Lexer.isIdentifierStart`'s KDoc, not something this conformance run newly found.
     */
    private val json5TestsKnownDeviations =
        setOf(
            "empty.txt",
            "top-level-block-comment.txt",
            "top-level-inline-comment.txt",
            "duplicate-keys.json",
            "unicode-escaped-unquoted-key.json5",
        )

    private fun resourceDir(path: String): File {
        val url = javaClass.classLoader.getResource(path) ?: fail("$path not found on test classpath")
        return File(url.toURI())
    }
}
