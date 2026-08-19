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

## Publishing

Not available yet. `kiit-jsonx/build.gradle.kts` doesn't apply the
`com.vanniktech.maven.publish` plugin in Phase 1 — no `publishToMavenLocal` or Maven Central
task exists until Phase 4 wires it up (see `_prd/jsonx` in the workspace root).
