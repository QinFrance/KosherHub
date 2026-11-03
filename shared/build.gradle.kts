plugins { kotlin("multiplatform"); kotlin("plugin.serialization") }
kotlin {
    jvm(); js(IR) { browser() }; @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }
    sourceSets { commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") } }
}
