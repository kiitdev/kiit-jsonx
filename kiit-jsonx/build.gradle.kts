plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

kotlin {
    jvm {
        compilerOptions {
            // JVM 21: kiit-codes/kiit-result ship JVM-21 inline bytecode (for sealed
            // exhaustiveness via PermittedSubclasses), so consumers must target 21+ too.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    // Android/JS/iOS targets are deferred to Phase 4 of the jsonx plan (see _prd/jsonx) —
    // Phase 1 is JVM-only by design.

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: JsonxError wraps kiit-codes' Err, and JsonxResult is a
            // typealias over kiit-result's Result — both types are part of kiit-jsonx's own
            // public API surface, so consumers need them transitively on their classpath.
            api("dev.kiit:kiit-codes:1.0.1")
            api("dev.kiit:kiit-result:1.0.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            // Only for reading the vendored jju portable-suite YAML — see Json5ConformanceTest.
            implementation(libs.snakeyaml)
        }
    }
}

detekt {
    config.setFrom("$projectDir/detekt.yml")
    buildUponDefaultConfig = true
    source.setFrom("src/commonMain/kotlin")
}

// jvmTest's compiled classes target JVM 21 bytecode (see the jvm{} block above) — run them on a
// matching JVM. Auto-provisioned via the foojay resolver (see settings.gradle.kts) if a JDK 21
// isn't already installed locally.
tasks.named<Test>("jvmTest") {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}
