# TalkCtrl

Cardo motorcycle intercom app in the VKNXWorks Ctrl series (a private replacement for Cardo Connect). Kotlin, Jetpack Compose, Material 3, manual DI (`AppContainer`), DataStore for settings. Bluetooth LE (GATT) to the unit; GAIA v3 for firmware updates.

## Layout

- `PROTOCOL.md`: everything known about the protocol, with the Cardo app class/method it came from, marked verified-in-code or inferred. Update it whenever the protocol code changes.
- `protocol/Caip.kt`: CAIP64 messages (pure Kotlin, unit tested): builders, the parser, config records (`UserConfig`, `Volumes`, `Capabilities`, FM presets), RFCOMM splitting.
- `protocol/Gaia.kt`: GAIA v3 packets, RFCOMM framing, `GaiaDialect` (V2 vendor 0x000A for every unit but Flow Lite, V3 vendor 0x001D for Flow Lite), upgrade messages and `UpgradeEngine` (the Qualcomm upgrade state machine, acknowledged mode, no RWCP). `protocol/FirmwareFile.kt`: first file of Cardo's `.tar.gz`.
- `protocol/Models.kt`: model folders on Cardo's update server, the partial serial-prefix map, prompt languages.
- `bt/UnitLink.kt`: the control link (GATT write/notify queue, or RFCOMM), handshake, `UnitState`. `bt/Scanner.kt`: paired + BLE scan. `bt/GaiaGatt.kt`: GAIA over GATT.
- `update/UpdateServer.kt`: the only network code. `update/FirmwareService.kt`: the transfer, a connectedDevice foreground service.
- `ui/unit`: the Unit, Audio, Radio and Intercom tabs. `ui/update`: firmware update.

## Hard rules

- **The only network access** is `UpdateServer`: HTTPS to `update.cardosystems.com` only, started by a tap, sending only the model folder (no account, token, serial or device id, fixed User-Agent, no redirects). CI checks the permission list. No other host, ever.
- **No location permission** (BLUETOOTH_SCAN has `neverForLocation`).
- **Never guess protocol bytes.** Anything not confirmed in PROTOCOL.md is a disabled "Needs confirming on your unit" row. Firmware transfers refuse versions not newer than the unit's and battery under 50 %.
- **Google-free:** no Google libraries, services or SDKs, including transitive ones (CI checks `releaseRuntimeClasspath`). No WebView. Fonts are bundled.
- **Telemetry-free:** no analytics or crash reporting.
- arm64-v8a only. Every user-facing string goes in `strings.xml` (shared Ctrl strings in `strings_ctrl.xml`).
- Settings open only from the profile picture. No settings tab, menu or gear icon, no You tab.
- Must work as a normal user app on any ROM (Android 15+), not only when built into the system.
- No Cardo code or assets in the repo. Out of scope: Live Intercom/cloud, accounts, paid unlocks, crash detection/SOS.

## Dependency verification

Gradle checks every library and plugin download against the sha256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or the Gradle version changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/TalkCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from its log (gzipped and base64-encoded between the BEGIN/END markers), check the diff (only Maven Central, Google's Maven and the Gradle plugin portal) and commit it in the same push. Never turn verification off or make it lenient.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "TalkCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.
- Push straight to `main`; no branches or pull requests.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
