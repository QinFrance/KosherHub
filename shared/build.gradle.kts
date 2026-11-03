plugins { kotlin("multiplatform"); kotlin("plugin.serialization") }
kotlin {
    jvm(); js(IR) { browser() }
    sourceSets { commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") } }
}
