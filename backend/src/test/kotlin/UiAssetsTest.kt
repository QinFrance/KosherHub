import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.*

class UiAssetsTest {
    private fun iconRecord() = catalogJson.parseToJsonElement("""{
        "id":"simple-keyboard","resourceName":"app_simple_keyboard","repository":"rkkr/simple-keyboard",
        "ref":"149","path":"metadata/en-US/images/icon.png",
        "downloadUrl":"https://raw.githubusercontent.com/rkkr/simple-keyboard/149/metadata/en-US/images/icon.png",
        "localResourcePath":"frontend/src/commonMain/composeResources/drawable/app_simple_keyboard.png",
        "sourceSha256":"${"a".repeat(64)}","sha256":"${"a".repeat(64)}","sourceSizeBytes":42,
        "expectedGitBlobSha":"${"b".repeat(40)}","width":1,"height":1,"conversion":null
    }""").jsonObject

    @Test fun manifestMayOnlyUseReviewedRepositoriesAndExactOutputPaths() {
        val record = iconRecord()
        assertEquals("app_simple_keyboard", uiIconPlan(record).resourceName)
        for ((key, value) in listOf(
            "downloadUrl" to "https://evil.example/icon.png",
            "downloadUrl" to "https://raw.githubusercontent.com.evil.example/rkkr/simple-keyboard/149/metadata/en-US/images/icon.png",
            "repository" to "attacker/icon",
            "localResourcePath" to "../../outside.png",
            "localResourcePath" to "frontend/src/commonMain/composeResources/drawable/../../outside.png",
            "ref" to "main"
        )) assertFailsWith<IllegalArgumentException> { uiIconPlan(JsonObject(record + (key to JsonPrimitive(value)))) }
    }

    @Test fun pinnedSourcesAreVerifiedAndMatchingCachedOutputsNeedNoDownload() {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()
        val gitSha = MessageDigest.getInstance("SHA-1").digest("blob ${bytes.size}\u0000".toByteArray() + bytes).joinToString("") { "%02x".format(it) }
        val plan = uiIconPlan(iconRecord()).copy(sourceSize = bytes.size, sourceSha256 = sha256(bytes), outputSha256 = setOf(sha256(bytes)), gitBlobSha = gitSha)
        val root = Files.createTempDirectory("kosherhub-ui-assets-test").toFile()
        try {
            var downloads = 0
            assertTrue(prepareUiAsset(plan, root) { _, _ -> downloads++; bytes })
            assertContentEquals(bytes, File(root, plan.outputPath).readBytes())
            assertFalse(prepareUiAsset(plan, root) { _, _ -> error("Matching cache must not download") })
            assertEquals(1, downloads)
            assertFailsWith<IllegalArgumentException> { prepareUiAsset(plan.copy(outputPath = "../outside.png"), root) { _, _ -> bytes } }
            assertFailsWith<IllegalArgumentException> { prepareUiAsset(plan.copy(outputPath = "bad-hash.png", sourceSha256 = "f".repeat(64)), root) { _, _ -> bytes } }
        } finally { root.deleteRecursively() }
    }

    @Test fun arbitraryTranscodingIsNotPermitted() {
        val record = iconRecord()
        assertFailsWith<IllegalArgumentException> { uiIconPlan(JsonObject(record + ("conversion" to JsonPrimitive("JPEG to PNG")))) }
        assertFailsWith<IllegalArgumentException> { uiIconPlan(JsonObject(record + ("sha256" to JsonPrimitive("f".repeat(64))))) }
    }

    @Test fun fontIsLimitedToThePinnedGoogleFontAndExactOutput() {
        val font = catalogJson.parseToJsonElement("""{
            "resourceName":"catalog_sans","sourceRepository":"google/fonts","sourceCommit":"${"a".repeat(40)}",
            "sourcePath":"ofl/notosanshebrew/NotoSansHebrew[wdth,wght].ttf",
            "sourceUrl":"https://raw.githubusercontent.com/google/fonts/${"a".repeat(40)}/ofl/notosanshebrew/NotoSansHebrew%5Bwdth%2Cwght%5D.ttf",
            "localResourcePath":"frontend/src/commonMain/composeResources/font/catalog_sans.ttf",
            "sha256":"${"b".repeat(64)}","gitBlobSha":"${"c".repeat(40)}","sizeBytes":42
        }""").jsonObject
        assertTrue(uiFontPlan(font).font)
        for ((key, value) in listOf("sourceRepository" to "attacker/fonts", "sourceCommit" to "main",
            "sourceUrl" to "https://evil.example/catalog_sans.ttf", "localResourcePath" to "../catalog_sans.ttf")) {
            assertFailsWith<IllegalArgumentException> { uiFontPlan(JsonObject(font + (key to JsonPrimitive(value)))) }
        }
    }
}
