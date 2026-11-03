import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.*

fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
private val permissiveMirrorLicenses = setOf("Apache-2.0", "MIT", "BSD-3-Clause")
private val gplMirrorLicenses = setOf("GPL-3.0", "GPL-3.0-only")
private val sha256Pattern = Regex("[a-f0-9]{64}")

internal fun reviewedMirrorSlug(app: AppRecord, source: AppSource): String {
    require(source.mirrorApproved && source.license in permissiveMirrorLicenses + gplMirrorLicenses)
    require(Regex("[a-z0-9][a-z0-9-]*").matches(app.id)) { "Unsafe app ID" }
    require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(source.repository))
    require(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*").matches(app.version) && !app.version.contains("..")) { "Unsafe release tag" }
    require(app.status == "mirrored" || app.downloadUrl.startsWith("https://github.com/${source.repository}/releases/download/"))
    if (source.license in gplMirrorLicenses) {
        require(source.sourceReviewVersion == app.version) { "This GPL version has no reviewed corresponding-source pair" }
        require(sha256Pattern.matches(source.binarySha256) && sha256Pattern.matches(source.sourceBundleSha256))
        require(source.sourceBundleUrl.startsWith("https://f-droid.org/repo/") && source.sourceBundleUrl.endsWith("_src.tar.gz"))
        require(source.auxiliarySources.isNotEmpty()) { "Reviewed library sources are required" }
    }
    return "${app.id}-${app.version}"
}

internal fun validateApk(bytes: ByteArray) {
    require(bytes.size in 4..32_000_000 && bytes[0] == 80.toByte() && bytes[1] == 75.toByte()) { "Not an APK ZIP container" }
    var entries = 0
    val hasManifest = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        var found = false
        var expandedBytes = 0L
        val buffer = ByteArray(8192)
        while (entries++ < 20_000) {
            val entry = zip.nextEntry ?: break
            if (entry.name == "AndroidManifest.xml") { found = true; break }
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                expandedBytes += count
                require(expandedBytes <= 128_000_000) { "APK expands beyond the inspection limit" }
            }
            zip.closeEntry()
        }
        found
    }
    require(hasManifest) { "APK manifest is missing" }
}

private fun checkedArchive(url: String, expectedSha: String, cache: File): ByteArray {
    val uri = URI(url)
    require(uri.scheme == "https" && uri.userInfo == null && uri.host in setOf("github.com", "f-droid.org"))
    require(url.endsWith(".tar.gz"))
    val bytes = if (cache.exists() && (expectedSha.isBlank() || sha256(cache.readBytes()) == expectedSha)) cache.readBytes()
        else request(url, 32_000_000)
    require(bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) { "Not a gzip source archive" }
    if (expectedSha.isNotBlank()) require(sha256(bytes) == expectedSha) { "Source archive checksum changed" }
    return bytes
}

