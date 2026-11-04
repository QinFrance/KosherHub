import kotlinx.serialization.Serializable

@Serializable enum class CatalogSection(val key: String, val label: String) {
    Phones("phones", "Phones"),
    Roms("roms", "ROMs"),
    Guides("guides", "Guides"),
    Tools("tools", "Tools"),
    Filters("filters", "Filters"),
    Reviews("reviews", "Reviews");

    companion object {
        fun fromKey(key: String): CatalogSection? = when (val normalized = key.trim().lowercase()) {
            "apps" -> Tools
            else -> entries.firstOrNull { it.key == normalized }
        }
    }
}

val catalogSections: List<CatalogSection> = CatalogSection.entries.toList()

// Exact aliases keep unknown content out of a section, especially firmware downloads.
fun resourceSection(kind: String): CatalogSection? = when (kind.trim().lowercase()) {
    "firmware", "rom", "roms" -> CatalogSection.Roms
    "guide", "guides", "documentation", "docs", "tutorial", "tutorials" -> CatalogSection.Guides
    "tool", "tools", "driver", "drivers", "module", "modules", "app", "apps", "apk", "apks" -> CatalogSection.Tools
    "filter", "filters" -> CatalogSection.Filters
    "review", "reviews" -> CatalogSection.Reviews
    else -> null
}

private val reviewedGuideDownloads = mapOf(
    "android auto for cat" to "https://drive.google.com/drive/folders/1NBDiUbSGnkh8rzP9cusv6ClvvjNz8KEG",
    "driver_auto_installer_exe_v5.1632.00" to "https://drive.google.com/drive/folders/1lJb3asilM84urKb73OOdzzahxCxs30-X",
    "open_gapps-arm64-11.0-pico-20211217" to "https://drive.google.com/drive/folders/1C1cxM2AnAWwS99vXNk0GYcLinZ897OXY"
)

fun resourceSection(resource: Resource): CatalogSection? {
    val section = resourceSection(resource.kind)
    if (section != CatalogSection.Guides) return section
    val url = resource.url.trim().substringBefore('?').substringBefore('#').trimEnd('/')
    if (reviewedGuideDownloads[resource.title.trim().lowercase()] == url) return CatalogSection.Tools
    val downloadUrl = url.lowercase()
    val host = downloadUrl.removePrefix("https://").substringBefore('/')
    val downloadHost = host in setOf("github.com", "raw.githubusercontent.com", "drive.google.com", "drive.usercontent.google.com",
        "mediafire.com", "www.mediafire.com", "androidfilehost.com", "www.androidfilehost.com", "downloads.sourceforge.net")
    val file = listOf(".apk", ".zip", ".exe", ".msi", ".dmg", ".deb", ".rpm", ".tar.gz", ".tgz", ".7z", ".rar", ".img")
        .any { downloadUrl.endsWith(it) }
    return if (downloadUrl.startsWith("https://") && downloadHost && file) CatalogSection.Tools else section
}

@Serializable data class SectionResource(val device: Device, val resourceIndex: Int, val resource: Resource) {
    val index: Int get() = resourceIndex
    val routeKey: String get() = "${device.id}/$resourceIndex"
}

fun sectionResources(catalog: Catalog, section: CatalogSection): List<SectionResource> {
    val resources = catalog.devices.flatMap { device ->
        device.resources.mapIndexedNotNull { index, resource ->
            if (resourceSection(resource) == section &&
                (resource.title.isNotBlank() || resource.url.isNotBlank() || resource.mirrorUrl.isNotBlank())) {
                SectionResource(device, index, resource)
            } else null
        }
    }
    // A shared file remains visible for each device; duplicates within one device retain their first original route.
    return resources.distinctBy { reference ->
        reference.device.id to reference.resource.let { resource ->
            resource.url.trim().ifBlank { resource.mirrorUrl.trim() }.ifBlank { resource.title.trim().lowercase() }
        }
    }
}

fun sectionResources(catalog: Catalog, section: String): List<SectionResource> =
    CatalogSection.fromKey(section)?.let { sectionResources(catalog, it) }.orEmpty()

fun sectionEntries(catalog: Catalog, section: CatalogSection): List<ContentEntry> = catalog.entries
    .filter { entry ->
        CatalogSection.fromKey(entry.section) == section &&
            (entry.title.isNotBlank() || entry.summary.isNotBlank() || entry.body.isNotBlank() || entry.url.isNotBlank())
    }
    .distinctBy { entry ->
        entry.id.trim().ifBlank {
            entry.deviceId.trim() + "\u0000" + entry.url.trim().ifBlank {
                entry.title.trim().ifBlank { entry.summary.trim() + "\u0000" + entry.body.trim() }
            }
        }
    }

fun sectionEntries(catalog: Catalog, section: String): List<ContentEntry> =
    CatalogSection.fromKey(section)?.let { sectionEntries(catalog, it) }.orEmpty()

fun sectionApps(catalog: Catalog, section: CatalogSection): List<AppRecord> =
    if (section == CatalogSection.Tools) catalog.apps.distinctBy { it.id } else emptyList()

fun sectionApps(catalog: Catalog, section: String): List<AppRecord> =
    CatalogSection.fromKey(section)?.let { sectionApps(catalog, it) }.orEmpty()

fun sectionCount(catalog: Catalog, section: CatalogSection): Int =
    (if (section == CatalogSection.Phones) catalog.devices.distinctBy { it.id }.size else 0) +
        sectionResources(catalog, section).size + sectionEntries(catalog, section).size + sectionApps(catalog, section).size

fun sectionCount(catalog: Catalog, section: String): Int =
    CatalogSection.fromKey(section)?.let { sectionCount(catalog, it) } ?: 0
