import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val MIRROR_REPOSITORY = "QinFrance/KosherHub"
internal const val MAX_RELEASE_ASSET_BYTES = 2_147_483_647L
private val releaseRepositories = setOf("alltechdev/DietDroid", "Noochtech/awesome-qin-f21-pro")
private val downloadHosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")

/** The Actions installation token is scoped to the target repository; public sources need no credential. */
internal fun releaseApiUsesToken(path: String): Boolean {
    val target = path.startsWith("/repos/$MIRROR_REPOSITORY/")
    require(target || releaseRepositories.any { path.startsWith("/repos/$it/") }) { "Release API repository is not approved" }
    return target
}

@Serializable data class ReleaseDeviceRule(val releaseTagPrefix: String, val deviceId: String)
@Serializable data class ReleaseSource(
    val repository: String,
    val permission: String,
    val notice: String,
    val deviceRules: List<ReleaseDeviceRule> = emptyList(),
    val defaultDeviceId: String = ""
)
@Serializable data class FirmwareMirror(
    val sourceRepository: String,
    val sourceReleaseTag: String,
    val sourceReleaseUrl: String,
    val sourceAssetId: Long,
    val sourceAssetName: String,
    val sourceAssetUrl: String,
    val sourceDigest: String,
    val bytes: Long,
    val sha256: String,
    val targetReleaseTag: String,
    val targetAssetId: Long,
    val targetAssetName: String,
    val mirrorUrl: String,
    val mirroredAt: String,
    val deviceIds: List<String> = emptyList(),
    val notice: String = ""
)
@Serializable data class FirmwareMirrorIndex(
    val updated: String = "",
    val repository: String = MIRROR_REPOSITORY,
    val assets: List<FirmwareMirror> = emptyList(),
    val status: String = "partial",
    val failures: List<String> = emptyList()
)
internal data class ReleaseAsset(
    val id: Long, val name: String, val url: String, val size: Long,
    val digest: String = "", val contentType: String = "application/octet-stream", val state: String = "uploaded"
)
private data class PublicRelease(val id: Long, val tag: String, val htmlUrl: String, val assets: List<ReleaseAsset>)
private data class MirrorRelease(val id: Long, val tag: String, val assets: MutableList<ReleaseAsset>)
internal data class CheckedTransfer(val bytes: Long, val sha256: String)

internal fun releaseDigest(value: String): String {
    if (value.isBlank()) return ""
    require(Regex("sha256:[a-fA-F0-9]{64}").matches(value)) { "Release digest is not a valid SHA-256 digest" }
    return value.substringAfter(':').lowercase()
}

internal fun mirroredReleaseTag(repository: String, sourceTag: String): String {
    require(repository in releaseRepositories) { "Release repository is not approved" }
    require(sourceTag.isNotBlank() && sourceTag.length <= 250 && sourceTag.none { it.isISOControl() }) { "Invalid release tag" }
    val safe = sourceTag.replace(Regex("[^A-Za-z0-9._-]"), "-").replace(Regex("\\.{2,}"), ".").trim('.', '-').take(64).ifBlank { "release" }
    val suffix = sha256("$repository\n$sourceTag".toByteArray()).take(12)
    return "mirror-${repository.lowercase().replace('/', '-') }-$safe-$suffix"
}

internal fun mirroredAssetName(id: Long, name: String): String {
    require(id > 0 && name.isNotBlank() && name.length <= 500) { "Invalid release asset" }
    val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "-").replace(Regex("\\.{2,}"), ".").trim('.', '-').take(160).ifBlank { "asset.bin" }
    return "$id--$safe"
}

internal fun checkedPublicAssetUrl(repository: String, url: String): URI {
    require(repository in releaseRepositories || repository == MIRROR_REPOSITORY)
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 && uri.fragment == null)
    require(uri.rawPath.startsWith("/$repository/releases/download/") && uri.rawQuery == null) { "Unexpected release download URL" }
    require(uri.path.split('/').none { it == "." || it == ".." }) { "Release URL contains a path traversal" }
    return uri
}

