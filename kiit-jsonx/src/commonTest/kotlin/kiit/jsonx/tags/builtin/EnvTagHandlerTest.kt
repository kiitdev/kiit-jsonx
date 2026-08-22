package kiit.jsonx.tags.builtin

import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.tags.ExperimentalJsonxTagApi
import kiit.jsonx.tags.TagKind
import kiit.result.Failure
import kiit.result.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =================================================================================================
// EnvTagHandlerTest: @env resolution, end to end (registration, argument validation, resolution)
// =================================================================================================
@OptIn(ExperimentalJsonxTagApi::class)
class EnvTagHandlerTest {
    private val handler = EnvTagHandler()

    @Test
    fun name_isEnv() {
        assertEquals("env", handler.name)
    }

    @Test
    fun kind_isSimple() {
        assertEquals(TagKind.Simple, handler.kind)
    }

    @Test
    fun resolve_setVariable_returnsItsValue() {
        // PATH is set in effectively every real process, on every platform this module targets.
        val result = handler.resolve(listOf(JsonXString("PATH")))
        assertTrue(result is Success)
        assertTrue(result.value is JsonXString)
    }

    @Test
    fun resolve_unsetVariable_fails() {
        val result = handler.resolve(listOf(JsonXString("KIIT_JSONX_DEFINITELY_UNSET_VAR_12345")))
        assertTrue(result is Failure)
    }

    @Test
    fun resolve_noArguments_fails() {
        val result = handler.resolve(emptyList())
        assertTrue(result is Failure)
    }

    @Test
    fun resolve_tooManyArguments_fails() {
        val result = handler.resolve(listOf(JsonXString("PATH"), JsonXString("EXTRA")))
        assertTrue(result is Failure)
    }

    @Test
    fun resolve_nonStringArgument_fails() {
        val result = handler.resolve(listOf(JsonXNumber.of(1L)))
        assertTrue(result is Failure)
    }
}
