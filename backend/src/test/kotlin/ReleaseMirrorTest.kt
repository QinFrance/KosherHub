import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.test.*

class ReleaseMirrorTest {
    @Test fun targetCredentialsNeverFollowPublicUpstreamMetadataReads() {
        assertFalse(releaseApiUsesToken("/repos/alltechdev/DietDroid/releases?per_page=100&page=1"))
        assertFalse(releaseApiUsesToken("/repos/Noochtech/awesome-qin-f21-pro/releases/123/assets"))
        assertTrue(releaseApiUsesToken("/repos/QinFrance/KosherHub/releases"))
        assertTrue(releaseApiUsesToken("/repos/QinFrance/KosherHub/releases/123/assets"))
        assertFailsWith<IllegalArgumentException> { releaseApiUsesToken("/repos/other/unapproved/releases") }
        assertFailsWith<IllegalArgumentException> { releaseApiUsesToken("/repos/QinFrance/KosherHub-evil/releases") }
    }

    @Test fun incompleteSourceStatusRetainsVerifiedMirrorsInTheSerializedIndex() {
        val failure = "alltechdev/DietDroid: source is not publicly accessible (HTTP 404)"
        val index = FirmwareMirrorIndex(updated = "2026-11-03T00:00:00Z", status = "incomplete", failures = listOf(failure))
        val restored = catalogJson.decodeFromString<FirmwareMirrorIndex>(catalogJson.encodeToString(FirmwareMirrorIndex.serializer(), index))
        assertEquals("incomplete", restored.status)
        assertEquals(listOf(failure), restored.failures)
        assertEquals(index.assets, restored.assets)
    }

    @Test fun releaseNamesAreStableScopedAndCollisionResistant() {
        val tag = mirroredReleaseTag("alltechdev/DietDroid", "v1/../../test")
        assertTrue(Regex("[A-Za-z0-9._-]+").matches(tag))
        assertFalse(tag.contains(".."))
        assertEquals(tag, mirroredReleaseTag("alltechdev/DietDroid", "v1/../../test"))
        assertNotEquals(tag, mirroredReleaseTag("alltechdev/DietDroid", "v1-..-..-test"))
        assertNotEquals(tag, mirroredReleaseTag("Noochtech/awesome-qin-f21-pro", "v1/../../test"))
        assertFailsWith<IllegalArgumentException> { mirroredReleaseTag("unreviewed/other", "v1") }
        val name = mirroredAssetName(123, "../../image $(run) .bin")
        assertTrue(Regex("[A-Za-z0-9._-]+").matches(name))
        assertFalse(name.contains(".."))
        assertNotEquals(name, mirroredAssetName(124, "../../image $(run) .bin"))
    }

    @Test fun downloadUrlsStayInsideApprovedReleaseRepositories() {
        assertEquals("github.com", checkedPublicAssetUrl("alltechdev/DietDroid", "https://github.com/alltechdev/DietDroid/releases/download/v1/image.bin").host)
        listOf(
            "https://github.com.evil.test/alltechdev/DietDroid/releases/download/v1/image.bin",
            "https://github.com/other/repo/releases/download/v1/image.bin",
            "https://github.com/alltechdev/DietDroid/releases/download/%2e%2e/image.bin",
            "https://user@github.com/alltechdev/DietDroid/releases/download/v1/image.bin",
            "http://github.com/alltechdev/DietDroid/releases/download/v1/image.bin",
            "https://github.com/alltechdev/DietDroid/releases/download/v1/image.bin?token=example"
        ).forEach { url -> assertFailsWith<IllegalArgumentException> { checkedPublicAssetUrl("alltechdev/DietDroid", url) } }
    }

    @Test fun streamedFilesMustMatchBothSizeAndHash() {
        val target = Files.createTempFile("release-mirror-test", ".bin").toFile()
        val bytes = "firmware test bytes".toByteArray()
        try {
            val expected = sha256(bytes)
            val result = copyCheckedRelease(ByteArrayInputStream(bytes), target, bytes.size.toLong(), "sha256:$expected")
            assertEquals(bytes.size.toLong(), result.bytes)
            assertEquals(expected, result.sha256)
            assertContentEquals(bytes, target.readBytes())
            assertFailsWith<IllegalArgumentException> { copyCheckedRelease(ByteArrayInputStream(bytes), target, bytes.size.toLong() - 1, "") }
            assertFailsWith<IllegalArgumentException> { copyCheckedRelease(ByteArrayInputStream(bytes), target, bytes.size.toLong() + 1, "") }
            assertFailsWith<IllegalArgumentException> { copyCheckedRelease(ByteArrayInputStream(bytes), target, bytes.size.toLong(), "sha256:${"0".repeat(64)}") }
            assertFailsWith<IllegalArgumentException> { copyCheckedRelease(ByteArrayInputStream(bytes), target, MAX_RELEASE_ASSET_BYTES + 1, "") }
            assertFailsWith<IllegalArgumentException> { releaseDigest("md5:${"0".repeat(32)}") }
        } finally { target.delete() }
    }