/** Copies at most one release asset to disk; never loads a firmware image into the JVM heap. */
internal fun copyCheckedRelease(input: InputStream, destination: File, expectedBytes: Long, expectedDigest: String): CheckedTransfer {
    require(expectedBytes in 1..MAX_RELEASE_ASSET_BYTES) { "Asset is outside the GitHub release file limit" }
    val expectedHash = releaseDigest(expectedDigest)
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    destination.outputStream().buffered(256 * 1024).use { output ->
        val buffer = ByteArray(256 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= expectedBytes && total <= MAX_RELEASE_ASSET_BYTES) { "Release exceeds its declared size" }
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
    }
    require(total == expectedBytes) { "Release size does not match upstream metadata" }
    val hash = digest.digest().joinToString("") { "%02x".format(it) }
    require(expectedHash.isEmpty() || expectedHash == hash) { "Release SHA-256 does not match upstream metadata" }
    return CheckedTransfer(total, hash)
}

internal fun verifiedExistingRelease(sourceHash: String, sourceBytes: Long, targetHash: String, targetBytes: Long): Boolean {
    require(sourceBytes == targetBytes) { "An existing mirrored filename has a different size; it will not be replaced" }
    if (sourceHash.isBlank() || targetHash.isBlank()) return false
    require(sourceHash == targetHash) { "An existing mirrored filename has a different SHA-256; it will not be replaced" }
    return true
}

internal fun applyFirmwareMirrors(catalog: Catalog, mirrors: List<FirmwareMirror>): Catalog = catalog.copy(
    devices = catalog.devices.map { device ->
        val applicable = mirrors.filter { device.id in it.deviceIds }
        val resources = device.resources.map { resource ->
            val mirrored = applicable.firstOrNull { it.sourceAssetUrl == resource.url }
            if (mirrored == null) resource else resource.copy(mirrorUrl = mirrored.mirrorUrl, sha256 = mirrored.sha256)
        }.toMutableList()
        applicable.forEach { mirror ->
            if (resources.none { it.mirrorUrl == mirror.mirrorUrl ||
                    (it.mirrorUrl.isNotBlank() && Regex("[a-fA-F0-9]{64}").matches(mirror.sha256) && it.sha256.equals(mirror.sha256, true)) }) {
                val tool = mirror.sourceReleaseTag.contains("tool", true) || mirror.sourceReleaseTag.contains("module", true) ||
                    mirror.sourceAssetName.substringAfterLast('.', "").lowercase() in setOf("exe", "dll", "bat", "sh", "msi", "ps1")
                resources += Resource(mirror.sourceAssetName, mirror.sourceAssetUrl, if (tool) "tools" else "firmware",
                    mirror.sourceRepository, mirror.notice, mirror.mirrorUrl, mirror.sha256)
            }
        }
        // A release can be republished under another filename/repository. Keep every provenance
        // record in the index, but show a verified binary only once in the device's file list.
        val seenHashes = mutableSetOf<String>()
        device.copy(resources = resources.filter { resource ->
            if (resource.mirrorUrl.isNotBlank() && Regex("[a-fA-F0-9]{64}").matches(resource.sha256)) seenHashes.add(resource.sha256.lowercase()) else true
        })
    }
)

