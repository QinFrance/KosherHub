import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

// Issue text is data. This command only contacts GitHub's API; it never runs or downloads APKs.
private val repositoryName = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
private val upstreamSection = Regex("(?m)^### Upstream repository[\\t ]*\\r?\\n\\s*https://github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)/?[\\t ]*(?:\\r?\\n|$)")
internal fun appIssueUpstream(body: String): String? = upstreamSection.find(body)?.groupValues?.get(1)
private fun JsonObject.text(key: String): String = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
private fun jsonObject(vararg values: Pair<String, JsonElement>): JsonObject = JsonObject(mapOf(*values))
private fun String.json() = JsonPrimitive(this)

internal interface AppIssueApi {
    fun request(path: String, method: String = "GET", body: JsonObject? = null): JsonObject
}

internal class GitHubApiFailure(val status: Int) : IllegalStateException("GitHub request returned $status")

private class GitHubAppIssueApi(private val token: String) : AppIssueApi {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER).build()

    override fun request(path: String, method: String, body: JsonObject?): JsonObject {
        require(path.startsWith("repos/") && !path.contains("..") && !path.contains('#'))
        val uri = URI("https://api.github.com/$path")
        require(uri.scheme == "https" && uri.host == "api.github.com" && uri.userInfo == null)
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Content-Type", "application/json")
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it.toString()) }
                ?: HttpRequest.BodyPublishers.noBody()).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) throw GitHubApiFailure(response.statusCode())
        require(response.body().length <= 8_000_000) { "GitHub response is too large" }
        return if (response.statusCode() == 204) JsonObject(emptyMap())
        else catalogJson.parseToJsonElement(response.body()).jsonObject
    }
}

