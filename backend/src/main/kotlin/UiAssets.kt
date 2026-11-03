import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

private const val composeAssetsRoot = "frontend/src/commonMain/composeResources/"
private const val spotifySourceSha = "07d50e3c8417774c57f097f1e5cf44c9c1d0b45b2b711122a18b266155d172ae"
// Java 21 ImageIO's PNG encoding of the pinned, otherwise unmodified Spotify JPEG.
private const val spotifyJavaPngSha = "3821c90b88c9e84abc45ed9e603cd2fde8aba2ef774102b890b57547e70eb6c7"
private val assetDigest = Regex("[a-f0-9]{64}")
private val assetGitSha = Regex("[a-f0-9]{40}")
private val uiAssetHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
    .followRedirects(HttpClient.Redirect.NEVER).build()
private fun JsonObject.assetText(key: String) = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.assetInt(key: String) = get(key)?.jsonPrimitive?.intOrNull ?: error("Missing asset size: $key")

internal data class UiAssetPlan(
    val resourceName: String,
    val sourceUrl: String,
    val outputPath: String,
    val sourceSha256: String,
    val sourceSize: Int,
    val outputSha256: Set<String>,
    val gitBlobSha: String,
    val width: Int = 0,
    val height: Int = 0,
    val convertJpeg: Boolean = false,
    val font: Boolean = false
)

private data class IconSource(val repository: String, val path: String, val fixedRef: String = "")
private val allowedUiIcons = buildMap {
    put("simple-keyboard", IconSource("rkkr/simple-keyboard", "metadata/en-US/images/icon.png", "149"))
    put("fossify-calculator", IconSource("FossifyOrg/Calculator", "fastlane/metadata/android/en-US/images/icon.png", "1.4.0"))
    for (id in listOf("bezeq", "bit", "egg", "hopon", "mizrahi", "osmand", "spotify", "termux", "waze", "whatsapp", "yahav")) {
        put(id, IconSource("cfopuser/app-store", "apps/$id/icon.png"))
    }
    put("meld", IconSource("FrancescoGrazioso/Meld", "fastlane/metadata/android/en-US/images/icon.png"))
    put("metrolist", IconSource("MetrolistGroup/Metrolist", "fastlane/metadata/android/en-US/images/icon.png"))
    put("sealplus", IconSource("MaheshTechnicals/Sealplus", "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"))
}

private fun checkedUiAssetUrl(url: String, expectedPath: String) {
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "raw.githubusercontent.com" && uri.port == -1 &&
        uri.userInfo == null && uri.fragment == null && uri.query == null && uri.path == expectedPath) {
        "UI asset URL is outside its reviewed source path"
    }
}

internal fun uiIconPlan(record: JsonObject): UiAssetPlan {
    val id = record.assetText("id")
    val approved = requireNotNull(allowedUiIcons[id]) { "Unreviewed app icon" }
    val resource = "app_${id.replace('-', '_')}"
    require(record.assetText("resourceName") == resource)
    require(record.assetText("repository") == approved.repository && record.assetText("path") == approved.path)
    val ref = if (approved.fixedRef.isNotBlank()) {
        require(record.assetText("ref") == approved.fixedRef)
        approved.fixedRef
    } else record.assetText("resolvedCommit").also { require(assetGitSha.matches(it)) { "UI assets require a pinned commit" } }
    val url = record.assetText("pinnedSourceUrl").ifBlank { record.assetText("downloadUrl") }
    checkedUiAssetUrl(url, "/${approved.repository}/$ref/${approved.path}")
    val outputPath = "${composeAssetsRoot}drawable/$resource.png"
    require(record.assetText("localResourcePath") == outputPath) { "Unsafe UI asset output path" }
    val sourceSha = record.assetText("sourceSha256").also { require(assetDigest.matches(it)) }
    val outputSha = record.assetText("sha256").also { require(assetDigest.matches(it)) }
    val sourceSize = record.assetInt("sourceSizeBytes").also { require(it in 1..1_000_000) }
    val blob = record.assetText("expectedGitBlobSha").also { require(assetGitSha.matches(it)) }
    val converted = record["conversion"]?.let { it !is JsonNull } ?: false
    if (converted) require(id == "spotify" && sourceSha == spotifySourceSha) { "Only the reviewed Spotify JPEG may be transcoded" }
    else require(sourceSha == outputSha) { "PNG artwork must be preserved byte for byte" }
    val width = record.assetInt("width").also { require(it in 1..1024) }
    val height = record.assetInt("height").also { require(it in 1..1024) }
    return UiAssetPlan(resource, url, outputPath, sourceSha, sourceSize,
        if (converted) setOf(outputSha, spotifyJavaPngSha) else setOf(outputSha), blob, width, height, converted)
}