/** Authentication is confined to GitHub's API/upload hosts; redirects never carry the token. */
private class ReleaseGitHub(private val token: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NEVER).build()
    private fun builder(uri: URI, authenticated: Boolean = false): HttpRequest.Builder {
        require(uri.scheme == "https" && uri.userInfo == null && uri.port == -1)
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(30))
            .header("User-Agent", "KosherHub-release-mirror/1.0")
        if (authenticated) {
            require(uri.host in setOf("api.github.com", "uploads.github.com")) { "Authentication host is not approved" }
            request.header("Authorization", "Bearer $token").header("X-GitHub-Api-Version", "2022-11-28")
        }
        return request
    }

    fun api(path: String, body: JsonObject? = null, missingAllowed: Boolean = false): JsonElement? {
        require(path.startsWith("/repos/") && !path.contains("..") && !path.contains('#'))
        // Public upstream metadata is deliberately anonymous; the ephemeral token is target-repository scoped.
        val request = builder(URI("https://api.github.com$path"), releaseApiUsesToken(path)).header("Accept", "application/vnd.github+json")
        if (body == null) request.GET() else request.header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        val response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { input ->
            if (response.statusCode() == 404 && missingAllowed) return null
            require(response.statusCode() in 200..299) { "GitHub API returned HTTP ${response.statusCode()} for ${path.substringBefore('?')}" }
            val bytes = input.readNBytes(12_000_001)
            require(bytes.size <= 12_000_000) { "GitHub metadata is too large" }
            return catalogJson.parseToJsonElement(bytes.decodeToString())
        }
    }

    fun pages(path: String): List<JsonObject> {
        val output = mutableListOf<JsonObject>()
        for (page in 1..100) {
            val items = api("$path?per_page=100&page=$page")!!.jsonArray
            output += items.map { it.jsonObject }
            if (items.size < 100) return output
        }
        error("GitHub metadata pagination limit reached")
    }

    fun download(repository: String, asset: ReleaseAsset, destination: File): CheckedTransfer {
        var uri = checkedPublicAssetUrl(repository, asset.url)
        repeat(8) {
            require(uri.scheme == "https" && uri.host in downloadHosts && uri.userInfo == null && uri.port == -1)
            val response = client.send(builder(uri).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() in setOf(301, 302, 303, 307, 308)) {
                response.body().close()
                val location = response.headers().firstValue("Location").orElseThrow { IllegalStateException("Missing release redirect") }
                uri = uri.resolve(location)
            } else {
                if (response.statusCode() != 200) {
                    response.body().close()
                    error("Release download returned HTTP ${response.statusCode()}")
                }
                val input = response.body()
                val timeout = Executors.newSingleThreadScheduledExecutor { command -> Thread(command, "mirror-download-timeout").apply { isDaemon = true } }
                val closing = timeout.schedule({ input.close() }, 30, TimeUnit.MINUTES)
                try {
                    return input.use { copyCheckedRelease(it, destination, asset.size, asset.digest) }
                } finally {
                    closing.cancel(false)
                    timeout.shutdownNow()
                }
            }
        }
        error("Too many release download redirects")
    }

    fun upload(releaseId: Long, name: String, file: File, contentType: String): ReleaseAsset {
        require(releaseId > 0 && Regex("[A-Za-z0-9._-]+").matches(name) && !name.contains(".."))
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val type = contentType.takeIf { Regex("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+").matches(it) } ?: "application/octet-stream"
        val uri = URI("https://uploads.github.com/repos/$MIRROR_REPOSITORY/releases/$releaseId/assets?name=$encodedName")
        val request = builder(uri, true).header("Accept", "application/vnd.github+json").header("Content-Type", type)
            .POST(HttpRequest.BodyPublishers.ofFile(file.toPath())).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { input ->
            require(response.statusCode() == 201) { "Release upload returned HTTP ${response.statusCode()}; no existing asset was overwritten" }
            val result = input.readNBytes(1_000_001)
            require(result.size <= 1_000_000)
            return assetFromJson(catalogJson.parseToJsonElement(result.decodeToString()).jsonObject)
        }
    }
}

private fun JsonObject.string(name: String): String = get(name)?.jsonPrimitive?.contentOrNull ?: ""
private fun assetFromJson(value: JsonObject): ReleaseAsset = ReleaseAsset(
    value["id"]!!.jsonPrimitive.long, value.string("name"), value.string("browser_download_url"), value["size"]!!.jsonPrimitive.long,
    value.string("digest"), value.string("content_type").ifBlank { "application/octet-stream" }, value.string("state")
)

