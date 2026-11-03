import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class MirrorTest {
    private val source = AppSource("test", "owner/app", "MIT", ".*", mirrorApproved = true)
    private val app = AppRecord("test", "Test", "", "Tools", "https://github.com/owner/app", "MIT", version = "v1",
        downloadUrl = "https://github.com/owner/app/releases/download/v1/app.apk", status = "official-release")

    @Test fun mirrorPathsAndUpstreamMustBeSafe() {
        assertEquals("test-v1", reviewedMirrorSlug(app, source))
        assertFailsWith<IllegalArgumentException> { reviewedMirrorSlug(app.copy(id = "../test"), source) }
        assertFailsWith<IllegalArgumentException> { reviewedMirrorSlug(app.copy(version = "../v1"), source) }
        assertFailsWith<IllegalArgumentException> { reviewedMirrorSlug(app.copy(downloadUrl = "https://evil.example/app.apk"), source) }
        assertFailsWith<IllegalArgumentException> { reviewedMirrorSlug(app, source.copy(mirrorApproved = false)) }
    }

    @Test fun gplMirroringRequiresTheExactReviewedSourceAndBinaryPair() {
        val gpl = source.copy(license = "GPL-3.0-only")
        assertFailsWith<IllegalArgumentException> { reviewedMirrorSlug(app, gpl) }
        val reviewed = gpl.copy(sourceReviewVersion = "v1", binarySha256 = "a".repeat(64),
            sourceBundleUrl = "https://f-droid.org/repo/example_1_src.tar.gz", sourceBundleSha256 = "b".repeat(64),
            auxiliarySources = listOf(ReviewedSource("commons-1", "https://github.com/FossifyOrg/Commons/archive/refs/tags/1.tar.gz", "c".repeat(64))))
        assertEquals("test-v1", reviewedMirrorSlug(app, reviewed))
        assertFailsWith<IllegalArgumentException> { reviewedMirrorSlug(app.copy(version = "v2"), reviewed) }
        assertFailsWith<IllegalArgumentException> { reviewedMirrorSlug(app, reviewed.copy(sourceBundleSha256 = "")) }
    }

    @Test fun apkContainerMustContainAnAndroidManifest() {
        fun zip(entry: String): ByteArray = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { it.putNextEntry(ZipEntry(entry)); it.write("test".toByteArray()); it.closeEntry() }
        }.toByteArray()
        validateApk(zip("AndroidManifest.xml"))
        assertFailsWith<IllegalArgumentException> { validateApk(zip("README.txt")) }
        assertFailsWith<IllegalArgumentException> { validateApk("not an APK".toByteArray()) }
    }
}
