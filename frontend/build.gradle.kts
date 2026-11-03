plugins { kotlin("multiplatform"); kotlin("plugin.compose"); id("org.jetbrains.compose") }
kotlin {
    js(IR) { browser { commonWebpackConfig { outputFileName = "kosherhub.js" } }; binaries.executable() }
    sourceSets { jsMain.dependencies { implementation(project(":shared")); implementation(compose.html.core); implementation(compose.runtime); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") } }
}
tasks.named("jsProcessResources") { dependsOn(":backend:exportCatalog") }
