# KosherHub

A small, English-first catalog of phones, Android apps, and ROMs.

## Kotlin throughout

The interface uses **Compose Multiplatform UI on Kotlin/Wasm**, real Material 3 components and `MaterialExpressiveTheme`. It replaces the earlier Compose HTML implementation. All application and ingestion logic is Kotlin. The only JavaScript delivered to browsers is generated compiler/Skia runtime glue; generated builds are published as Pages artifacts, not committed as hand-written source.

The palette has exactly two families: blue and achromatic neutrals. Every Material color role is explicitly set for light and dark themes. Genuine app logos are bundled locally and rendered in monochrome. Google Material Symbols Rounded are bundled locally under Apache 2.0. Noto Sans Hebrew supports both English and Hebrew names and is bundled under the SIL Open Font License. Sources and hashes are in `resources/`.

Desktop uses a compact side navigation and adaptive grids. Mobile uses a bottom navigation bar. App, phone and file details have their own shareable hash routes, parent Back controls, and native browser Back/Forward support. Search/filter state survives returning from a detail page. There are no accounts, cookies, trackers, ads, or saved preferences.

## Build

Java 21, Gradle 8.14.3:

```sh
gradle :backend:test
gradle :backend:run --args='--sync'
gradle :backend:run --args='--mirror'
gradle :backend:run --args='--assets'
gradle :frontend:wasmJsBrowserDistribution
gradle :backend:run
```

The frontend build depends on exporting the latest catalog. `--assets` reconstructs all 16 genuine app logos and the font from exact reviewed upstream URLs, sizes, Git blob identities and SHA-256 hashes. Kotlin/Wasm needs a modern browser with WasmGC support.

GitHub Pages serves the compiled site and generated `catalog.json`. The Kotlin/Ktor server is also supplied for separate JVM hosting, with `/health`, `/api/catalog`, `/api/devices`, `/api/apps`, `/api/roms`, and static files. Pages does not run a permanent JVM server. `PORT`, `CATALOG_PATH`, and `FRONTEND_PATH` configure server-only deployment.

## Hosting and automation

The daily catalog workflow tests the backend, refreshes known sources, mirrors reviewed APK/source pairs, rebuilds local icons, and publishes a Pages artifact. Firmware mirroring uses a separate Kotlin streaming workflow, with one temporary file at a time, size limits, source and target SHA-256 verification, resumable checkpoints and immutable target names. Files are hosted in this repository's GitHub Releases, not inside Git history. Verified mirror metadata is merged into the site after each completed workflow, including partial results.

The approved firmware sources are DietDroid and Noochtech, requested by the owner for migration. **DietDroid's repository is private**: this repository's automatic GitHub token cannot read its binary assets. That source remains explicitly reported as incomplete until those assets are made accessible through an authorized transfer. Public Noochtech transfers operate independently. No credentials are invented or copied from the browser. Provenance, upstream requirements and embedded notices are retained; no blanket copyright license or kosher certification is asserted.

Simple Keyboard is an unmodified Apache-2.0 APK with its license, notices and source archive. Fossify Calculator 1.4.0 is hosted with its exact reviewed F-Droid corresponding-source package, Fossify Commons 6.1.0 sources, GPL license and notices. New GPL versions require a corresponding-source review before mirroring. CFOP contributes 14 factual app records under its separate CC-BY-NC-SA-4.0 metadata license; unverified third-party binary rights are not inferred from that catalog license. Their original release downloads remain available until redistribution requirements are established.

GitHub app issues are processed entirely in Kotlin. They produce a review branch or metadata-only PR; untrusted issue text is never executed and cannot approve or download a new APK. Regular source refreshes do not depend on contributions.

## Community

Contact uses GitHub issues. The coffee icon opens a support dialog; no payment destination has been invented. The Shalom Karr prototype's fictional apps and `.test` downloads are not represented as real downloads.

Application code is MIT licensed. Data, font, icon, APK and source-archive licenses remain separate and retain their attribution. Never commit credentials, private chat content, private contact details, or runtime files.
