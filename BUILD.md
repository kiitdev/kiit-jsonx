# kiit-jsonx — Build Guide

All Gradle commands below are run from the **repository root**.

> Publishing (Maven Central / npm) isn't wired up yet — that's Phase 4 of the jsonx plan (see
> `_prd/jsonx` in the workspace root). This guide only covers building and testing locally.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK  | 17+     | `java -version` to verify |

## Build

```bash
# Stop the Gradle daemon (useful after changing env vars or upgrading Gradle)
./gradlew --stop

# Clean build outputs
./gradlew :kiit-jsonx:clean

# Compile the JVM target
./gradlew :kiit-jsonx:build

# Compile only — no tests
./gradlew :kiit-jsonx:assemble

# Run the Kotlin sample app
./gradlew :samples:sample-kotlin:run
```

## Test

```bash
# JVM tests (commonTest, run on the JVM)
./gradlew :kiit-jsonx:jvmTest

# Lint
./gradlew :kiit-jsonx:ktlintCheck
./gradlew :kiit-jsonx:detekt
```

## Conformance testing

`JsonParser` is validated against [`nst/JSONTestSuite`](https://github.com/nst/JSONTestSuite), a
third-party corpus of JSON parsing edge cases maintained by Nicolas Seriot.

- **Source**: https://github.com/nst/JSONTestSuite, `test_parsing/` subdirectory, commit
  `1ef36fa01286573e846ac449e8683f8833c5b26a` (2024-11-22).
- **License**: MIT. The upstream `LICENSE` file is vendored alongside the fixtures (see below) —
  it covers the fixture files, not kiit-jsonx itself (which remains Apache 2.0).
- **Vendored, unmodified, at**: `kiit-jsonx/src/jvmTest/resources/conformance/json/` — 318
  `*.json` fixtures copied verbatim from upstream, plus `LICENSE` and a `VENDORED.md` noting the
  same source/commit/license info. Nothing in that directory is hand-edited; it stays isolated
  from kiit-jsonx's own test fixtures so the vendored data stays auditable against upstream.
- **Harness**: `kiit.jsonx.conformance.JsonConformanceTest` (JVM-only, since reading fixture
  files off disk needs real file I/O — same reason `kiit-codes` keeps `JavaInteropTest` under
  `jvmTest` rather than `commonTest`). Runs as part of `./gradlew :kiit-jsonx:jvmTest`.
- **Naming convention** (upstream's): `y_*.json` must parse successfully, `n_*.json` must fail,
  `i_*.json` is implementation-defined (either outcome accepted, not asserted).
- **Known, deliberate deviations**: a small set of fixtures where kiit-jsonx intentionally
  disagrees with upstream's expected outcome — documented in the harness itself
  (`JsonConformanceTest.knownDeviations`), each pointing at the `JsonParserTest` case that locks
  in the actual intended behavior (empty/whitespace-only input parsing as an empty object;
  duplicate object keys rejected by the default `ParseOptions.duplicateKeyPolicy`).

## Publishing

Not available yet. `kiit-jsonx/build.gradle.kts` doesn't apply the
`com.vanniktech.maven.publish` plugin in Phase 1 — no `publishToMavenLocal` or Maven Central
task exists until Phase 4 wires it up (see `_prd/jsonx` in the workspace root).
