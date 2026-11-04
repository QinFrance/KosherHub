import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlin.test.*

class SectionsTest {
    @Test fun canonicalFileReplacesGeneratedEntriesAndLastDuplicateWins() {
        val file = Files.createTempFile("kosherhub-sections-", ".json").toFile()
        val base = Catalog(updated = "2026-11-04", apps = listOf(AppRecord("test-app", "Test", "", "Tools", "", "")),
            entries = listOf(ContentEntry("removed-review", "Old review", "reviews")))
        try {
            file.writeText("""[
                {"id":"filter-one","title":"First title","section":"filters"},
                {"id":"guide-one","title":"Saved guide","section":"guides","body":"Durable content"},
                {"id":"filter-one","title":"Updated title","section":"filters","source":"Source attribution"}
            ]""")
            val loaded = loadSectionEntries(base, file.path)
            assertEquals(listOf("filter-one", "guide-one"), loaded.entries.map { it.id })
            assertEquals("Updated title", loaded.entries.first().title)
            assertEquals("Source attribution", loaded.entries.first().source)
            assertEquals("Durable content", loaded.entries.last().body)
            assertEquals(base.apps, loaded.apps)
            assertEquals(base.updated, loaded.updated)
            assertTrue(loaded.entries.none { it.id == "removed-review" })
        } finally { file.delete() }
    }

    @Test fun unsafeRouteIdsAndNonCanonicalSectionsAreRejected() {
        val file = Files.createTempFile("kosherhub-sections-invalid-", ".json").toFile()
        try {
            for (id in listOf("", "bad/id", "bad#id", "bad?id", "../outside", "%2Foutside", "MixedCase", "a".repeat(81))) {
                file.writeText(catalogJson.encodeToString(listOf(ContentEntry(id, "Entry", "guides"))))
                assertFailsWith<IllegalArgumentException>(id) { loadSectionEntries(Catalog(), file.path) }
            }
            for (section in listOf("", "apps", "firmware", "ROMs", " reviews ", "unknown")) {
                file.writeText(catalogJson.encodeToString(listOf(ContentEntry("safe-id", "Entry", section))))
                assertFailsWith<IllegalArgumentException>(section) { loadSectionEntries(Catalog(), file.path) }
            }
        } finally { file.delete() }
    }

    @Test fun missingFileKeepsLegacyEntriesWhileEmptyFileClearsThemWithoutInvention() {
        val directory = Files.createTempDirectory("kosherhub-sections-empty-").toFile()
        val file = directory.resolve("sections.json")
        val base = Catalog(entries = listOf(ContentEntry("existing", "Existing", "reviews")))
        try {
            assertSame(base, loadSectionEntries(base, file.path))
            assertTrue(loadSectionEntries(Catalog(), file.path).entries.isEmpty())
            file.writeText("[]")
            assertTrue(loadSectionEntries(base, file.path).entries.isEmpty())
            assertTrue(sectionEntries(loadSectionEntries(base, file.path), "filters").isEmpty())
            assertTrue(sectionEntries(loadSectionEntries(base, file.path), "reviews").isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
