# kiit-jsonx

Extensible JSON library for configuration — a Kotlin Multiplatform tree parser supporting strict
JSON, JSON5, a jsonx superset dialect (triple-quoted strings, typed `@tag(args)` literals), and
JsonL (line-delimited JSON).

> **Status: Phase 2 (Lexer & Parser), Milestone 2.2 done.** Strict JSON parses end to end. JSON5
> and the jsonx dialect aren't implemented yet. See [`_prd/jsonx`](../_prd/jsonx) in the
> workspace root for the full PRD and phased plan.

## What's here today

- `JsonXElement` — the sealed tree type (`JsonXObject`, `JsonXArray`, `JsonXString`,
  `JsonXNumber`, `JsonXBoolean`, `JsonXNull`, `JsonXTagged`) shared by every dialect.
- `JsonLexer` / `JsonParser` / `JsonDecoder` — a full strict RFC 8259 JSON parser:
  `JsonParser.parse(text)` returns a `JsonXResult<JsonXElement>`. Validated against the
  [`nst/JSONTestSuite`](https://github.com/nst/JSONTestSuite) conformance corpus — see
  [Conformance testing](./BUILD.md#conformance-testing) in BUILD.md for details and licensing.
- `LexerState` / `SourcePosition` / `Token` / `TokenType` (`parser.core`) — shared lexer/token
  infrastructure other dialect lexers (JSON5, jsonx) will build on top of, not reimplement.
- `JsonXError` / `JsonXResult<T>` / `JsonXException` — the error model, built on
  [`kiit-codes`](https://github.com/kiitdev/kiit-codes)' `Err`/`Status` taxonomy and
  [`kiit-result`](https://github.com/kiitdev/kiit-result)'s `Result<T, E>`.
- `ParseOptions` — `duplicateKeyPolicy` (default `Error`; also `LastWins`/`FirstWins`/
  `CollectIntoArray`) and a `retainComments` flag placeholder.

Not yet implemented: JSON5/jsonx/JsonL parsing (rest of Phase 2), the extraction API and tag
mechanism (Phase 3), serialization and multiplatform targets beyond JVM (Phase 4).

## Modules

| Module | Purpose |
|---|---|
| `kiit-jsonx` | The library itself |
| `samples/sample-kotlin` | A runnable JVM sample demonstrating the tree type and error model |

## Build & test

```bash
./gradlew :kiit-jsonx:build
./gradlew :kiit-jsonx:jvmTest
./gradlew :samples:sample-kotlin:run
```

See [BUILD.md](./BUILD.md) for details.

## License

Apache 2.0 — see [LICENSE](./LICENSE).