internal fun uiFontPlan(record: JsonObject): UiAssetPlan {
    require(record.assetText("resourceName") == "catalog_sans" && record.assetText("sourceRepository") == "google/fonts")
    val commit = record.assetText("sourceCommit").also { require(assetGitSha.matches(it)) }
    val sourcePath = "ofl/notosanshebrew/NotoSansHebrew[wdth,wght].ttf"
    require(record.assetText("sourcePath") == sourcePath)
    val url = record.assetText("sourceUrl")
    checkedUiAssetUrl(url, "/google/fonts/$commit/$sourcePath")
    val outputPath = "${composeAssetsRoot}font/catalog_sans.ttf"
    require(record.assetText("localResourcePath") == outputPath)
    val hash = record.assetText("sha256").also { require(assetDigest.matches(it)) }
    val blob = record.assetText("gitBlobSha").also { require(assetGitSha.matches(it)) }
    val size = record.assetInt("sizeBytes").also { require(it in 1..1_000_000) }
    return UiAssetPlan("catalog_sans", url, outputPath, hash, size, setOf(hash), blob, font = true)
}

private fun uiGitBlobSha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(
    "blob ${bytes.size}\u0000".toByteArray() + bytes
).joinToString("") { "%02x".format(it) }

internal fun prepareUiAsset(plan: UiAssetPlan, projectRoot: File, download: (String, Int) -> ByteArray): Boolean {
    val root = projectRoot.canonicalFile
    val output = File(root, plan.outputPath).canonicalFile
    require(output.toPath().startsWith(root.toPath()) && output.path != root.path) { "UI asset output escapes the project" }
    if (output.isFile && output.length() <= 1_000_000 && sha256(output.readBytes()) in plan.outputSha256) return false
    val bytes = download(plan.sourceUrl, 1_000_000)
    require(bytes.size == plan.sourceSize && sha256(bytes) == plan.sourceSha256 && uiGitBlobSha(bytes) == plan.gitBlobSha) {
        "UI asset no longer matches its pinned source"
    }
    val prepared = if (plan.font) {
        require(bytes.size >= 4 && bytes.take(4) == listOf(0.toByte(), 1.toByte(), 0.toByte(), 0.toByte())) { "Invalid TrueType font" }
        bytes
    } else {
        val image = requireNotNull(ImageIO.read(ByteArrayInputStream(bytes))) { "Invalid app image" }
        require(image.width == plan.width && image.height == plan.height)
        if (plan.convertJpeg) {
            require(bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte())
            ByteArrayOutputStream().use { out ->
                require(ImageIO.write(image, "png", out))
                out.toByteArray()
            }
        } else {
            require(bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map { it.toByte() }) { "App icon must be PNG" }
            bytes
        }
    }
    require(sha256(prepared) in plan.outputSha256) { "UI asset output checksum changed" }
    output.parentFile.mkdirs()
    val temporary = Files.createTempFile(output.parentFile.toPath(), ".${output.name}.", ".tmp")
    try {
        Files.write(temporary, prepared)
        Files.move(temporary, output.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } finally { Files.deleteIfExists(temporary) }
    return true
}

private fun downloadUiAsset(url: String, limit: Int): ByteArray {
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "raw.githubusercontent.com" && uri.userInfo == null && uri.port == -1)
    val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build()
    val pending = uiAssetHttp.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
    val response = try { pending.orTimeout(30, TimeUnit.SECONDS).join() }
        catch (error: Exception) { pending.cancel(true); throw IllegalStateException("UI asset request failed", error) }
    require(response.statusCode() == 200) { "UI asset source returned HTTP ${response.statusCode()}" }
    require(response.body().size <= limit) { "UI asset exceeds its size limit" }
    return response.body()
}

fun prepareUiAssets(projectRoot: File = File("."), download: (String, Int) -> ByteArray = ::downloadUiAsset) {
    val icons = catalogJson.parseToJsonElement(File(projectRoot, "resources/icons/app-icons.json").readText()).jsonObject
    require(icons["schemaVersion"]?.jsonPrimitive?.int == 1)
    val iconPlans = icons["icons"]!!.jsonArray.map { uiIconPlan(it.jsonObject) }
    require(iconPlans.size == allowedUiIcons.size && iconPlans.map { it.resourceName }.toSet().size == allowedUiIcons.size)
    val font = catalogJson.parseToJsonElement(File(projectRoot, "resources/fonts/catalog_sans.json").readText()).jsonObject
    val plans = iconPlans + uiFontPlan(font)
    val prepared = plans.count { prepareUiAsset(it, projectRoot, download) }
    println("Verified ${plans.size} pinned UI assets; downloaded $prepared, retained ${plans.size - prepared} matching local files.")
}
