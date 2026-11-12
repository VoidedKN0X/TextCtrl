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
