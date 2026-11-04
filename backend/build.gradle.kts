plugins { kotlin("jvm"); kotlin("plugin.serialization"); application }
kotlin { jvmToolchain(21) }
application { mainClass.set("MainKt") }
dependencies {
    implementation(project(":shared"))
    implementation("io.ktor:ktor-server-core-jvm:3.3.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.3.1")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.3.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.3.1")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("ch.qos.logback:logback-classic:1.5.20")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.3.1")
}
tasks.test { useJUnitPlatform() }
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
tasks.register<JavaExec>("validateAppIssue") {
    dependsOn("classes")
    mainClass.set("AppIssueKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}
tasks.register<JavaExec>("exportCatalog") {
    dependsOn("classes")
    mainClass.set("MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    args("--export")
    workingDir = rootProject.projectDir
    inputs.file(rootProject.file("data/catalog.json"))
    inputs.file(rootProject.file("data/sections.json")).optional()
    inputs.file(rootProject.file("data/firmware-mirrors.json")).optional()
    outputs.file(rootProject.file("frontend/src/jsMain/resources/catalog.json"))
}
