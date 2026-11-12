# Changelog

What changed in each TalkCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/TalkCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

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