internal fun validateAppIssue(event: JsonObject, repository: String, api: AppIssueApi) {
    require(repositoryName.matches(repository)) { "Invalid workflow repository" }
    val issue = event["issue"]!!.jsonObject
    val number = issue["number"]!!.jsonPrimitive.int
    require(number > 0)
    fun comment(body: String) {
        api.request("repos/$repository/issues/$number/comments", "POST", jsonObject("body" to body.json()))
    }
    val upstream = appIssueUpstream(issue.text("body"))
    if (upstream == null) {
        comment("Please use the app template and provide the original public GitHub repository. No APK files or code were executed.")
        return
    }
    val meta = api.request("repos/$upstream")
    val canonical = "https://github.com/$upstream"
    require(meta.text("full_name").equals(upstream, ignoreCase = true) &&
        meta.text("html_url").equals(canonical, ignoreCase = true) &&
        meta["private"]?.jsonPrimitive?.booleanOrNull == false) { "Upstream must be the original public GitHub repository" }
    val release = api.request("repos/$upstream/releases/latest")
    val assets = release["assets"]?.jsonArray?.map { it.jsonObject }.orEmpty()
    val apkAssets = assets.filter { it.text("name").endsWith(".apk") }
    if (apkAssets.isEmpty()) {
        comment("No APK release was found upstream. This request needs a source review before it can be listed.")
        return
    }
    val asset = apkAssets.firstOrNull { it.text("name").contains("stable", ignoreCase = true) } ?: apkAssets.first()
    val releaseUrl = release.text("html_url")
    val assetUrl = asset.text("browser_download_url")
    require(releaseUrl.startsWith("$canonical/releases/tag/") && assetUrl.startsWith("$canonical/releases/download/")) {
        "Release metadata points outside the upstream repository"
    }
    val license = meta["license"]?.takeUnless { it is JsonNull }?.jsonObject?.text("spdx_id")?.ifBlank { "NOASSERTION" } ?: "NOASSERTION"
    val tag = release.text("tag_name").also { require(it.isNotBlank()) }
    val record = jsonObject(
        "issue" to JsonPrimitive(number), "repository" to meta.text("html_url").json(),
        "license" to license.json(), "release" to releaseUrl.json(), "tag" to tag.json(),
        "mirrorApproved" to JsonPrimitive(false), "status" to "needs-maintainer-review".json()
    )
    val mainSha = api.request("repos/$repository/git/ref/heads/main")["object"]!!.jsonObject.text("sha")
    val baseCommit = api.request("repos/$repository/git/commits/$mainSha")
    val branch = "app-request-$number"
    try {
        api.request("repos/$repository/git/refs", "POST", jsonObject("ref" to "refs/heads/$branch".json(), "sha" to mainSha.json()))
    } catch (error: GitHubApiFailure) {
        if (error.status !in setOf(409, 422)) throw error
        comment("A review branch already exists for this issue. Please update that pull request rather than creating duplicate requests.")
        return
    }
    fun readJson(path: String): JsonElement {
        val file = api.request("repos/$repository/contents/$path?ref=main")
        val decoded = Base64.getMimeDecoder().decode(file.text("content")).toString(StandardCharsets.UTF_8)
        return catalogJson.parseToJsonElement(decoded)
    }
    val catalog = readJson("data/catalog.json").jsonObject
    val sources = readJson("data/app-sources.json").jsonArray
    val apps = catalog["apps"]!!.jsonArray
    val existing = apps.map { it.jsonObject }.firstOrNull { it.text("repository").equals(canonical, ignoreCase = true) }
    val id = existing?.text("id") ?: upstream.lowercase().replace(Regex("[^a-z0-9]+"), "-")
    val encodedTag = URLEncoder.encode(tag, StandardCharsets.UTF_8).replace("+", "%20")
    val app = JsonObject(existing.orEmpty() + mapOf(
        "id" to id.json(), "name" to (existing?.text("name") ?: meta.text("name")).json(),
        "description" to (existing?.text("description") ?: "A community-requested Android app.").json(),
        "category" to (existing?.text("category") ?: "Community").json(),
        "repository" to canonical.json(), "license" to license.json(), "version" to tag.json(),
        "downloadUrl" to assetUrl.json(), "sourceArchive" to "$canonical/archive/refs/tags/$encodedTag.tar.gz".json(),
        "sha256" to asset.text("digest").removePrefix("sha256:").json(),
        "bytes" to JsonPrimitive(asset["size"]?.jsonPrimitive?.longOrNull ?: 0),
        "status" to "official-release".json(),
        "notes" to "Proposed through GitHub. Inclusion is not kosher certification. No binary mirroring is authorized by this request.".json()
    ))
    val nextApps = if (existing == null) apps + app else apps.map { if (it.jsonObject.text("id") == id) app else it }
    val nextCatalog = JsonObject(catalog + ("apps" to JsonArray(nextApps)))
    val nextSources = if (sources.any { it.jsonObject.text("repository").equals(upstream, ignoreCase = true) }) sources
    else JsonArray(sources + jsonObject(
        "id" to id.json(), "repository" to upstream.json(), "license" to license.json(),
        "assetPattern" to Regex.escape(asset.text("name")).json(), "assetName" to asset.text("name").json(),
        "mirrorApproved" to JsonPrimitive(false), "attribution" to "Maintainer review required before mirroring.".json()
    ))
    val changes = listOf("data/requests/issue-$number.json" to record, "data/catalog.json" to nextCatalog, "data/app-sources.json" to nextSources)
    val tree = api.request("repos/$repository/git/trees", "POST", jsonObject(
        "base_tree" to baseCommit["tree"]!!.jsonObject.text("sha").json(),
        "tree" to JsonArray(changes.map { (path, data) -> jsonObject(
            "path" to path.json(), "mode" to "100644".json(), "type" to "blob".json(),
            "content" to (catalogJson.encodeToString(JsonElement.serializer(), data) + "\n").json()
        ) })
    ))
    val commit = api.request("repos/$repository/git/commits", "POST", jsonObject(
        "message" to "Validate app source from issue #$number".json(), "tree" to tree.text("sha").json(),
        "parents" to JsonArray(listOf(mainSha.json())),
        "author" to jsonObject("name" to "kosherhub-bot".json(), "email" to "41898282+github-actions[bot]@users.noreply.github.com".json())
    ))
    api.request("repos/$repository/git/refs/heads/$branch", "PATCH", jsonObject("sha" to commit.text("sha").json(), "force" to JsonPrimitive(false)))
    val pr = try {
        api.request("repos/$repository/pulls", "POST", jsonObject(
            "title" to "Review app source from issue #$number".json(), "head" to branch.json(), "base" to "main".json(),
            "body" to "Automated metadata check for #$number.\n\nUpstream: $canonical\nLicense: $license\nRelease: $releaseUrl\n\nNo APK has been imported or approved. A maintainer must verify suitability, redistribution rights, upstream identity, and the mirror allowlist. Merging this PR publishes the proposed catalog entry; mirroring remains disabled for new apps.".json()
        ))
    } catch (error: GitHubApiFailure) {
        if (error.status !in setOf(403, 422)) throw error
        comment("Metadata validated and review branch created: https://github.com/$repository/compare/main...$branch. Repository policy prevented automatic pull-request creation. No binary was downloaded or approved.")
        return
    }
    comment("Upstream metadata checked. Review prepared: ${pr.text("html_url")}. No automatic approval or certification is implied.")
}

fun main() {
    val event = catalogJson.parseToJsonElement(File(requireNotNull(System.getenv("GITHUB_EVENT_PATH"))).readText()).jsonObject
    validateAppIssue(event, requireNotNull(System.getenv("GITHUB_REPOSITORY")), GitHubAppIssueApi(requireNotNull(System.getenv("GITHUB_TOKEN"))))
}
