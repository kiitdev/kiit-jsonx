package kiit.jsonx.conformance

import kiit.jsonx.parser.json.JsonParser
import kiit.result.Failure
import kiit.result.Success
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Runs [JsonParser] against the vendored `nst/JSONTestSuite` fixtures
 * (`src/jvmTest/resources/conformance/json/`, see `VENDORED.md` there), an independently
 * authored conformance corpus, not just our own hand-written [kiit.jsonx.parser.json.JsonParserTest] cases.
 *
 * Naming convention (upstream's, not ours): `y_*.json` must parse successfully, `n_*.json` must
 * fail to parse, `i_*.json` is implementation-defined. Either outcome is acceptable there, so
 * those fixtures are exercised (must not hang or throw anything other than a normal parse
 * failure) but not asserted either way.
 *
 * [knownDeviations] excepts a small, explicit set of fixtures where kiit-jsonx deliberately
 * disagrees with upstream's expected outcome, each already a decided design choice covered by
 * its own [kiit.jsonx.parser.json.JsonParserTest] case, not a parser bug to fix here.
 */
class JsonConformanceTest {
    @Test
    fun jsonTestSuite_conformance() {
        val fixtures =
            fixturesDir().listFiles { file -> file.extension == "json" }
                ?: fail("no fixture files found under ${fixturesDir()}")

        val failures = mutableListOf<String>()
        for (file in fixtures.sortedBy { it.name }) {
            if (file.name in knownDeviations) continue

            val text = file.readText(Charsets.UTF_8)
            val result = JsonParser.parse(text)
            when {
                file.name.startsWith("y_") && result !is Success ->
                    failures += "${file.name}: expected to parse successfully but failed"
                file.name.startsWith("n_") && result !is Failure ->
                    failures += "${file.name}: expected to fail but parsed successfully"
                // i_*.json: implementation-defined, either outcome is fine.
            }
        }

        if (failures.isNotEmpty()) {
            fail("${failures.size}/${fixtures.size} JSONTestSuite conformance failures:\n${failures.joinToString("\n")}")
        }
    }

    private val knownDeviations =
        setOf(
            // Empty/whitespace-only input parses as an empty object (Milestone 2.2's explicit
            // "empty input -> empty JsonXObject" requirement), not a syntax error. Upstream
            // expects these to fail. See JsonParserTest.emptyInput_yieldsEmptyObject /
            // .whitespaceOnlyInput_yieldsEmptyObject.
            "n_structure_no_data.json",
            "n_single_space.json",
            // The default ParseOptions.duplicateKeyPolicy is Error, rejecting duplicate object
            // keys. RFC 8259 says keys SHOULD (not MUST) be unique, so upstream treats these as
            // valid JSON. A caller wanting RFC-permissive handling can pass a different
            // duplicateKeyPolicy. See JsonParserTest.duplicateKey_defaultPolicyIsError.
            "y_object_duplicated_key.json",
            "y_object_duplicated_key_and_value.json",
        )

    private fun fixturesDir(): File {
        val url =
            javaClass.classLoader.getResource("conformance/json")
                ?: fail("conformance/json resource directory not found on test classpath")
        return File(url.toURI())
    }
}
