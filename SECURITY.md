# Security policy

Thank you for helping keep TalkCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/TalkCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of TalkCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

TalkCtrl has no server. It talks to your Cardo unit over Bluetooth, and goes online only for the firmware check, release notes and download you start yourself, to Cardo's public update server. So these count:

- Any way for TalkCtrl to contact a host other than `update.cardosystems.com`, to send anything other than the model name, or to connect without HTTPS
- A firmware file reaching the unit without your confirmation, or TalkCtrl installing firmware that isn't newer than the unit's
- Another app being able to start a firmware transfer or send commands to your unit through TalkCtrl
- The serial number or other unit details leaving the phone
- Ways around the app lock, or protected screens showing up in screenshots when they shouldn't
- Weaknesses in the app lock PIN
- Anything that lets someone run code or read files they shouldn't

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
58:23:2D:34:2F:39:95:FD:F2:12:2F:E2:2A:F1:9E:DB:B5:9C:23:55:68:13:8B:6E:36:4E:A3:15:70:8B:A9:82
```

The key never meets the build: the job that runs Gradle (and with it every plugin and library) has no secrets and produces an unsigned APK. A separate job that checks out nothing and runs no build code signs that APK, then refuses to pass it on unless it is signed by exactly this certificate and its contents are unchanged.

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
