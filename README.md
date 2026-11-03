# KosherHub

A small community home for phone documentation, Android apps, and ROM resources. English-first, open source, and deliberately personal rather than corporate.

## Stack

- **Frontend:** Kotlin/JS, Jetpack Compose runtime and Compose HTML. Material 3 expressive design tokens, original device SVGs, desktop side rail, and a separately composed mobile experience using light surfaces and bottom navigation. Compose HTML renders accessible DOM elements; Android Material 3 widgets are not falsely presented as DOM widgets.
- **Backend:** Kotlin/JVM and Ktor, with shared serializable Kotlin catalog models.
- **Free public deployment:** GitHub Pages serves the compiled Kotlin frontend and a generated JSON API snapshot. It uses no iframe, hosted Sites dependency, account, cookies, trackers, or database credentials.
- **Live Ktor server:** available through the Dockerfile or `gradle :backend:run`; GitHub Pages cannot run a JVM server continuously. It exposes the same read-only catalog and serves the compiled frontend. No JVM hosting account is silently provisioned.

## Build and run

Use Java 21 and Gradle 8.14.3:

```sh
gradle :backend:test
gradle :backend:run --args='--sync'
gradle :backend:run --args='--mirror'
gradle :backend:run --args='--export'
gradle :frontend:jsBrowserDistribution
gradle :backend:run
```

Open localhost:8080. The server supports `/health`, `/api/catalog`, `/api/devices`, `/api/apps`, `/api/roms`, and `/catalog.json`. Server-only paths are configurable through `PORT`, `CATALOG_PATH`, and `FRONTEND_PATH`. No secrets are needed for reading the public catalog. An optional CI `GITHUB_TOKEN` increases upstream API rate limits and is never shipped in the browser.

## Automated content

The daily/push workflow refreshes DietDroid resource metadata and known apps' official GitHub releases, mirrors only explicitly approved permissive-license binaries, verifies SHA-256, preserves license and attribution notices, tests Ktor, compiles Compose HTML, and publishes `docs/` for GitHub Pages. Failed sources preserve last-known records. No source HTML is executed.

GitHub app issues trigger a separate workflow that validates the original repository, license and actual APK release, then prepares a metadata-only pull request. Issue content is not executed, and unknown apps are never automatically approved or downloaded. Regular refreshes do not depend on users submitting issues.

## Sources and licensing

Phone metadata includes original summaries and source attribution to DietDroid. Firmware without verified redistribution rights stays a source reference; it is not silently rehosted. Simple Keyboard is an unmodified Apache-2.0 upstream APK, accompanied by its original license and upstream/AOSP attribution. Fossify Calculator is a real GPL release; its binary is not mirrored until full corresponding-source requirements are reviewed. No listing constitutes kosher certification.

The Shalom Karr app-store repository is a prototype with fictional listings, simulated downloads, and `.test` URLs. It has no redistribution license for its source. Those mock apps are not represented as real downloads here.

The coffee label is present, but no payment destination has been invented. It opens an honest support dialog until the owner configures a donation account. Contact uses public GitHub issues, so no private email is exposed in the interface.

## Security and privacy

Do not commit credentials, personal contact details, local runtime files, or private user submissions. No account or cookie is needed on the site. External GitHub issue submission follows GitHub's own account rules. CI source imports are allowlisted; APKs are never executed during ingestion. Download checksums identify files, not safety or religious suitability.

## CFOP catalog

Includes 14 factual app records from cfopuser/app-store, credited under its CC-BY-NC-SA-4.0 catalog license. This dataset has a separate license from the MIT application code. Patched builds are clearly labeled as community builds and are linked to their original release, not claimed as official publisher APKs or mirrored without redistribution rights. The Kotlin importer refreshes the CFOP JSON index automatically.
