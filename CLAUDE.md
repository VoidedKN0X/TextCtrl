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

## Build toolchain

- AGP 9.5.1, Gradle 9.9.2, Kotlin 2.4.21, Compose BOM 2026.09.00, `compileSdk` 37 (Compose 1.12 needs it), `minSdk` 35, `targetSdk` 36. All versions are in `gradle/libs.versions.toml`.
- AGP 9 has built-in Kotlin: there is no `org.jetbrains.kotlin.android` plugin any more. The Kotlin version is pinned by the `kotlin-gradle-plugin` classpath entry in the root `build.gradle.kts`; the Compose and serialization compiler plugins use the same `kotlin` version. Don't re-add the Kotlin Android plugin.
- AGP 9's R8 is stricter (`-keep class X` without a member block no longer keeps the constructor; resources are shrunk together with code). Keep rules in `proguard-rules.pro` must name the members they need, and resources must be referenced through `R`, never by name.
- Any change here needs the dependency verification procedure below.

## Dependency verification

Gradle checks every downloaded library and plugin against `gradle/verification-metadata.xml` (sha256 only, no PGP). When a dependency or plugin changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/TextCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), decode the file from its log (gzip + base64 between the BEGIN/END markers; it already trusts `-sources.jar` and `-javadoc.jar` for Android Studio), check the diff and commit it in the same push. Never turn verification off or make it lenient in the real build.

`settings.gradle.kts` lists only `google()` and `mavenCentral()` for plugins, not `gradlePluginPortal()`: the portal serves different files for the Kotlin plugin markers, which made verification fail whenever Maven Central had a hiccup. Don't add it back unfiltered (see the comment in that file).

## Build and signing (CI)

`.github/workflows/build.yml` has four jobs: `version` (is this a release?), `build` (Gradle, **no secrets**, `UNSIGNED_RELEASE=true` gives `app-release-unsigned.apk`; runs every check), `sign` (no checkout, no Gradle: `zipalign` + `apksigner` with the release key, then checks the signer against `EXPECTED_CERT_SHA256` and that the APK's contents didn't change) and `release` (attestation, release notes, publish).

- Never give the `build` job a secret, and never run Gradle or check out the repo in `sign`. New build checks (tests, lint, APK checks) go in `build`.
- `EXPECTED_CERT_SHA256` in `build.yml` must match the fingerprint in `SECURITY.md` and `README.md`; the build fails if it doesn't.
- No Gradle cache in any workflow (`cache-disabled: true`): a cache saved by a failed run used to break every later build.

## Encryption at rest

- Only unsent drafts (`shared_prefs/chats`, `draft_<threadId>`) are TextCtrl's own content; messages live in Android's Telephony provider and are out of scope. Scheduled messages and the hidden-chat list were already Keystore-encrypted (`KeystoreCrypto`, alias `textctrl_data`) and are untouched.
- A draft is stored in one of three forms, and reading accepts all three whatever the lock state: `vault1:…` (app lock on: `LockVault`'s vault key, unchanged), `TEXTCTRL-REST1:` + Base64 of `SealedFile` bytes (`data/crypto/SealedText`, app lock off and data key in memory), or ordinary text (no data key). All rules are in `data/settings/Drafts.kt` (pure Kotlin, tested by `DraftsTest`); `ChatPrefs` only adapts it to SharedPreferences and `LockVault`.
- The data key (`DataKeyStore`: 32 random bytes in `filesDir/data.key`) is wrapped by the Keystore key `textctrl_data_at_rest` (`KeystoreWrapper`/`KeystoreAes`; no user authentication). `ChatPrefs.prepareDrafts()` loads it on `Dispatchers.IO` from `TextCtrlApp.onCreate` and then seals drafts that are still ordinary text. Never call `prepareDrafts`/`retryDraftKey` on the main thread; `Drafts` only ever uses a key that is already in memory, because the composer reads and saves on the main thread.
- Never delete a sealed draft that can't be opened: an empty save leaves it, a new draft moves it to `draft_unreadable_<threadId>` (one sealed value per line), `recoverDraft` hands it back to the chat once it opens, and only deleting the chat (`discardDraft`) removes it. Never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`).
- Receiving, sending, scheduled sends and notifications must not depend on the data key.
- `SealedFileTest` and `DraftsTest` are JVM tests (`./gradlew :app:testDebugUnitTest`; CI doesn't run unit tests).
