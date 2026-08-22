package kiit.jsonx.tags.examples

import kiit.codes.Err
import kiit.codes.Invalid
import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.error.JsonXError
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.tags.ExperimentalJsonxTagApi
import kiit.jsonx.tags.TagHandler
import kiit.jsonx.tags.TagKind
import kiit.result.Failure
import kiit.result.Success

/**
 * `@acmecorp.date('2026-08-03')` -> `"2026-08-03T00:00:00Z"`: a small, illustrative
 * consumer-registered tag (Milestone 3.3, task 1). Not production-grade — it exists only to
 * exercise the external-registration and eager-resolution path end-to-end through a real
 * [TagHandler], distinct from the registry-mechanics-only `FakeTag` in `TagRegistryTest`.
 */
@ExperimentalJsonxTagApi
class AcmeDateTagHandler : TagHandler {
    override val name: String = NAME
    override val kind: TagKind = TagKind.Simple

    override fun resolve(args: List<JsonXElement>): JsonXResult<JsonXElement> {
        val date =
            args.singleOrNull() as? JsonXString
                ?: return Failure(
                    JsonXError(Err.of("@acmecorp.date expects a single string argument, got $args")),
                    Invalid.INVALID_VALUE,
                )
        return Success(JsonXString("${date.value}T00:00:00Z"))
    }

    companion object {
        const val NAME: String = "acmecorp.date"
    }
}
