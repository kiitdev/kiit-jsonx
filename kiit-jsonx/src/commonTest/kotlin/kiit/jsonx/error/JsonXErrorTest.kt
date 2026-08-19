package kiit.jsonx.error

import kiit.codes.Err
import kiit.codes.Invalid
import kiit.result.Failure
import kiit.result.Success
import kiit.result.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

// =================================================================================================
// JsonXErrorTest: JsonXError, JsonXResult, and JsonXException
// =================================================================================================
class JsonXErrorTest {
    @Test
    fun jsonXError_defaultsPositionFieldsToNull() {
        val error = JsonXError(Err.of("unexpected token"))
        assertNull(error.line)
        assertNull(error.column)
        assertNull(error.offset)
        assertNull(error.file)
    }

    @Test
    fun jsonXError_equalsByAllFields() {
        val a = JsonXError(Err.of("bad token"), line = 3, column = 5, offset = 42, file = "config.jsonx")
        val b = JsonXError(Err.of("bad token"), line = 3, column = 5, offset = 42, file = "config.jsonx")
        val differentPosition = a.copy(line = 4)

        assertEquals(a, b)
        assertNotEquals(a, differentPosition)
    }

    @Test
    fun jsonXResult_success_foldsToSuccessBranch() {
        val result: JsonXResult<Int> = Success(42)
        assertEquals(42, result.getOrElse { -1 })
    }

    @Test
    fun jsonXResult_failure_carriesJsonXError() {
        val error = JsonXError(Err.on("port", "abc", "expected a number"), line = 1, column = 8)
        val result: JsonXResult<Int> = Failure(error, Invalid.INVALID_VALUE)

        val recovered =
            result.fold(
                onSuccess = { null },
                onFailure = { it },
            )
        assertEquals(error, recovered)
    }

    @Test
    fun jsonXException_messageComesFromWrappedErr() {
        val error = JsonXError(Err.of("unexpected end of input"))
        val exception = JsonXException(error)

        assertEquals("unexpected end of input", exception.message)
        assertEquals(error, exception.error)
    }

    @Test
    fun jsonXException_retainsOptionalCause() {
        val cause = IllegalStateException("root cause")
        val exception = JsonXException(JsonXError(Err.of("wrapped")), cause)

        assertEquals(cause, exception.cause)
    }
}
