# Changelog

What changed in each TalkCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/TalkCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.3.2 (2026-11-22)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Fixed

- **A firmware update no longer hangs at the same percentage when a Bluetooth packet doesn't go out.** When Android refused to send one of the update's packets (the Bluetooth stack was still busy, or the link hiccupped), TalkCtrl dropped that packet without noticing and then waited forever for the unit's answer, so the progress stood still until you pressed **Abort**. TalkCtrl now sends the same packet again after half a second, as it already did for the normal control link; if that is refused too, it closes the update link and reports it as lost, so the usual reconnect-and-resume takes over (up to 5 tries, and the unit continues from its own resume point). **This needs a test on a real Cardo unit** (a complete update) before it can be trusted: nothing in it has run on a phone or a unit yet.
- **Starting a firmware update can no longer make TalkCtrl crash or run two transfers at once.** If the update service was started without its details, or a second time while an update was running, Android could close TalkCtrl with a "did not call startForeground" error, and a second start also reset the progress on screen and started a second transfer next to the first. A second start is now ignored while one runs (its notification stays as it was), and a start without details ends cleanly.
- **Loading a large firmware file no longer freezes the screen.** The firmware file (a few MB, at most 64 MB) was read on the main thread when the transfer started; it is now read in the background.

### Security

- **"Forgot PIN?" now asks for your phone's own screen lock first.** Before, anyone holding your unlocked phone could tap **Forgot PIN?** on TalkCtrl's lock screen and reset it to get past the app lock. Now, after you confirm in the dialog, Android shows its own screen-lock prompt (your phone PIN, pattern or password, or a strong fingerprint) and the reset only happens once that succeeds. Cancelling the prompt changes nothing. If your phone has no screen lock, the reset works as before, straight after the confirmation. The dialog text says so in all four languages.
- **Importing a settings file can no longer make the app lock wait longer.** Hide in screenshots and Hide in recent apps could already only be turned on by an import, but the lock timeout was taken from the file as it was, so a file could change "Immediately" into "When TalkCtrl restarts". An import now only uses the file's timeout when it is shorter than yours. The text under the settings file section says so.

## v0.3.1 (2026-11-22)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Removed

- **Export diagnostics.** The "Export diagnostics" row under Unit > Maintenance is gone, with its explanation dialog and the text file it saved (versions, phone model, connection, settings records and the last messages between phone and unit). TalkCtrl also no longer keeps the in-memory list of the last 200 messages to and from the unit that the file was built from. Nothing else changes: firmware update, factory reset, forget unit, the low-battery warning and every other control work as before.
- **Leftover log lines stripped from release builds.** The release build now also removes warning, error and other system-log calls from the code it ships (TalkCtrl itself had none), so nothing ends up in the phone's system log.

## v0.3.0 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### New

- **TalkCtrl in French and German, following the phone's language.** When your phone is set to French, TalkCtrl now shows in French (polite "vous"); set to German, it shows in German (informal "du"). Every screen, notification and message is translated, including the firmware update, the app lock and the You page. Cardo model names and technical terms (BLE, RFCOMM, VOX, RDS, DMC, Intercom, mesh) stay as they are. Android's per-app language setting (Settings > Apps > TalkCtrl > Language) now lists English, Dutch, French and German, so you can also pick a language for TalkCtrl alone. Not yet tried on a phone.

## v0.2.0 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### New