private fun targetRelease(github: ReleaseGitHub, source: ReleaseSource, release: PublicRelease): MirrorRelease {
    val tag = mirroredReleaseTag(source.repository, release.tag)
    val encodedTag = URLEncoder.encode(tag, StandardCharsets.UTF_8)
    val existing = github.api("/repos/$MIRROR_REPOSITORY/releases/tags/$encodedTag", missingAllowed = true)
    val value = (existing ?: github.api("/repos/$MIRROR_REPOSITORY/releases", buildJsonObject {
        put("tag_name", tag); put("target_commitish", "main"); put("name", "${source.repository.substringAfter('/')} · ${release.tag}")
        put("body", "Unmodified mirror of public release assets.\n\nSource: ${release.htmlUrl}\n\n${source.notice}\n\n${source.permission}\n\nProvenance, byte counts and SHA-256 checksums: https://github.com/$MIRROR_REPOSITORY/blob/main/data/firmware-mirrors.json\n\nThese files are device-specific. Their inclusion does not establish kosher certification.")
        put("draft", false); put("prerelease", false); put("make_latest", "false")
    }))!!.jsonObject
    val id = value["id"]!!.jsonPrimitive.long
    return MirrorRelease(id, tag, github.pages("/repos/$MIRROR_REPOSITORY/releases/$id/assets").map(::assetFromJson).toMutableList())
}

object ReleaseMirrorCli {
    @JvmStatic fun main(args: Array<String>) { mirrorReleaseAssets() }
}

