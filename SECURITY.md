# Security policy

Thank you for helping keep TextCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/TextCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of TextCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

TextCtrl is your phone's SMS app and has no internet permission. So these count:

- Any way for TextCtrl to send your messages, contacts or other data off the phone, or to send a text or picture message you didn't ask for
- Another app on the phone being able to read TextCtrl's data, or make it send, delete or show something (through its share and "text this number" screens, widget, notifications or receivers)
- A received text or picture message that can make TextCtrl run code, write files where it shouldn't, or crash in a way that loses messages
- Ways around the app lock or the hidden-chats PIN, or protected screens showing up in screenshots when they shouldn't
- Weaknesses in the encryption of backups, drafts, scheduled messages, filter rules or hidden chats, or in the PINs
- Anything that lets someone run code or read files they shouldn't

Your texts themselves live in Android's shared message store, which every SMS app uses; how Android protects that store is up to Android.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
CF:93:CF:96:08:49:56:90:F2:FC:82:58:86:6D:A0:89:1C:8C:CA:63:D7:5A:DC:AD:86:7B:20:92:57:F3:94:B3
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