- **TalkCtrl in Dutch.** When your phone is set to Dutch, TalkCtrl now shows in Dutch (Belgian Dutch, informal "je"), all screens including the new features above; otherwise it stays in English. There is no language setting in the app: it follows the phone. Cardo model names and technical terms (BLE, RFCOMM, VOX, RDS, DMC) stay as they are.
- **Riding mode.** The Unit tab has a *Riding mode* button while your unit is connected. It opens a screen of eight big buttons you can hit with gloves on: FM on/off, next saved preset, seek down and up, music quieter and louder, mute or unmute your mesh microphone, and end all intercom calls, with a short vibration on each press. The top shows the battery (or that the unit isn't connected), and the FM station with its name if you gave it one. The screen stays on while riding mode is open; Exit or Back leaves it. It only sends the same commands the Radio, Audio and Intercom tabs already send; nothing new is sent to the unit. As on the Intercom tab, the unit doesn't report whether the mesh mic is muted, so the button shows what you last chose here. Not yet tried with a real Cardo unit.
- **Low battery warning.** Settings → Notifications → *Low battery warning* (off until you choose a level: 10, 15, 20 or 30 %) posts a notification "Unit battery at 15 %" when the battery level your unit reports reaches that level and it isn't charging. It warns once per drop, and again only after the unit has charged or come back 5 % above the level, so a level that wobbles doesn't keep warning. It uses the battery reports the unit already sends while TalkCtrl is connected (the app open or in the background, as long as Android keeps TalkCtrl running): it adds no connection of its own and sends nothing to the unit. TalkCtrl asks for permission to show notifications when you turn it on. Not yet tried with a real Cardo unit.
- **Export diagnostics.** Unit tab → Maintenance → *Export diagnostics* saves a text file, where you choose, with what TalkCtrl sees of your unit: TalkCtrl and Android versions and the phone model, the link (BLE or RFCOMM, CAIP version), firmware versions, the USER_CONFIG and VOLUMES records and capabilities as bytes, supported languages and EQ profiles, battery, FM presets and state, and the last 200 messages between phone and unit since connecting (kept in memory only, gone when TalkCtrl closes). It is the quickest way to check on a real unit that TalkCtrl reads it correctly. Personal details are left out: the unit's name, serial number and Bluetooth address, your speed-dial number and your FM station names; messages that carry the name, serial or speed-dial number are stored as just their type and length, before they are even kept in memory. Only the model folder the serial's two-letter prefix points to is included. Nothing is sent anywhere. Not yet tried with a real Cardo unit.
- **Export and import profiles and settings.** Settings → Profiles and settings file. *Export profiles* saves your Solo, Duo and Group profiles in a small JSON file; *Export settings* saves TalkCtrl's own settings (classic Bluetooth fallback, the model picked for firmware updates, the app lock's "lock again" time, Hide in screenshots / Hide in recent apps) and your FM station names. *Import profiles or settings* reads either kind of file, lists exactly what would be replaced, and changes nothing until you tap Replace. The files never hold your unit's Bluetooth address, its name or serial number, or the app lock's PIN or keys. Imported profiles are only stored in TalkCtrl: nothing is sent to the unit until you apply a profile, as before, and a profile that isn't a valid USER_CONFIG + VOLUMES record is skipped. An import can turn Hide in screenshots and Hide in recent apps on but never off, and never changes whether the app lock is on. Not yet tried on a phone.
- **Names for FM presets.** The pencil next to a preset on the Radio tab now also takes a name ("Studio Brussel", "Radio 2"). The preset then shows under that name, and the Radio card shows the name of the station you are listening to when it has one. Names are kept on the phone only, the unit has no place for them, and belong to the frequency: a name follows the station, not the preset number. Changing only the name sends nothing to the unit. Not yet tried with a real Cardo unit.

## v0.1.3 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published. It is the first release built with the new build tools (Android Gradle Plugin 9, stricter code shrinking), so update over your installed version and check that your data and settings are still there. It has also never been tried with a real Cardo unit.

### Changed

- **Newer build tools; nothing changes in how TalkCtrl looks or works.** TalkCtrl is now built with the current stable toolchain: Android Gradle Plugin 8.13.2 → 9.5.1, Gradle 8.14.3 → 9.9.2 and Compose BOM 2026.06.01 → 2026.09.00 (Compose 1.12.1, Material 3 1.4.0). Kotlin stays at 2.4.21, and every library stays at the version it had (TalkCtrl was already on the versions the newest Ctrl apps use). No library was added, and the app is still Google-free (CI checks it).
- **Compiled against the Android 17 SDK.** Compose 1.12 needs `compileSdk` 37, so that goes from 36 to 37. The Android versions TalkCtrl runs on and targets are unchanged (`minSdk` 35, `targetSdk` 36), so Android treats the app exactly as before.
- **The app is shrunk by a newer R8.** Android Gradle Plugin 9 compiles Kotlin itself (the separate Kotlin Android plugin is gone from the build files) and brings the R8 version that understands Kotlin 2.4. TalkCtrl's own keep rules are unchanged. The newer R8 has stricter defaults than the old one, so the release build needs a check on a phone before the next version is published.
- The checksums Gradle verifies every download against (`gradle/verification-metadata.xml`) were regenerated on CI for the new versions. Built and checked by CI only; not yet run on a phone.

### Security

- **The signing key is no longer available to the build.** Until now the release key and its passwords were present in the same GitHub Actions job that runs Gradle, so any build plugin or library could in principle have read them. The build job now has no secrets at all and produces an unsigned APK. A separate job, which checks out no code and runs no Gradle, signs it with `apksigner`, and then refuses to hand it on unless it is signed by exactly the certificate published in SECURITY.md and README.md and its contents are identical to what the build job made. Releases are signed with the same key and the same signature scheme (v2) as before, so updates install as usual. The app itself is unchanged by this. The release step of the new workflow has not run yet for TalkCtrl: it runs for the first time with the next version.
- **The build checks that the published fingerprint and the enforced one are the same.** If `build.yml`, SECURITY.md and README.md ever disagree about the signing certificate, the build fails.

### Project

