import java.io.File
import kotlinx.serialization.decodeFromString

private val sectionEntryId = Regex("[a-z0-9][a-z0-9-]{0,79}")

fun loadSectionEntries(catalog: Catalog, path: String = "data/sections.json"): Catalog {
    val file = File(path)
    if (!file.exists()) return catalog
    require(file.isFile && file.length() <= 2_000_000) { "Section content must be a JSON file smaller than 2 MB" }
    val entries = catalogJson.decodeFromString<List<ContentEntry>>(file.readText())
    val allowedSections = catalogSections.map { it.key }.toSet()
    val byId = linkedMapOf<String, ContentEntry>()
    entries.forEachIndexed { index, entry ->
        require(sectionEntryId.matches(entry.id)) {
            "Section entry ${index + 1} needs an ID of 1–80 lowercase letters, numbers, and hyphens"
        }
        require(entry.section in allowedSections) {
            "Section entry ${index + 1} must use one of the six canonical section keys"
        }
        byId[entry.id] = entry
    }
    // This file is canonical, so deleting an entry also removes any copy left in a generated catalog.
    return catalog.copy(entries = byId.values.toList())
}
