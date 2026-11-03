import kotlinx.serialization.json.*
import java.util.Base64
import kotlin.test.*

class AppIssueTest {
    @Test fun workflowTokenOnlyAuthenticatesTargetRepositoryWrites() {
        val repository = "QinFrance/KosherHub"
        for (path in listOf("repos/FossifyOrg/Calculator/releases/latest", "repos/cfopuser/app-store", "repos/$repository/contents/data/catalog.json?ref=main")) {
            val request = githubIssueHttpRequest("test-token", repository, path, "GET", null)
            assertEquals("api.github.com", request.uri().host)
            assertTrue(request.headers().firstValue("Authorization").isEmpty)
        }
        for (method in listOf("POST", "PATCH")) {
            val request = githubIssueHttpRequest("test-token", repository, "repos/$repository/git/refs", method, JsonObject(emptyMap()))
            assertEquals("Bearer test-token", request.headers().firstValue("Authorization").orElse(null))
        }
        for (path in listOf("repos/other/repo/git/refs", "repos/QinFrance/KosherHub-extra/git/refs", "repos/QinFrance/KosherHub%2Fother/git/refs")) {
            assertFailsWith<IllegalArgumentException> { githubIssueHttpRequest("test-token", repository, path, "POST", JsonObject(emptyMap())) }
        }
    }

    @Test fun acceptsOnlyTheExactGitHubRepositoryField() {
        assertEquals("owner/app", appIssueUpstream("### Upstream repository\n\nhttps://github.com/owner/app/\n\n### Why\nUseful"))
        for (input in listOf(
            "### Upstream repository\nhttps://evil.example/owner/app",
            "### Upstream repository\nhttps://github.com.evil.example/owner/app",
            "### Upstream repository\nhttps://github.com/owner/app/releases/latest",
            "### Upstream repository\nhttps://github.com/owner/app; touch /tmp/anything",
            "### Upstream repository\nhttps://github.com/owner/app?token=example"
        )) assertNull(appIssueUpstream(input), input)
    }

    @Test fun proposedAppNeverAuthorizesBinaryMirroring() {
        val api = FakeIssueApi()
        validateAppIssue(event(), "community/hub", api)
        val tree = api.calls.single { it.path.endsWith("/git/trees") }.body!!["tree"]!!.jsonArray
        fun changed(path: String) = catalogJson.parseToJsonElement(tree.single { it.jsonObject["path"]!!.jsonPrimitive.content == path }.jsonObject["content"]!!.jsonPrimitive.content)
        val source = changed("data/app-sources.json").jsonArray.single().jsonObject
        assertFalse(source["mirrorApproved"]!!.jsonPrimitive.boolean)
        assertEquals("owner-app", source["id"]!!.jsonPrimitive.content)
        val assetPattern = Regex(source["assetPattern"]!!.jsonPrimitive.content)
        assertTrue(assetPattern.matches("stable.app+[1].apk"))
        assertFalse(assetPattern.matches("stableXapp+[1]Xapk"))
        val record = changed("data/requests/issue-7.json").jsonObject
        assertEquals("needs-maintainer-review", record["status"]!!.jsonPrimitive.content)
        assertFalse(record["mirrorApproved"]!!.jsonPrimitive.boolean)
        assertTrue(api.calls.all { it.path.startsWith("repos/") })
        assertTrue(api.calls.none { it.path.contains("/releases/download/") })
        assertTrue(api.calls.any { it.path.endsWith("/pulls") })
    }

    @Test fun policyBlockedPullRequestKeepsAReviewBranch() {
        val api = FakeIssueApi(blockPullRequests = true)
        validateAppIssue(event(), "community/hub", api)
        val comments = api.calls.filter { it.path.endsWith("/comments") }.map { it.body!!.toString() }
        assertTrue(comments.any { "compare/main...app-request-7" in it })
        assertTrue(api.calls.any { it.method == "PATCH" && it.path.endsWith("/heads/app-request-7") })
    }

    @Test fun aReleaseWithoutAnApkDoesNotCreateAReviewBranch() {
        val api = FakeIssueApi(hasApk = false)
        validateAppIssue(event(), "community/hub", api)
        assertFalse(api.calls.any { it.path.contains("/git/") })
        assertTrue(api.calls.last().body.toString().contains("No APK release was found"))
    }

    private fun event() = catalogJson.parseToJsonElement("""{"issue":{"number":7,"body":"### Upstream repository\n\nhttps://github.com/owner/app\n"}}""").jsonObject

    private data class Call(val path: String, val method: String, val body: JsonObject?)
    private class FakeIssueApi(private val blockPullRequests: Boolean = false, private val hasApk: Boolean = true) : AppIssueApi {
        val calls = mutableListOf<Call>()
        override fun request(path: String, method: String, body: JsonObject?): JsonObject {
            calls += Call(path, method, body)
            val response = when {
                path == "repos/owner/app" -> """{"full_name":"owner/app","name":"app","html_url":"https://github.com/owner/app","private":false,"license":{"spdx_id":"MIT"}}"""
                path.endsWith("/releases/latest") -> if (hasApk) """{"html_url":"https://github.com/owner/app/releases/tag/v1","tag_name":"v1","assets":[{"name":"stable.app+[1].apk","browser_download_url":"https://github.com/owner/app/releases/download/v1/stable.apk","size":42}]}""" else """{"assets":[]}"""
                path.endsWith("/git/ref/heads/main") -> """{"object":{"sha":"base"}}"""
                path.endsWith("/git/commits/base") -> """{"tree":{"sha":"base-tree"}}"""
                path.contains("/contents/") -> {
                    val content = if (path.contains("catalog.json")) """{"apps":[],"devices":[],"notices":[]}""" else "[]"
                    """{"content":"${Base64.getEncoder().encodeToString(content.toByteArray())}"}"""
                }
                path.endsWith("/git/trees") -> """{"sha":"new-tree"}"""
                path.endsWith("/git/commits") -> """{"sha":"new-commit"}"""
                path.endsWith("/pulls") -> {
                    if (blockPullRequests) throw GitHubApiFailure(403)
                    """{"html_url":"https://github.com/community/hub/pull/1"}"""
                }
                else -> "{}"
            }
            return catalogJson.parseToJsonElement(response).jsonObject
        }
    }
}
