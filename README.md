# kiit-jsonx

Extensible JSON library for configuration — a Kotlin Multiplatform tree parser supporting strict
JSON, JSON5, a jsonx superset dialect (triple-quoted strings, typed `@tag(args)` literals), and
JsonL (line-delimited JSON).

> **Status: Phase 1 (Foundation).** There is no parser yet. This phase ships the `JsonxElement`
> tree type, shared lexer position tracking, and the error model every later phase builds on.
> See [`_prd/jsonx`](../_prd/jsonx) in the workspace root for the full PRD and phased plan.

## What's here today

- `JsonxElement` — the sealed tree type (`JsonxObject`, `JsonxArray`, `JsonxString`,
  `JsonxNumber`, `JsonxBoolean`, `JsonxNull`, `JsonxTagged`) shared by every dialect.
- `LexerState` / `SourcePosition` — offset/line/column tracking, exposed for later dialect
  lexers (Phase 2) to build on.
- `JsonxError` / `JsonxResult<T>` / `JsonxException` — the error model, built on
  [`kiit-codes`](https://github.com/kiitdev/kiit-codes)' `Err`/`Status` taxonomy and
  [`kiit-result`](https://github.com/kiitdev/kiit-result)'s `Result<T, E>`.
- `ParseOptions` — stubbed with `duplicateKeyPolicy` and `retainComments`.

Not yet implemented: actual JSON/JSON5/jsonx/JsonL parsing (Phase 2), the extraction API and tag
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
