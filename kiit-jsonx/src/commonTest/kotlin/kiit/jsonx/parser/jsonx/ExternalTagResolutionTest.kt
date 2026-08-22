package kiit.jsonx.parser.jsonx

import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.element.JsonXElement.JsonXTagged
import kiit.jsonx.error.JsonXParseException
import kiit.jsonx.options.ParseOptions
import kiit.jsonx.tags.ExperimentalJsonxTagApi
import kiit.jsonx.tags.examples.AcmeDateTagHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// =================================================================================================
// ExternalTagResolutionTest: Milestone 3.3 — an external, consumer-registered tag exercised
// end-to-end through the full parser, plus nested-tag composition across eager/deferred handlers.
// =================================================================================================
@OptIn(ExperimentalJsonxTagApi::class)
class ExternalTagResolutionTest {
    private fun optionsWithAcmeDate(): ParseOptions {
        return ParseOptions().apply { tagRegistry.register(AcmeDateTagHandler()) }
    }

    private fun parseDocument(text: String, options: ParseOptions = ParseOptions()): JsonXElement =
        JsonXParser(JsonXLexer(text), options).parseDocument()

    @Test
    fun externalTag_registeredOnOptions_resolvesEagerlyThroughFullParser() {
        val result = parseDocument("@acmecorp.date('2026-08-03')", optionsWithAcmeDate())
        assertEquals(JsonXString("2026-08-03T00:00:00Z"), result)
    }

    @Test
    fun externalTag_unregistered_staysAsRawTaggedNode() {
        // Same call, but on a fresh ParseOptions where nothing was registered — the tag name
        // alone doesn't make it eager, registry membership does.
        val result = parseDocument("@acmecorp.date('2026-08-03')")
        assertEquals(JsonXTagged("acmecorp.date", listOf(JsonXString("2026-08-03"))), result)
    }

    @Test
    fun externalTag_resolutionFailure_raisesParseExceptionThroughFullParser() {
        assertFailsWith<JsonXParseException> {
            parseDocument("@acmecorp.date(1)", optionsWithAcmeDate())
        }
    }

    // --- nested-tag composition: no special-casing needed, ordinary recursive-descent handles it --

    @Test
    fun nestedEagerTags_innerResolvesBeforeOuterSeesItsArgs() {
        val result = parseDocument("@acmecorp.date(@acmecorp.date('2026-08-03'))", optionsWithAcmeDate())
        assertEquals(JsonXString("2026-08-03T00:00:00ZT00:00:00Z"), result)
    }

    @Test
    fun deferredTagNestedInsideEagerTagsArgument_isResolvedButStaysUnresolvedItself() {
        // @table is eager and registered by default; @env is deliberately never registered, so
        // it must survive untouched as a raw JsonXTagged node embedded inside @table's own
        // (already-resolved) output.
        val source = "@table({names: ['host'], rows: [[@env('DB_HOST')]]})"
        val result = parseDocument(source) as JsonXArray
        val expected =
            JsonXArray(
                listOf(
                    JsonXObject(linkedMapOf("host" to JsonXTagged("env", listOf(JsonXString("DB_HOST"))))),
                ),
            )
        assertEquals(expected, result)
    }

    @Test
    fun eagerTagNestedInsideAnotherEagerTagsArgument_bothResolve() {
        // A @table cell that is itself an eagerly-resolved external tag: the inner call resolves
        // first (ordinary parseValue recursion), so @table sees a plain JsonXString cell value.
        val source = "@table({names: ['effective'], rows: [[@acmecorp.date('2026-08-03')]]})"
        val result = parseDocument(source, optionsWithAcmeDate()) as JsonXArray
        val expected =
            JsonXArray(
                listOf(
                    JsonXObject(linkedMapOf("effective" to JsonXString("2026-08-03T00:00:00Z"))),
                ),
            )
        assertEquals(expected, result)
    }
}
