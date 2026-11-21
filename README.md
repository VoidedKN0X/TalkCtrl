# TalkCtrl

**A private app for your Cardo motorcycle intercom: settings, FM radio, intercom and firmware updates, without an account.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/TalkCtrl?label=download)](https://github.com/VoidedKN0X/TalkCtrl/releases/latest) ![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

TalkCtrl controls Cardo Packtalk, Freecom X and Spirit intercoms over Bluetooth, as a private replacement for the Cardo Connect app. It needs no Cardo account and sends nothing about you anywhere. The only time it goes online is when you tap **Check for update** or **Download** on the firmware screen, and then it only asks Cardo's public update server for your model's firmware.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

> **Tested on: none yet.** TalkCtrl was written from how the Cardo Connect app talks to the units (see [PROTOCOL.md](PROTOCOL.md)), but the developer doesn't have a Cardo unit. Settings that the Cardo app's code shows clearly are enabled; anything that isn't clear is shown greyed out as "Needs confirming on your unit". **The firmware transfer is not yet tested on a real unit.** Reports from Cardo owners are very welcome.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/TalkCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a>

## Features

- **Connect over Bluetooth LE**, the way the Cardo app does on current units, with classic Bluetooth (RFCOMM) as a fallback in Settings. TalkCtrl remembers your unit and reconnects when you open it
- **Unit info:** battery and charging, firmware version, name (rename it), serial number (shown on the phone only, after a tap)
- **Volumes:** speaker (prompts), intercom, music, phone and FM, plus how loud music stays during an intercom call
- **Sound:** automatic (speed-based) volume, audio profiles (EQ), music sharing, phone and intercom together
- **Voice:** voice prompts on or off, prompt language (from the languages your unit has), voice commands
- **Microphone:** VOX sensitivity and noise gate
- **FM radio:** on/off, tune to a frequency, seek, scan, auto tune, six presets (play and edit, with your own station names kept on the phone), region and RDS
- **Intercom:** mesh groups (create, join, add riders, stop, mute, bridge to Bluetooth intercom, delete), Bluetooth intercom channels A/B/C (pair, call, end, conference, reset riders), speed dial number and call
- **Profiles:** save the unit's volumes and settings as Solo, Duo and Group and switch with one tap; export them to a file and import them again (on this or another phone)
- **Settings file:** export TalkCtrl's settings and FM station names and import them, after seeing what will be replaced. Never includes your unit's address, name or serial number or the app lock's PIN
- **Factory reset**, with a confirmation
- **Firmware update:** check Cardo's update server for your model, read what's new, download, or load a `.bin`, `.fot` or `.tar.gz` file yourself. Installed with Qualcomm's standard GAIA upgrade, with progress, Abort and resume. TalkCtrl refuses firmware that isn't newer than the unit's and needs at least 50 % battery
- **App lock** with its own PIN and optional fingerprint unlock, and **Hide in screenshots** / **Hide in recent apps** (all off until you turn them on)
- **Ctrl / Signal look**, shared by every Ctrl app: dark and light, high contrast, one violet accent, Space Grotesk and JetBrains Mono

**Not in TalkCtrl:** Live Intercom and other cloud or internet features, Cardo accounts, paid feature unlocks, crash detection and SOS. VOX on/off and mesh private chat are shown but greyed out until they can be confirmed on a real unit.

## Requirements

- Android 15 or newer, on a 64-bit ARM phone with Bluetooth LE
- A Cardo unit that the Cardo Connect app supports with its newer protocol (Packtalk Edge, Neo, Pro, Outdoor, Custom; Freecom 2X/4X; Spirit and their brand editions). Older units (Packtalk Bold/Slim, Freecom 1–4+, SmartH) use an older protocol; TalkCtrl warns if it sees one
- Works on any Android ROM as a normal app: stock Android, LineageOS, GrapheneOS, CalyxOS, /e/OS and the phone makers' own systems. It doesn't need root.
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google libraries, and the app never contacts Google.

## Install

1. Open the [latest release](https://github.com/VoidedKN0X/TalkCtrl/releases/latest) on your phone, scroll down to **Assets** and tap the file that ends in `.apk` (for example `TalkCtrl-v0.1.0.apk`). The "Source code" zip and tar.gz files are not the app; you don't need them.
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings are kept.

**Automatic updates:** on your phone, tap the Obtainium button above. Install [Obtainium](https://github.com/ImranR98/Obtainium) first; the button adds TalkCtrl and installs every new release for you. If the button doesn't open the app, add `https://github.com/VoidedKN0X/TalkCtrl` in Obtainium by hand.

## Getting started

1. Pair your Cardo unit with the phone in Android's Bluetooth settings as usual.
2. Open TalkCtrl, allow **Nearby devices**, tap **Find units** and pick your unit.
3. The **Unit** tab shows the battery, firmware and name, your profiles, firmware update and factory reset. **Audio**, **Radio** and **Intercom** hold the rest.
4. **Settings** (profile picture): connection type and privacy.

## Firmware updates

**Check for update** sends one HTTPS request to `update.cardosystems.com/firmware/<model>/fw_info.json`, where `<model>` is the model you picked (for example `Packtalk_Edge`). **What's new** fetches `fw_release_notes.json` from the same folder, and **Download** fetches the firmware file the server names, in the voice prompt language you pick. That's all: no account, token, serial number or device ID, a fixed `User-Agent: TalkCtrl`, no cookies, and TalkCtrl refuses to download from any other address.

You can also load a firmware file yourself (`.bin`, `.fot` or Cardo's `.tar.gz`) and tell TalkCtrl its version.

The transfer uses Qualcomm's standard GAIA upgrade over Bluetooth LE, as the Cardo app does (GAIA v1/v2 for most units, GAIA v3 for Flow Lite, chosen from the model you pick): TalkCtrl asks the unit to get ready, unpacks Cardo's `.tar.gz`, then connects, syncs (so an interrupted transfer resumes where it stopped), sends the file in acknowledged chunks, lets the unit check it, restarts it into the new image and confirms the update. The unit checks Cardo's signature and keeps its old firmware until the new one is confirmed. **This transfer is not yet tested on a real unit.** Keep the screen on (TalkCtrl does that while the transfer screen is open), the unit close and charged to at least 50 %.

## Is it safe to install?

- **Open source.** All of TalkCtrl's code is in this repo, under the GPL-3.0 licence. Anyone can read it.
- **Built by GitHub, not on a personal computer.** Every release on the Releases page is built by [the build workflow](.github/workflows/build.yml) on GitHub Actions, straight from the code in this repo. Each build also checks the app's permissions and that it has no Google libraries. The signing key is kept away from the build: one job builds the APK without any secrets, and a separate job that runs no code from this repo signs it and checks the signature against the fingerprint below.
- **Always signed with the same key.** Android refuses to install an update signed with a different key over TalkCtrl. The SHA-256 fingerprint of TalkCtrl's signing certificate is:

  ```
  58:23:2D:34:2F:39:95:FD:F2:12:2F:E2:2A:F1:9E:DB:B5:9C:23:55:68:13:8B:6E:36:4E:A3:15:70:8B:A9:82
  ```

  Advanced users can check it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).

**Check a download.** Every release is built and published by GitHub Actions, never on a personal computer:

- The release notes end with the APK's **SHA-256** checksum. Compare it with `sha256sum TalkCtrl-vX.Y.Z.apk` (or `Get-FileHash` on Windows).
- Each release APK has a signed **build provenance attestation**. With the [GitHub CLI](https://cli.github.com/), `gh attestation verify TalkCtrl-vX.Y.Z.apk -R VoidedKN0X/TalkCtrl` proves it was built by this repo's workflow from the tagged commit.
- The signing-certificate fingerprint above stays the same for every release.

**Permissions.** TalkCtrl has **no location permission**.

| Permission | What TalkCtrl uses it for |
| --- | --- |
| Nearby devices (Bluetooth scan, `neverForLocation`) | Finding your unit. Android can't use it to work out where you are. |
| Nearby devices (Bluetooth connect) | Talking to your unit. |
| Internet | Only the firmware check, release notes and download you start yourself, to `update.cardosystems.com` over HTTPS. The code refuses every other host; the build checks this permission list. |
| Foreground service (connected device) | Keeps a firmware transfer going if you leave the app, with a progress notification and Abort. |
| Notifications | That progress notification. |
| Use biometric hardware | Optional fingerprint unlock for the app lock. |
| Hide other apps' floating windows over the PIN screen | While the lock or PIN screen is showing, other apps' floating windows are hidden over it. |
| App-internal broadcasts (`com.vknxworks.talkctrl.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) | Added by AndroidX so that only TalkCtrl itself can send to its own internal receivers. |

See [Privacy](#privacy) for what happens to your data.

## App lock

Turn the app lock on under **Settings > Privacy and security > App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, checked by the phone's secure hardware. The PIN is still needed after the phone restarts and at least every 72 hours.
- **The PIN is never stored.** It's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Forgot your PIN?** It can't be recovered. The lock screen offers to forget your unit and saved profiles and turn the lock off. Nothing on the unit itself is changed.

## Privacy

**Telemetry-free.** TalkCtrl has no analytics, crash reporting, usage stats or tracking of any kind.

- **Bluetooth only, to your unit.** Everything TalkCtrl does to your intercom goes over Bluetooth between your phone and the unit.
- **One kind of internet request, only when you tap it:** the firmware check, release notes and download, to Cardo's public update server, carrying only the model name in the address. No account, serial number, device ID or location.
- **No cleartext:** the network security configuration forbids unencrypted connections.
- The serial number is shown on the phone only (after a tap) and never sent anywhere.
- The remembered unit (its Bluetooth address and name), your profiles and settings are in TalkCtrl's private storage. Android backups don't include them, and TalkCtrl is left out of Android's cloud backup and its phone-to-phone transfer.
- The Support, Report a bug, Source code and Licence links on the You page open in your browser, only when you tap them.

## How TalkCtrl is made

TalkCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

The protocol notes in [PROTOCOL.md](PROTOCOL.md) were made by reading the Cardo Connect Android app, only to make TalkCtrl work with Cardo units you own. No Cardo code or assets are in TalkCtrl. TalkCtrl isn't made by, affiliated with or endorsed by Cardo Systems; Cardo, Packtalk and Freecom are their trademarks.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 or newer and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/TalkCtrl.git
   cd TalkCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`. `./gradlew testDebugUnitTest` runs the protocol tests.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

**Dependency verification.** Every library and build plugin download is checked against the sha256 checksums in `gradle/verification-metadata.xml`, and the build fails on any mismatch. When you change a dependency, a plugin or the Gradle version, run the **Dependency verification metadata** workflow (Actions tab, *Run workflow*), check the diff of the file it prints and commit it in the same push.

## Support the project

TalkCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Please report bugs and ideas with the [bug report and feature request forms](https://github.com/VoidedKN0X/TalkCtrl/issues/new/choose). If you have a Cardo unit, telling which features work on it helps the most. Starring the repo helps too.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [CameraCtrl](https://github.com/VoidedKN0X/CameraCtrl) | Private camera with RAW (DNG) on every lens |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [GalleryCtrl](https://github.com/VoidedKN0X/GalleryCtrl) | Private, offline gallery with an encrypted vault |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [LumenCtrl](https://github.com/VoidedKN0X/LumenCtrl) | Private, offline photo and video editor |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MapsCtrl](https://github.com/VoidedKN0X/MapsCtrl) | Private, offline maps and navigation |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [ShutterCtrl](https://github.com/VoidedKN0X/ShutterCtrl) | Remote control and photo transfer for Canon cameras |
| [SoundCtrl](https://github.com/VoidedKN0X/SoundCtrl) | JBL headphones and speakers, without the account |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| **TalkCtrl** (this app) | Cardo intercom settings, radio and updates |
| [TapoCtrl](https://github.com/VoidedKN0X/TapoCtrl) | Tapo plugs and bulbs on your home network |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WakeCtrl](https://github.com/VoidedKN0X/WakeCtrl) | Keep the screen awake, per app or while charging |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

TalkCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

Fonts, bundled in the app (nothing is downloaded): [Space Grotesk](https://github.com/floriankarsten/space-grotesk) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), both under the SIL Open Font License 1.1 ([FONT-LICENSE-SpaceGrotesk.txt](FONT-LICENSE-SpaceGrotesk.txt), [FONT-LICENSE-JetBrainsMono.txt](FONT-LICENSE-JetBrainsMono.txt)).