fun mirrorApps() {
    val catalog = loadCatalog()
    val registry = sources().associateBy { it.id }
    val root = File("frontend/src/jsMain/resources/downloads").apply { mkdirs() }
    val apps = catalog.apps.map { app ->
        val source = registry[app.id] ?: return@map app
        if (!source.mirrorApproved) return@map app
        try {
            val slug = reviewedMirrorSlug(app, source)
            val tag = app.version
            val license = request("https://raw.githubusercontent.com/${source.repository}/$tag/LICENSE").decodeToString()
            require(when (source.license) {
                "Apache-2.0" -> license.contains("Apache License")
                "MIT" -> license.contains("Permission is hereby granted")
                "BSD-3-Clause" -> license.contains("Redistribution")
                in gplMirrorLicenses -> license.contains("GNU GENERAL PUBLIC LICENSE") && license.contains("Version 3")
                else -> false
            }) { "Upstream license no longer matches approval" }
            val assetName = source.assetName.ifBlank { File(URI(app.downloadUrl).path).name }
            require(assetName.isNotBlank() && !assetName.contains('/') && !assetName.contains('\\') && !assetName.contains(".."))
            val apkFile = File(root, "$slug.apk")
            val bytes = if (app.status == "mirrored" && apkFile.exists()) apkFile.readBytes()
                else request("https://github.com/${source.repository}/releases/download/$tag/$assetName", 32_000_000)
            validateApk(bytes)
            val hash = sha256(bytes)
            if (app.sha256.isNotBlank()) require(hash == app.sha256) { "APK checksum changed" }
            if (source.binarySha256.isNotBlank()) require(hash == source.binarySha256) { "APK does not match the reviewed source pair" }

            val sourceFile = File(root, "$slug.SOURCE.tar.gz")
            val sourceUrl = source.sourceBundleUrl.ifBlank { "https://github.com/${source.repository}/archive/refs/tags/$tag.tar.gz" }
            val sourceBytes = checkedArchive(sourceUrl, source.sourceBundleSha256, sourceFile)
            val auxiliary = source.auxiliarySources.map { reviewed ->
                require(Regex("[a-z0-9][a-z0-9.-]*").matches(reviewed.name) && !reviewed.name.contains(".."))
                require(sha256Pattern.matches(reviewed.sha256))
                require(reviewed.url.startsWith("https://github.com/FossifyOrg/Commons/archive/refs/tags/"))
                val file = File(root, "$slug.${reviewed.name}.SOURCE.tar.gz")
                Triple(reviewed, file, checkedArchive(reviewed.url, reviewed.sha256, file))
            }
            // Only publish the binary after the license and all required source archives have passed checks.
            apkFile.writeBytes(bytes)
            sourceFile.writeBytes(sourceBytes)
            auxiliary.forEach { (_, file, content) -> file.writeBytes(content) }
            File(root, "$slug.LICENSE.txt").writeText(license)
            File(root, "$slug.NOTICE.txt").writeText(buildString {
                appendLine(app.name); appendLine("Upstream: ${app.repository}"); appendLine("Version: $tag")
                appendLine(source.attribution); appendLine("License: ${source.license}")
                appendLine("Unmodified upstream APK, redistributed by KosherHub. Original in-APK licenses, notices, and signing certificate are retained.")
                appendLine("No kosher certification is asserted."); appendLine("APK SHA-256: $hash")
                appendLine("Source: $slug.SOURCE.tar.gz"); appendLine("Source provenance: $sourceUrl")
                appendLine("Source SHA-256: ${sha256(sourceBytes)}")
                auxiliary.forEach { (reviewed, file, _) ->
                    appendLine("Library source: ${file.name}"); appendLine("Library provenance: ${reviewed.url}")
                    appendLine("Library SHA-256: ${reviewed.sha256}")
                }
                if (source.license in gplMirrorLicenses) {
                    appendLine("The F-Droid source archive is the source package associated with this developer-signed release; it preserves the application sources, licenses, dependency versions, resources, and build scripts.")
                    appendLine("Fossify Commons source for the exact declared version is also provided alongside this notice. The Gradle build resolves its declared standard public dependencies.")
                    appendLine("Build the fossRelease variant using the Android SDK and the Gradle version declared in the source archive. No signing key is supplied; users may build and sign their own APK.")
                    appendLine("Source/reproducibility record: https://f-droid.org/en/packages/org.fossify.math/ and https://verification.f-droid.org/packages/org.fossify.math/")
                }
            })
            println("Mirrored ${app.id} with checked APK, source archives, license, and attribution.")
            app.copy(license = source.license, downloadUrl = "downloads/$slug.apk", sourceArchive = "downloads/$slug.SOURCE.tar.gz", sha256 = hash,
                bytes = bytes.size.toLong(), status = "mirrored",
                notes = "Unmodified upstream release. License and attribution: downloads/$slug.LICENSE.txt and downloads/$slug.NOTICE.txt. Source archive SHA-256: ${sha256(sourceBytes)}.")
        } catch (error: Exception) {
            println("Mirror skipped for ${app.id}: ${error.message}")
            app
        }
    }
    File("data/catalog.json").writeText(catalogJson.encodeToString(Catalog.serializer(), catalog.copy(apps = apps)))
}
