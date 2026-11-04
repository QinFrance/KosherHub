import kotlin.test.*

class SectionCatalogTest {
    private fun device(id: String, resources: List<Resource>) = Device(id, "Test phone", "Test", "bar", "", "", resources)

    @Test fun sectionOrderAndLegacyAppsAliasAreExplicit() {
        assertEquals(listOf("phones", "roms", "guides", "tools", "filters", "reviews"), catalogSections.map { it.key })
        assertEquals(CatalogSection.Tools, CatalogSection.fromKey("apps"))
        assertEquals(CatalogSection.Roms, CatalogSection.fromKey(" ROMs "))
        assertNull(CatalogSection.fromKey("firmware-help"))
    }

    @Test fun filteredAndDeduplicatedResourcesKeepTheirOriginalDeviceIndex() {
        val source = Catalog(devices = listOf(device("first", listOf(
            Resource("Manual", "https://example.test/manual", "guide"),
            Resource("ROM", "https://example.test/rom", "firmware"),
            Resource("Duplicate ROM", "https://example.test/rom", "roms"),
            Resource("Driver", "https://example.test/driver", "tools")
        )), device("second", listOf(Resource("Same file, another device", "https://example.test/rom", "firmware")))))
        val roms = sectionResources(source, CatalogSection.Roms)
        assertEquals(listOf("first/1", "second/0"), roms.map { it.routeKey })
        assertEquals(listOf(1, 0), roms.map { it.resourceIndex })
        assertEquals("Driver", sectionResources(source, "tools").single().resource.title)
    }

    @Test fun onlyExactKnownKindsAreClassified() {
        assertEquals(CatalogSection.Roms, resourceSection(" FIRMWARE "))
        assertEquals(CatalogSection.Tools, resourceSection("APKs"))
        assertEquals(CatalogSection.Tools, resourceSection("driver"))
        assertEquals(CatalogSection.Filters, resourceSection("filters"))
        assertEquals(CatalogSection.Reviews, resourceSection("review"))
        for (kind in listOf("", "unknown", "firmware instructions", "not-firmware", "filtering news", "reviews-tools")) {
            assertNull(resourceSection(kind), kind)
        }
    }

    @Test fun reviewedSupportDownloadsMoveToToolsWhileTheRedditModuleGuideStaysAGuide() {
        val module = Resource("Android Auto for CAT", "https://drive.google.com/drive/folders/1NBDiUbSGnkh8rzP9cusv6ClvvjNz8KEG?usp=sharing", "guide")
        val driver = Resource("driver_auto_installer_exe_v5.1632.00", "https://drive.google.com/drive/folders/1lJb3asilM84urKb73OOdzzahxCxs30-X?usp=sharing", "guide")
        val gapps = Resource("open_gapps-arm64-11.0-pico-20211217", "https://drive.google.com/drive/folders/1C1cxM2AnAWwS99vXNk0GYcLinZ897OXY?usp=sharing", "guide")
        for (resource in listOf(module, driver, gapps)) assertEquals(CatalogSection.Tools, resourceSection(resource))
        assertEquals(CatalogSection.Guides, resourceSection(Resource("Android Auto Magisk Module", "https://www.reddit.com/r/dumbphones/comments/guide/", "guide")))
        assertEquals(CatalogSection.Guides, resourceSection(module.copy(url = "https://www.reddit.com/r/dumbphones/comments/guide/")))
        assertEquals(CatalogSection.Tools, resourceSection(Resource("Driver", "https://github.com/example/tool/releases/download/v1/driver.exe", "guide")))
        assertEquals(CatalogSection.Guides, resourceSection(Resource("Manual", "https://github.com/example/tool/blob/main/manual.pdf", "guide")))
        assertNull(resourceSection(Resource("Unknown", "https://github.com/example/tool/releases/download/v1/driver.exe", "unknown")))
    }

    @Test fun toolsCountIncludesAppsResourcesAndDurableEntriesWithoutDuplicates() {
        val app = AppRecord("test-app", "Test app", "", "Tools", "", "")
        val entry = ContentEntry("tool-entry", "A tool", "tools")
        val source = Catalog(
            devices = listOf(device("test", listOf(Resource("Driver", "https://example.test/driver", "tools")))),
            apps = listOf(app, app), entries = listOf(entry, entry, ContentEntry("review-entry", "A review", "reviews"))
        )
        assertEquals(3, sectionCount(source, "tools"))
        assertEquals(1, sectionApps(source, "apps").size)
        assertTrue(sectionApps(source, "reviews").isEmpty())
        assertEquals(1, sectionCount(source, "reviews"))
        assertEquals(0, sectionCount(source, "filters"))
        assertEquals(0, sectionCount(source, "unknown"))
    }

    @Test fun oldCatalogsRemainCompatibleAndEmptySectionsGainNoInventedContent() {
        val old = catalogJson.decodeFromString<Catalog>("""{"devices":[],"apps":[]}""")
        assertTrue(old.entries.isEmpty())
        assertEquals(ContentEntry(), catalogJson.decodeFromString<ContentEntry>("{}"))
        assertTrue(sectionEntries(old, "filters").isEmpty())
        assertTrue(sectionEntries(old, "reviews").isEmpty())
        val content = Catalog(entries = listOf(ContentEntry("filter-entry", "A filter", "filters", body = "Saved content")))
        assertEquals(content, catalogJson.decodeFromString<Catalog>(catalogJson.encodeToString(Catalog.serializer(), content)))
    }
}