    @Test fun existingFilenamesAreImmutableAndNeedMatchingChecksums() {
        val hash = "a".repeat(64)
        assertTrue(verifiedExistingRelease(hash, 42, hash, 42))
        assertFalse(verifiedExistingRelease(hash, 42, "", 42))
        assertFailsWith<IllegalArgumentException> { verifiedExistingRelease(hash, 42, "b".repeat(64), 42) }
        assertFailsWith<IllegalArgumentException> { verifiedExistingRelease(hash, 42, hash, 43) }
    }

    @Test fun verifiedCatalogResourcesRemainOnTheirOwnDevicesAndDoNotDuplicate() {
        val source = "https://github.com/alltechdev/DietDroid/releases/download/v1/image.bin"
        val hosted = "https://github.com/QinFrance/KosherHub/releases/download/mirror-v1/1--image.bin"
        val catalog = Catalog(devices = listOf(
            Device("f21-pro", "Qin F21 Pro", "Qin", "bar", "", "", listOf(Resource("Stock image", source, "firmware"))),
            Device("f21-pro-plus-unisoc-t310", "Different model", "Qin", "bar", "", "")
        ))
        val mirror = FirmwareMirror("alltechdev/DietDroid", "v1", "", 1, "image.bin", source, "", 12,
            "a".repeat(64), "mirror-v1", 2, "1--image.bin", hosted, "2026-11-03T00:00:00Z", listOf("f21-pro"), "Original notices apply")
        val once = applyFirmwareMirrors(catalog, listOf(mirror))
        assertEquals(1, once.devices.first().resources.size)
        assertEquals(hosted, once.devices.first().resources.single().mirrorUrl)
        assertEquals(mirror.sha256, once.devices.first().resources.single().sha256)
        assertTrue(once.devices.last().resources.isEmpty())
        assertEquals(once, applyFirmwareMirrors(once, listOf(mirror)))
    }

    @Test fun identicalHostedBinaryAliasesUseOneFileCardWithoutLosingIndexProvenance() {
        val catalog = Catalog(devices = listOf(Device("f21-pro", "Qin F21 Pro", "Qin", "bar", "", "")))
        val first = FirmwareMirror("alltechdev/DietDroid", "f21pro-firmware", "", 1, "OLD-F21-USA.rar", "https://github.com/alltechdev/DietDroid/releases/download/v1/OLD-F21-USA.rar", "", 42,
            "a".repeat(64), "dietdroid-archive", 10, "OLD-F21-USA.rar", "https://github.com/QinFrance/KosherHub/releases/download/dietdroid-archive/OLD-F21-USA.rar", "2026-11-03T00:00:00Z", listOf("f21-pro"))
        val alias = first.copy(sourceRepository = "Noochtech/awesome-qin-f21-pro", sourceAssetId = 2, sourceAssetName = "v1-F21-USA.rar",
            targetAssetId = 20, targetAssetName = "v1-F21-USA.rar", mirrorUrl = "https://github.com/QinFrance/KosherHub/releases/download/nooch-v1/v1-F21-USA.rar")
        val index = FirmwareMirrorIndex(assets = listOf(first, alias))
        val merged = applyFirmwareMirrors(catalog, index.assets)
        assertEquals(1, merged.devices.single().resources.size)
        assertEquals(2, index.assets.size)
        assertEquals(merged, applyFirmwareMirrors(merged, index.assets))
        // Different data remains available even when the friendly filename is similar.
        val different = alias.copy(sha256 = "b".repeat(64), mirrorUrl = alias.mirrorUrl + ".new")
        assertEquals(2, applyFirmwareMirrors(catalog, listOf(first, different)).devices.single().resources.size)
    }
}