- **Builds no longer use a saved Gradle cache, and the Gradle plugin portal is no longer a plugin source.** Other Ctrl apps had builds fail with "Dependency verification failed ... from repository Gradle Central Plugin Repository", and keep failing until the caches were deleted by hand. The cause (found on DateCtrl): with a restored cache Gradle still fetches the plugin files from Maven Central on every build; when Maven Central can't be reached for a moment, Gradle takes the Kotlin plugin "marker" file from the Gradle plugin portal instead, the portal serves a different file under the same name, verification rightly refuses it, and the failed run then saved that state as the cache for every later run. Now every build and the code scan start from a clean Gradle home (a build takes a minute or two longer), and `settings.gradle.kts` lists only Google's Maven and Maven Central for plugins. Dependency verification itself is unchanged and as strict as before. Nothing in the app changed.

## v0.1.2 (2026-11-20)

Built and checked by CI only: nothing in this release had been run on a phone when it was published. It has also never been tried with a real Cardo unit.

### Privacy

- **The app is hidden from screenshots from its very first frame when it opens locked.** The "not in screenshots or screen recordings" protection was switched on a moment after the first frame was drawn, so the very first frame of the lock screen was not covered. It is now set before anything is drawn when the app lock is on (and when "Hide in screenshots" is already known to be on). Nothing changes while the app is open.

### Not changed

- TalkCtrl has no home-screen widgets and no Quick Settings tiles, so there was nothing to remove.
- Nothing in the Bluetooth code, the protocol or the firmware update was touched.

## Project updates (2026-11-19)

### Project

- **The weekly code scan builds again.** The scan on 17 November stopped before compiling anything: the build checks every download against the checksums in `gradle/verification-metadata.xml`, and six small build-description files (the parent files of Guava, JUnit and the Kotlin coroutines and serialization libraries) were missing from that list. They are only fetched when nothing is cached, which is why earlier builds passed. Their checksums, taken from a fresh run of the dependency verification workflow, are now in the list; nothing was removed and verification is as strict as before. No app changes, so no new version.

## v0.1.1 (2026-11-13)

A security and maintenance release. Nothing changes in how you use TalkCtrl.

### Security

- **Firmware transfer:** an unusual request from the unit during an update (asking to skip a huge amount of data) can no longer derail the transfer; TalkCtrl keeps sending from the right place in the file.
- **Firmware files you load are limited to 64 MB**, like downloads from Cardo's server, so picking a huge file by mistake can't fill the phone's storage.
- **Temporary firmware copies are cleaned up:** a failed or cancelled download leaves nothing behind, the copy is removed after a successful update, and leftovers are removed the next time TalkCtrl starts.
- The firmware transfer only ever reads TalkCtrl's own temporary copy of the file.
- The build tool download is now checked against its published checksum.

### Performance

- Updated Jetpack Compose, AndroidX and Kotlin libraries to their current versions.
- Release builds drop debug logging.
## v0.1.0 (2026-11-12)

The first release of TalkCtrl: a private app for Cardo motorcycle intercoms (Packtalk, Freecom X, Spirit), without a Cardo account. **Not yet tested on a real unit**; features that couldn't be confirmed from the Cardo app's code are shown greyed out as "Needs confirming on your unit".

### New

- **Find and connect** to your unit over Bluetooth LE (classic Bluetooth as a fallback in Settings); TalkCtrl remembers it and reconnects when you open the app.
- **Unit tab:** battery and charging, firmware version, rename the unit, serial number (shown on the phone only, after a tap), factory reset with a confirmation.
- **Profiles:** save the unit's volumes and settings as Solo, Duo and Group, and apply one with a tap.
- **Audio tab:** volumes for speaker, intercom, music, phone and FM, music level during intercom, automatic volume, audio profile (EQ), music sharing, phone and intercom together, voice prompts and their language, voice commands, VOX sensitivity and noise gate.
- **Radio tab:** FM on/off, tune to a frequency, seek, scan, auto tune, six presets you can play and edit, region and RDS.
- **Intercom tab:** mesh groups (create, join, add riders, stop, mute, bridge to Bluetooth intercom, delete), Bluetooth intercom channels A, B and C (pair, call, conference, end, reset riders), and the speed dial number.
- **Firmware update:** check Cardo's update server for your model (one anonymous request sending only the model name), read what's new, download the firmware or load a `.bin`, `.fot` or `.tar.gz` file, and install it with Qualcomm's standard GAIA upgrade, with progress, Abort and resume. Only firmware newer than the unit's is accepted, and the unit needs at least 50 % battery. The transfer has not yet been tested on a real unit.
- **App lock** with its own PIN (at least 6 digits) and optional fingerprint unlock, **Hide in screenshots** and **Hide in recent apps**, all off by default.
- **Ctrl / Signal look** in dark and light, following your phone's theme, with layouts for phones, tablets and foldables.
