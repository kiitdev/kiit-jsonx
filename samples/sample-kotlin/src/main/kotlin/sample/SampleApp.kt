package sample

import kiit.codes.Err
import kiit.codes.Invalid
import kiit.jsonx.element.JsonXElement
import kiit.jsonx.element.JsonXElement.JsonXArray
import kiit.jsonx.element.JsonXElement.JsonXBoolean
import kiit.jsonx.element.JsonXElement.JsonXNull
import kiit.jsonx.element.JsonXElement.JsonXNumber
import kiit.jsonx.element.JsonXElement.JsonXObject
import kiit.jsonx.element.JsonXElement.JsonXString
import kiit.jsonx.error.JsonXError
import kiit.jsonx.error.JsonXResult
import kiit.jsonx.parser.state.LexerState
import kiit.result.Failure
import kiit.result.Success

/**
 * Phase 1 of kiit-jsonx has no parser yet (see ../../_prd/jsonx) — just the [JsonXElement] tree
 * shape, [JsonXResult]/[JsonXError] error handling, and [LexerState]'s position tracking. This
 * sample hand-builds a tree to show that shape end to end. A later phase will replace
 * [sampleConfig] with an actual `JsonX.parse(...)` call over real source text.
 */
fun main() {
    val config = sampleConfig()

    report("host", findString(config, "host"))
    report("port", findNumber(config, "port"))
    report("missing", findString(config, "missing"))
    report("wrong type", findString(config, "port"))

    println()
    demonstrateLexerState()
}

private fun sampleConfig(): JsonXObject =
    JsonXObject(
        linkedMapOf(
            "host" to JsonXString("localhost"),
            "port" to JsonXNumber.of(8080L),
            "debug" to JsonXBoolean(true),
            "timeout" to JsonXNull,
            "tags" to JsonXArray(listOf(JsonXString("dev"), JsonXString("local"))),
        ),
    )

private fun findString(obj: JsonXObject, key: String): JsonXResult<String> =
    when (val value = obj.entries[key]) {
        is JsonXString -> Success(value.value)
        null -> Failure(JsonXError(Err.on(key, "", "key not found")), Invalid.MISSING_FIELD)
        else -> Failure(JsonXError(Err.on(key, value.toString(), "expected a string")), Invalid.INVALID_VALUE)
    }

private fun findNumber(obj: JsonXObject, key: String): JsonXResult<JsonXNumber> =
    when (val value = obj.entries[key]) {
        is JsonXNumber -> Success(value)
        null -> Failure(JsonXError(Err.on(key, "", "key not found")), Invalid.MISSING_FIELD)
        else -> Failure(JsonXError(Err.on(key, value.toString(), "expected a number")), Invalid.INVALID_VALUE)
    }

private fun report(label: String, result: JsonXResult<*>) {
    result.fold(
        onSuccess = { println("$label -> $it") },
        onFailure = { error -> println("$label -> failed: ${error.err.message}") },
    )
}

/** Walks a small source string with [LexerState] to show offset/line/column tracking in action. */
private fun demonstrateLexerState() {
    val source = "{\n  \"a\": 1\n}"
    val state = LexerState(source)

    println("walking source:")
    println(source.replace("\n", "\\n"))

    while (!state.isAtEnd()) {
        val before = state.snapshot()
        val char = state.advance()
        val label = if (char == '\n') "\\n" else char.toString()
        println("  consumed '$label' at offset=${before.offset} line=${before.line} column=${before.column}")
    }

    println("final position: ${state.snapshot()}")
}