fun mirrorReleaseAssets() {
    val token = System.getenv("GITHUB_TOKEN").orEmpty()
    require(token.isNotBlank()) { "GITHUB_TOKEN with repository contents write permission is required" }
    val sources = catalogJson.decodeFromString<List<ReleaseSource>>(File("data/release-sources.json").readText())
    require(sources.isNotEmpty() && sources.all { it.repository in releaseRepositories && it.permission.isNotBlank() && it.notice.isNotBlank() })
    require(sources.map { it.repository }.distinct().size == sources.size)
    val github = ReleaseGitHub(token)
    val indexFile = File("data/firmware-mirrors.json")
    val previous = if (indexFile.exists()) catalogJson.decodeFromString<FirmwareMirrorIndex>(indexFile.readText()) else FirmwareMirrorIndex()
    require(previous.repository == MIRROR_REPOSITORY)
    val mirrors = previous.assets.associateBy { it.sourceRepository to it.sourceAssetId }.toMutableMap()
    val failures = mutableListOf<String>()
    fun checkpoint(final: Boolean = false) {
        val index = FirmwareMirrorIndex(Instant.now().toString(), assets = mirrors.values.sortedWith(compareBy({ it.sourceRepository }, { it.sourceReleaseTag }, { it.sourceAssetName })),
            status = if (failures.isNotEmpty()) "incomplete" else if (final) "complete" else "partial", failures = failures.toList())
        val temporary = File(indexFile.parentFile, "firmware-mirrors.json.tmp")
        temporary.writeText(catalogJson.encodeToString(FirmwareMirrorIndex.serializer(), index))
        Files.move(temporary.toPath(), indexFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    for (source in sources) {
        val releases = try {
            github.pages("/repos/${source.repository}/releases")
                .filter { it["draft"]?.jsonPrimitive?.booleanOrNull != true }
                .map { value ->
                    val id = value["id"]!!.jsonPrimitive.long
                    val assets = github.pages("/repos/${source.repository}/releases/$id/assets").map(::assetFromJson)
                    PublicRelease(id, value.string("tag_name"), value.string("html_url"), assets)
                }
        } catch (error: Exception) {
            val description = "${source.repository}: release metadata is not publicly accessible (${error.message})"
            failures += description
            System.err.println("Source not mirrored: $description. Continuing other approved sources.")
            continue
        }
        println("Checking ${source.repository}: ${releases.size} public releases, ${releases.sumOf { it.assets.size }} assets")
        for (release in releases) {
            if (release.assets.isEmpty()) continue
            val target = targetRelease(github, source, release)
            for (asset in release.assets) {
                try {
                    require(asset.state == "uploaded") { "Source asset upload is incomplete" }
                    require(asset.size in 1..MAX_RELEASE_ASSET_BYTES) { "Asset exceeds GitHub's individual release asset limit" }
                    checkedPublicAssetUrl(source.repository, asset.url)
                    val sourceHash = releaseDigest(asset.digest)
                    val targetName = mirroredAssetName(asset.id, asset.name)
                    val found = target.assets.firstOrNull { it.name == targetName }
                    require(found == null || found.state == "uploaded") { "An incomplete existing asset needs maintainer review; it will not be deleted automatically" }
                    var hash = sourceHash
                    val remote: ReleaseAsset
                    if (found != null && verifiedExistingRelease(sourceHash, asset.size, releaseDigest(found.digest), found.size)) {
                        remote = found
                        println("Verified existing mirror: ${asset.name}")
                    } else {
                        val temporary = Files.createTempFile("kosherhub-release-", ".download").toFile()
                        try {
                            require(temporary.usableSpace > asset.size + 512L * 1024 * 1024) { "Not enough temporary disk space for this asset" }
                            val checked = github.download(source.repository, asset, temporary)
                            hash = checked.sha256
                            if (found != null) {
                                val targetTemporary = Files.createTempFile("kosherhub-existing-", ".download").toFile()
                                try {
                                    require(targetTemporary.usableSpace > found.size + 256L * 1024 * 1024)
                                    val existingHash = github.download(MIRROR_REPOSITORY, found, targetTemporary).sha256
                                    require(verifiedExistingRelease(hash, checked.bytes, existingHash, found.size))
                                    remote = found
                                } finally { targetTemporary.delete() }
                            } else {
                                val uploaded = github.upload(target.id, targetName, temporary, asset.contentType)
                                require(uploaded.name == targetName && uploaded.size == checked.bytes && uploaded.state == "uploaded") { "Uploaded release metadata differs from the verified file" }
                                val uploadedHash = releaseDigest(uploaded.digest)
                                require(uploadedHash.isBlank() || uploadedHash == hash) { "Uploaded release digest differs from the verified file" }
                                target.assets += uploaded
                                remote = uploaded
                                println("Mirrored ${asset.name} (${checked.bytes} bytes), SHA-256 $hash")
                            }
                        } finally { temporary.delete() }
                    }
                    checkedPublicAssetUrl(MIRROR_REPOSITORY, remote.url)
                    val device = source.deviceRules.firstOrNull { release.tag.startsWith(it.releaseTagPrefix, true) }?.deviceId ?: source.defaultDeviceId
                    val previousRecord = mirrors[source.repository to asset.id]
                    mirrors[source.repository to asset.id] = FirmwareMirror(source.repository, release.tag, release.htmlUrl,
                        asset.id, asset.name, asset.url, asset.digest, asset.size, hash, target.tag, remote.id, remote.name, remote.url,
                        previousRecord?.mirroredAt ?: Instant.now().toString(), listOfNotNull(device.takeIf { it.isNotBlank() }), source.notice)
                    checkpoint()
                } catch (error: Exception) {
                    val description = "${source.repository} / ${release.tag} / ${asset.name}: ${error.message}"
                    failures += description
                    System.err.println("Mirror not published: $description")
                }
            }
        }
    }
    checkpoint(final = true)
    require(failures.isEmpty()) { "${failures.size} release assets need review; verified mirrors were checkpointed. No existing assets were overwritten." }
    println("Verified ${mirrors.size} hosted firmware assets with SHA-256 provenance.")
}
