import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
plugins { kotlin("multiplatform"); kotlin("plugin.compose"); id("org.jetbrains.compose") }
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser { commonWebpackConfig { outputFileName = "kosherhub.js" } }; binaries.executable() }
    sourceSets {
        wasmJsMain {
            resources.srcDir("src/jsMain/resources")
            dependencies {
                implementation(project(":shared"))
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation("org.jetbrains.compose.material3:material3:1.9.0-alpha04")
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
            }
        }
    }
}
tasks.named("wasmJsProcessResources") { dependsOn(":backend:exportCatalog") }
compose.resources { packageOfResClass = "org.kosherhub.resources"; generateResClass = always }
