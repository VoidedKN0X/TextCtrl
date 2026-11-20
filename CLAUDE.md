# TextCtrl

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "TextCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.

## Dependency verification

Gradle checks every downloaded library and plugin against `gradle/verification-metadata.xml` (sha256 only, no PGP). When a dependency or plugin changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/TextCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), decode the file from its log (gzip + base64 between the BEGIN/END markers; it already trusts `-sources.jar` and `-javadoc.jar` for Android Studio), check the diff and commit it in the same push. Never turn verification off or make it lenient in the real build.

## Encryption at rest

- Only unsent drafts (`shared_prefs/chats`, `draft_<threadId>`) are TextCtrl's own content; messages live in Android's Telephony provider and are out of scope. Scheduled messages and the hidden-chat list were already Keystore-encrypted (`KeystoreCrypto`, alias `textctrl_data`) and are untouched.
- A draft is stored in one of three forms, and reading accepts all three whatever the lock state: `vault1:…` (app lock on: `LockVault`'s vault key, unchanged), `TEXTCTRL-REST1:` + Base64 of `SealedFile` bytes (`data/crypto/SealedText`, app lock off and data key in memory), or ordinary text (no data key). All rules are in `data/settings/Drafts.kt` (pure Kotlin, tested by `DraftsTest`); `ChatPrefs` only adapts it to SharedPreferences and `LockVault`.
- The data key (`DataKeyStore`: 32 random bytes in `filesDir/data.key`) is wrapped by the Keystore key `textctrl_data_at_rest` (`KeystoreWrapper`/`KeystoreAes`; no user authentication). `ChatPrefs.prepareDrafts()` loads it on `Dispatchers.IO` from `TextCtrlApp.onCreate` and then seals drafts that are still ordinary text. Never call `prepareDrafts`/`retryDraftKey` on the main thread; `Drafts` only ever uses a key that is already in memory, because the composer reads and saves on the main thread.
- Never delete a sealed draft that can't be opened: an empty save leaves it, a new draft moves it to `draft_unreadable_<threadId>` (one sealed value per line), `recoverDraft` hands it back to the chat once it opens, and only deleting the chat (`discardDraft`) removes it. Never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`).
- Receiving, sending, scheduled sends and notifications must not depend on the data key.
- `SealedFileTest` and `DraftsTest` are JVM tests (`./gradlew :app:testDebugUnitTest`; CI doesn't run unit tests).
