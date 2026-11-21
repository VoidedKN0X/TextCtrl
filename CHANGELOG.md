# Changelog

What changed in each TextCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/TextCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.9.0 (2026-11-21)

Built and checked by CI only: nothing in this release had been run on a phone when it was published. It is the first release built with the new build tools (Android Gradle Plugin 9, stricter code shrinking), so update over your installed version and check that your data and settings are still there.

### Security

- **Drafts are encrypted on the phone when the app lock is off too.** Until now an unsent draft was only encrypted while the app lock was on (with your PIN); with the lock off it sat as readable text in TextCtrl's own settings file. Drafts are now also encrypted with the lock off (AES-256-GCM), with a key that is itself protected by a key in the phone's secure hardware, so a copy of TextCtrl's files doesn't show what you were typing. With the app lock on nothing changes: drafts keep being encrypted with your PIN exactly as before. This protects the files, not a phone someone is using unlocked.
  - **What is and isn't encrypted.** Encrypted: the text of your drafts. Already encrypted before, unchanged: scheduled messages and the list of hidden chats. Not encrypted: which chats have a draft, are pinned, archived, muted or in Spam (TextCtrl needs those before any key is loaded), and your settings. Your messages themselves are in Android's message store, like with every SMS app, and aren't TextCtrl's to encrypt.
  - **Moving over.** Drafts you already have are encrypted in the background shortly after TextCtrl starts; each one is checked by decrypting it again before it replaces the readable text. If the key can't be made or loaded, drafts are simply read and saved as readable text as before, and encrypted on a later start.
  - **If the key is ever lost** (for example after restoring the phone from another one), a draft encrypted with it can't be opened. TextCtrl never deletes such a draft on its own: the chat shows a small line above the text field with **Try again**, the draft stays stored, and if you type a new draft the old one is kept aside. It only goes when you delete the chat.
  - **Older versions can't read these drafts.** If you install an older TextCtrl over this one, drafts written by this version show up as a line of unreadable characters. Send or copy drafts you care about first.
  - Receiving, sending, scheduled messages and notifications don't use this key and work as before.

### Changed

- **Chats and the chat list are read a little faster.** For every message, chat and picture-message part it read from Android's message store, TextCtrl looked up where each column was, field by field. It now looks that up once per read. Most noticeable in long chats and while searching in a chat (which reads up to 5,000 messages). What is read and shown is the same.
- **A lighter start when you have hidden chats.** The list of hidden chats is encrypted with a key in the phone's secure hardware, and decrypting it held up every start of TextCtrl (also the background starts for each incoming message). It is now decrypted on a separate thread while the rest of the app starts. Anything that needs the list (the chat list, search, a chat, notifications) waits until it is there, so a hidden chat can never show up or notify with its name because the list wasn't read yet. Without hidden chats nothing changes.
- **Searching all messages stops reading picture messages once it has enough.** The search only ever used the first 200 picture messages whose text matched, but read every match first. It now stops at 200, with the same results.
- **Newer build tools and libraries; nothing changes in how TextCtrl looks or works.** TextCtrl is now built with the current stable toolchain: Android Gradle Plugin 8.11.1 → 9.5.1, Gradle 8.14.3 → 9.9.2, Kotlin 2.2.0 → 2.4.21 (with its Compose and serialization compiler plugins) and Compose BOM 2025.07.00 → 2026.09.00 (Compose 1.12.1, Material 3 1.4.0). The libraries move to the versions the newest Ctrl apps use: activity-compose 1.10.1 → 1.13.0, core-ktx 1.16.0 → 1.18.0, lifecycle 2.9.1 → 2.10.0, navigation-compose 2.9.1 → 2.9.8, datastore-preferences 1.1.7 → 1.3.0, kotlinx-serialization-json 1.9.0 → 1.13.0 and kotlinx-coroutines-android 1.10.2 → 1.11.0. Coil (3.2.0) is unchanged. No library was added, and CI still checks that no Google Play services, Firebase, ML Kit, Play Core or Tink library is in the app.
- **Compiled against the Android 17 SDK.** Compose 1.12 needs `compileSdk` 37, so that goes from 36 to 37. The Android versions TextCtrl runs on and targets are unchanged (`minSdk` 35, `targetSdk` 36), so Android treats the app exactly as before.
- **The app is shrunk by a newer R8.** Android Gradle Plugin 9 compiles Kotlin itself (the separate Kotlin Android plugin is gone from the build files) and brings the R8 version that understands Kotlin 2.4. The release APK is about 1.4% larger (4.33 MB → 4.39 MB). The newer R8 shrinks more strictly than the old one, so the release build needs a check on a phone before the next version is published. TextCtrl's own keep rules are unchanged.
- The checksums Gradle verifies every download against (`gradle/verification-metadata.xml`) were regenerated on CI for the new versions. Built and checked by CI only; not yet run on a phone.

### Project

- **The signing key is no longer available to the build.** Until now the release key and its passwords were present in the same GitHub Actions job that runs Gradle, so any build plugin or library could in principle have read them. The build job now has no secrets at all and produces an unsigned APK. A separate job, which checks out no code and runs no Gradle, signs it with `apksigner`, and then refuses to hand it on unless it is signed by exactly the certificate published in SECURITY.md and README.md and its contents are identical to what the build job made. Same key and same signature scheme (v2) as before, so updates will install as usual. This changes how TextCtrl is built and signed, not the app itself. Seen working on CI for builds that don't publish; the publishing step runs with it for the first time at the next release.
- **The build checks that the published fingerprint and the enforced one are the same.** If `build.yml`, SECURITY.md and README.md ever disagree about the signing certificate, the build fails.
- **Builds no longer use a saved Gradle cache, and the Gradle plugin portal is no longer a plugin source.** Other Ctrl apps had builds fail with "Dependency verification failed ... from repository Gradle Central Plugin Repository", and keep failing until the caches were deleted by hand. The cause (found on DateCtrl): when Maven Central can't be reached for a moment, Gradle takes the Kotlin plugin "marker" file from the Gradle plugin portal instead, the portal serves a different file under the same name, verification rightly refuses it, and the failed run then saved that state as the cache for every later run. Now every build and the code scan start from a clean Gradle home, and `settings.gradle.kts` lists only Maven Central and Google's Maven for plugins. Dependency verification itself is unchanged and as strict as before. Builds take a minute or two longer.

## v0.8.0 (2026-11-20)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Removed

- **The Recent chats home screen widget is gone.** TextCtrl no longer offers a widget: its code, layouts, colours and texts were removed, and the app no longer refreshes a widget in the background after every message, contact or setting change. A Recent chats widget already on your home screen stops working after the update and can be removed. The "New chat" launcher shortcut, conversation shortcuts and notifications are unchanged.

### Changed

- **The chat list reloads faster on phones with a long history.** Every time a message arrived or changed, TextCtrl went through every message you ever sent to work out which chats you have written in (that decides which chats count as "unknown senders"). It now remembers the answer while the app is running and only looks at messages sent since the last check. It still reads everything again after TextCtrl deletes a message or chat, when a chat has fewer messages than before, and for as long as another app is your SMS app, so the result is the same as before.
- **Chats with many picture messages open and refresh faster.** Who a picture message is from or to was asked from Android's message store once per message on every refresh. Answers for messages older than five minutes are now remembered while the app is running, and forgotten whenever TextCtrl writes, restores or deletes messages.
- **Less work for the phone's secure hardware.** The key that protects scheduled messages and the list of hidden chats is looked up once per app start instead of on every read and write. The key itself still never leaves the secure hardware; if the remembered handle stops working, TextCtrl looks the key up again and retries.
- **A slightly lighter app start.** The backup, XML import/export and filter import/export helpers are only created when their settings screens open, instead of on every start (TextCtrl also starts in the background for each incoming message).
- **Smoother scrolling in a chat.** The message list now tells Android which rows are day headings, messages and scheduled messages, so rows are reused for the same kind of row.

## v0.7.1 (2026-11-20)

### Changed

- **The app icon matches the other Ctrl apps.** TextCtrl's icon was drawn in a darker set of greys than the rest of the series: a near-black background, a darker keycap and a front bar you could hardly see. It now uses the same background, keycap and bar colours as the other Ctrl apps, so the icons look like one family side by side. The purple symbol and the themed (monochrome) icon are unchanged.

## Project updates (2026-11-19)

### Project

- **The weekly code scan no longer fails at random.** CodeQL has to watch the app being compiled, but its build could take the compiled code from Gradle's build cache instead, and then the scan stopped with "CodeQL detected code written in Java/Kotlin but this run didn't build any of it" (and GitHub mailed a failed run). The scan's build now runs with `--no-build-cache`, so the code is always compiled. No app changes, so no new version.

## v0.7.0 (2026-11-13)

### New

- **Rules for exact numbers and sender names.** A new filter, "From these numbers or sender names", catches messages from the numbers or names you list (BANK, DHL-Info…). A number matches however it's written: 0470 12 34 56 and +32 470 12 34 56 count as the same.
- **Block rules block the sender.** A rule set to "Block the sender, keep it in Spam" now puts the sender on Android's own block list (shared with your phone app, so their calls stop too) and files the message in Spam. You'll see the sender under You › Blocked numbers and can unblock them there.
- **Summary notification for filtered messages** (off by default; Filters › Summary notification). One quiet notification, without sound, counts the messages your rules moved to Spam. It never shows who sent them or what they say. Tapping it opens the Spam folder, which also resets the count.
- **Import and export your filters.** Filters › Import and export saves your rules and the numbers on Android's block list to a JSON or CSV file you pick, and reads them back. Importing only adds what isn't there yet; nothing is removed. The CSV opens in any spreadsheet. Blocked numbers can be imported while TextCtrl is your SMS app.
- **Safer patterns.** Pattern (regex) rules that could take a very long time on some messages, like `(a+)+` or back-references, are refused with an explanation when you save them, and a pattern that still takes too long on a message counts as no match and is marked "Too slow, skipped".

### Changed

- **Rules never delete a message any more.** Until now, a Block rule threw the message away without storing it. It now keeps the message in the Spam folder and blocks the sender instead, so nothing is ever lost to a rule. Messages that arrive after that are stopped by Android itself, as for any blocked number.
## v0.6.10 (2026-11-13)

### Security

- **Replying from the lock screen needs the phone unlocked.** When your lock screen shows full notifications, anyone holding the phone could type a reply under a text and send it as you. Android now asks you to unlock first (fingerprint, face or PIN), then the reply goes out as before. Replying with the phone unlocked works exactly as it did.
- **"Copy code" from the lock screen needs the phone unlocked too**, so nobody can put a one-time code from your texts on the clipboard without unlocking it.
- **Your scheduled messages and hidden-chat list can use the phone's security chip.** On a new install, the key that encrypts them is made in the phone's separate security chip (StrongBox) when it has one, and in its regular secure hardware otherwise. If you already use TextCtrl, your existing key stays where it is and nothing changes for you.
- **The build tool download is checked.** The exact Gradle version used to build TextCtrl is now verified against its published checksum before it's used.

### Fixed

- **A contact card sent to you can't freeze the app.** A card built with thousands of tiny folded lines took a very long time to read when its message was shown. Cards are now read in one quick pass, however they're laid out.
- **No lost key on a first start that does two things at once.** If a text arrived at the very moment TextCtrl first set up its encryption key, two keys could be made and what was saved with the first one lost. Making the key now happens one at a time.

## v0.6.9 (2026-11-12)

### Fixed

- **Opening or sharing an attachment no longer holds up the screen.** The picture, video or file was copied out of the message store on the same thread that draws the screen, so the app could freeze for a moment on a big attachment. The copy now runs in the background and the other app opens when it's done.

### Changed

- **Slightly smaller download.** The bundled JetBrains Mono font (numbers, readouts and labels) no longer carries the Cyrillic, Vietnamese and maths-alphabet letters TextCtrl never shows itself, which takes about 0.1 MB off the download and 0.2 MB off the installed app. Everything looks the same; if a name or a log line shown in that font uses one of those scripts, those letters are now drawn with your phone's own font.

## v0.6.8 (2026-11-05)

### Changed

- **Needs Android 15 or newer.** Android 13 no longer gets security updates and Android 14's updates end in early 2027, so TextCtrl now supports Android 15, 16 and 17 only. On older phones the version you have keeps working, but it won't get updates.

## v0.6.7 (2026-11-05)

### Fixed

- **Password managers can fill the backup password again.** Since 0.6.6 the backup password dialog had been hidden from every accessibility service that isn't a screen reader, which also kept password managers that fill through accessibility from filling them. Only the lock and PIN screens are hidden from those apps now. Other apps' floating windows stay hidden over all of these screens, as in 0.6.6.

## v0.6.6 (2026-11-05)

A security update. Nothing changes in how you text.

### Security

- **No other app's floating window over the PIN and confirmation screens.** While the lock screen, PIN entry or change, the hidden chats PIN, the backup password or a "can't be undone" question (like deleting a chat) is showing, Android hides other apps' floating windows (chat heads, screen filters, overlays), so none can cover the screen or trick you into tapping the wrong button. They come back as soon as that screen closes and work as usual everywhere else. This uses Android's "hide other apps' floating windows" permission, which is granted at install without a prompt.
- **PIN and password screens are hidden from apps that misuse accessibility** (Android 14 and newer). Real accessibility tools such as TalkBack still read them; other apps with accessibility access can't see the PIN pad or the backup password.
- **Shares must really come from the sending app** (Android 15 and newer). When another app shares pictures to TextCtrl, Android now checks that the sending app can open them itself and refuses the share if it can't. This blocks tricks where an app passes on something it isn't allowed to read. Sharing from your gallery, camera or file manager works as before.
- **Stricter intent matching** (Android 16 and newer). Other apps can only start TextCtrl through the actions it declares. The launcher, the "New chat" shortcut, conversation shortcuts, the widget, "Text this number" links, shares, quick replies to declined calls and incoming texts work as before.

### Project

- **Verified dependencies:** every library and plugin the build downloads is now checked against SHA-256 checksums in the repo, and the build stops if anything doesn't match.
- **Provable releases:** from this release on, the release notes list the APK's SHA-256, and each APK has a signed build provenance attestation from GitHub. The README's new "Check a download" section shows how to check both.

## Project updates (2026-11-05)

Changes to how the app is built and released. Nothing changes in the app.

- **Every release is now provable.** From the next release on, each APK gets a signed build provenance attestation from GitHub, showing it was built by this repo's workflow from the tagged commit, and the release notes list the APK's SHA-256 checksum.
- **The build workflows are audited.** A new check runs [zizmor](https://docs.zizmor.sh) on every workflow change, and every workflow now starts with no permissions and gives each job only what it needs.
- **Code scanning.** CodeQL now analyses the app's code every week and on every app change; results go to the repo's Security tab.
- **Groundwork for verified dependencies:** a manually started workflow writes the checksums of every library and plugin the build downloads, so the build can refuse anything that doesn't match.

## v0.6.5 (2026-11-05)

A security update: resetting a forgotten PIN needs a stronger check, and shares from other apps can no longer pull in your texts or contacts.

### Security

- **"Forgot PIN?" no longer accepts camera face unlock.** Resetting the app lock (or the hidden chats PIN) asks you to confirm it's you with your phone's own screen lock. It used to accept any face unlock, including the camera-only kind many phones have, which a photo can fool. It now only accepts strong biometrics (like fingerprint, or secure face unlock on phones that have it) or your screen lock's PIN, pattern or password. If your phone only has camera face unlock, you'll be asked for the PIN, pattern or password instead.
- **Shares can no longer attach your texts or contacts.** When another app shares something to TextCtrl, TextCtrl opens it with its own permissions. A rogue app could share a link to Android's message or contact store, which it can't read itself, and get TextCtrl to attach your SMS, MMS pictures or contact cards to a new message. TextCtrl now refuses attachments from those stores. Sharing photos, files and text from other apps works as before.
- **Other apps can no longer poke TextCtrl's start-up profile.** An AndroidX library added a receiver that lets other apps ask TextCtrl to write or skip its start-up performance profile. TextCtrl doesn't need it, so it's removed. Nothing changes in how the app works.

### Project

- **README:** the "Forgot your PIN?" note says which screen-lock checks count.

## v0.6.4 (2026-11-05)

A privacy and security tidy-up. Nothing changes in how you text.

### Privacy

- **Less logging in release builds.** Verbose, debug and info log lines, from TextCtrl and from the libraries it uses, are now stripped from the app when it's built, so no message details can end up in the phone's system log through them.

### Project

- **Every build now proves TextCtrl stays offline:** the build fails if the app ever gets the internet or a network permission, or if anything from Google Play services, Firebase, ML Kit, Play Core or Tink ends up in it. It also checks that the Gradle wrapper in the repo is the official one before running it.
- **The repo ignores signing files and `google-services.json`**, so they can never be committed by accident.
- **New SECURITY.md:** how to report a security problem privately, what counts, and the fingerprint of TextCtrl's release signing key.
- **README:** a new **Permissions** section lists every permission the app has with the reason for each; the privacy section lists the links in About and says TextCtrl is Google-free; the Ctrl series list now has all 28 apps, with ChatCtrl instead of its old name ElementCtrl.

## v0.6.3 (2026-10-30)

### Changed

- **Bubbles are gone.** New messages no longer float as a bubble over other apps. The **Bubbles** setting (and the Notifications section it was in), **Show as bubble** in a chat's menu and the bubble window itself are removed, along with the saved setting and the list of chats that had bubbles. Notifications work as before, with **Reply**, **Mark as read** and **Copy code**; tap one to open the chat in TextCtrl.
- Chats keep their full-size picture in Android's conversation list (contact photo, initial or group picture) from 0.6.2.
- **README:** the bubble feature and the note on bubbles per phone brand are removed.

## v0.6.2 (2026-10-29)

Bubbles look right on more phones.

### Fixed

- **Bubble pictures fill the whole bubble.** A contact's photo was shown as a smaller circle inside the bubble, so the bubble looked smaller and further from the screen edge than it is. The gap was biggest on OPPO, OnePlus and realme phones (ColorOS, OxygenOS, realme UI), which add padding of their own. Bubbles and the chat's icon in Android's conversation list now use a full-size picture on every phone: the contact's photo, their initial on their colour when there's no photo, or a picture for group chats (two initials, or two outlines), instead of TextCtrl's app icon for people without a photo.
- **No empty bars inside an open bubble.** Some phones told the bubble about the status bar and navigation bar even though a bubble never sits under them, which left empty space at the top and bottom of the bubble and its lock screen. That space is gone; the keyboard still pushes the text field up as before.

### Changed

- **README:** a note on how bubbles differ per phone brand. Where a bubble rests on the screen and how far it sits from the edge is set by the phone's own software, which no app can change.

## v0.6.1 (2026-10-25)

A safety and clean-up release: incoming texts are even harder to lose, and closing a locked TextCtrl no longer risks a crash.

### Fixed

- **Incoming texts are never lost to a hiccup.** If the phone's message store is briefly busy when a text arrives, TextCtrl now tries again before giving up, and a problem with one text in a delivery no longer stops the others from being saved. A filter rule that fails always lets the text through.
- **No crash when closing TextCtrl with the app lock on.** With **Lock again: As soon as you leave the app**, pressing Back on the chat list or swiping TextCtrl away from Recents could crash it while the lock screen (or the fingerprint prompt) tried to open on a closing window. TextCtrl still locks straight away and opens locked next time. The same applies to bubbles.
- **A slow filter pattern can't hold up your messages.** A regular expression that takes too long on a message (some patterns can run for minutes) now counts as no match after a fraction of a second and is skipped from then on, instead of delaying the message and its notification.
- **Background tasks can't crash the app.** Saving filter rules or scheduled messages, updating the widget and other work done in the background now fail quietly instead of closing TextCtrl, for example when the phone's secure hardware is briefly busy.
- **Links and shares from other apps** with broken data are ignored instead of crashing TextCtrl.
- **Export to an XML file** no longer leaves half a file behind if the phone runs out of memory, and a cancelled export or import resets its progress bar.

### Security

- **Safer XML import.** Files are read without processing document type declarations, so a crafted file can't pull in outside content or blow up in memory through entities. A picture message in a file with absurdly many or huge attachments is skipped (and counted as unreadable) instead of exhausting memory.

### Changed

- Internal cleanup: removed unused code.

## v0.6.0 (2026-10-25)

Your own spam and keyword filters with a Spam folder, and moving messages to and from other SMS apps with SMS Backup & Restore files.

### New

- **Filters.** Under your profile picture › **Filters**, add your own rules to catch texts and picture messages: with certain words or phrases, matching a pattern (regular expression, for those who want it), from numbers starting with a code you choose (for example +44 or 0900), from short numbers of 3 to 8 digits, from senders with a name instead of a number, or from numbers not in your contacts. Each rule can be switched off without deleting it and shows how many messages it caught.
- **What a rule does, per rule:** **Block** throws the message away the moment it arrives, so it's never stored or shown; **Move to Spam** keeps it in the Spam folder; **Keep, but don't notify** leaves it in its chat as usual without a notification. When several rules match, the strictest one wins.
- **Checked before any notification.** Rules run on the phone as soon as a message arrives, before anything is notified. Rules about the sender already apply to the notice of a picture message, rules about the text once it's fetched.
- **Never filter my contacts** (on by default): messages from people in your contacts, and chats you've written in, always come through.
- **Spam folder.** Under your profile picture › **Spam**: chats your filters moved there, kept out of Chats, Archived, Search and the widget, and never notified. Select chats to mark them **Not spam** (they go back to Chats and your filters leave them alone from then on) or delete them. You can also move any chat to Spam yourself, from its menu or by selecting it in the chat list (**More › Move to Spam**).
- **Import from SMS Backup & Restore.** Under **Backup and restore › Import from an XML file**, pick a backup made by SMS Backup & Restore (or any app using its format). Texts and picture messages, with their pictures, files and group chats, are added to your phone's messages with a progress bar and a summary of how many were imported, already there or unreadable. The file is read bit by bit, so big backups with lots of pictures work too. Messages already on the phone (same time, same number and same text) aren't added twice, so importing the same file again does nothing.
- **Export to SMS Backup & Restore.** **Export to an XML file** saves every text and picture message in the same format, wherever you choose, so you can take them to another SMS app or back up with other tools. TextCtrl asks first, because these files aren't encrypted (other apps can't read encrypted ones); TextCtrl's own encrypted backup is unchanged.

### Privacy

- There's still no spam database and nothing goes online: TextCtrl has no internet access, and all filtering happens on the phone.
- Your filter rules are stored encrypted with a key in the phone's secure hardware, so they work while the app is locked.

## v0.5.0 (2026-10-25)

Send contact cards, swipe chats to archive or delete them, and read and reply in floating bubbles.

### New

- **Send a contact.** In a chat, tap **+** › **Contact** and pick someone from your phone's contacts. TextCtrl makes a contact card (vCard 3.0, the format every phone understands) and sends it as a picture message. With the contacts permission the whole contact goes: name, every phone number and email address, company and job title, postal addresses and websites (never their photo, notes or birthday). Without it, Android's contact picker lets you pick one number, and only that name and number are sent.
- **Contact cards in chats.** Contact cards you send or receive now show as a card with the person's name, company and numbers instead of a file name. Received cards have an **Add to contacts** button that opens your contacts app with everything filled in, so you can check it and save. Older cards from other phones (vCard 2.1, also with accented letters) are read too; a card that can't be read still shows as a file you can open.
- **Swipe actions.** Swipe a chat left or right in Chats or Archived to act on it straight away. Choose what each direction does under Settings › Chat list: archive, delete, mark as read or unread, pin, mute, block, or nothing. Out of the box, swipe right marks as read or unread and swipe left archives. The colour and label behind the row show what will happen before you let go.
- **Undo for swipes.** Archive, delete and block show an **Undo** bar for a few seconds instead of asking first. A deleted chat disappears at once and is only really deleted once the bar has gone.
- **Bubbles.** New messages can float as an Android bubble over other apps: tap it to read the whole chat and reply without leaving what you were doing, and drag it away to close it. Turn it on under Settings › Notifications › Bubbles, for all chats or only the chats you choose (then use **Show as bubble** in a chat's menu). If bubbles are turned off for TextCtrl in Android's settings, TextCtrl tells you and opens that setting.

### Security

- **Bubbles respect the app lock.** While TextCtrl is locked, a bubble shows the lock screen and nothing of the chat; it unlocks with your PIN or fingerprint like the app itself. With the app lock on, the chat's conversation shortcut that Android needs for a bubble has no name or picture.
- **Hide in screenshots** and **Hide in recent apps** apply to bubbles too, and the lock screen inside a bubble is always kept out of screenshots.
- **Hidden chats never bubble**, and the bubble screen can only be opened by TextCtrl's own notifications.
- No new permissions: picking a contact without the contacts permission only gives TextCtrl that one number.

## v0.4.0 (2026-10-25)

Light mode that follows the device theme: TextCtrl is now light when your phone is set to light and dark when it is set to dark, and switches by itself (also with a dark-mode schedule). There is still no theme setting in the app, and no wallpaper colours.

### New

- **Signal Light.** A warm off-white background (#F5F5F1) with white cards, sheets and dialogs, thin light-grey borders (#DEDED7 on cards, #D2D2CA on buttons, fields and the tab bar), light dividers between rows and near-black text (#0B0B0D) with grey secondary text.
- **Messages in light mode.** Messages you receive are white with a thin #DEDED7 border; your own messages keep the violet fill with dark text (or the bubble colour you picked). Links, sender names and contact initials are darkened slightly so they read on white.
- **Accent that stays readable.** Violet fills (the open tab, primary buttons, switches that are on, PIN dots, your bubbles, unread counters, the lock and profile badges) are the same #B48CFF in both modes. Violet text and icons on a light background, such as the slash in TEXT/CTRL, unread times, links, the Copy code button, focused text fields, the fingerprint key and scheduled-message times, use a deeper violet (#6A3BDB) so they stay readable.
- **Switches in light mode** are light grey with a grey knob when off, violet with a dark knob when on.
- **Lock screen in light mode:** white keys with a thin border, empty PIN squares with a grey outline, the same violet lock badge and fingerprint key.
- **Floating tab bar in light mode:** white with a thin border; the open tab is still a violet block.
- **Status and navigation bars** match the background in both modes, with dark icons in light mode and light icons in dark mode.
- **Widget** follows the phone's setting too: light background and white rows in light mode, dark in dark mode.
- **Notifications** use the deeper violet accent while the phone is in light mode.

### Changed

- The dark look from 0.3.0 is unchanged and still used whenever your phone is in dark mode.
- Dialogs, menus, choice sheets, date and time pickers, the backup progress bar, empty-state badges and error messages all have light versions (errors use #D93636 on light).
- The full-screen picture viewer stays black in both modes, so photos are shown without distraction. The launcher icon is unchanged.

## v0.3.0 (2026-10-25)

A new look: TextCtrl now uses **Ctrl / Signal**, the design shared by every Ctrl app. Everything works the same as before; only the way it looks has changed.

### Changed

- **New colours.** A near-black background (#08080A) with dark grey cards and a violet accent (#B48CFF). Cards, sheets and dialogs have a thin border instead of a shadow, and the status and navigation bars blend into the background with light icons.
- **New fonts.** Space Grotesk for all text and headings, and JetBrains Mono for times, numbers, counters and section labels. Both fonts are built into the app (no download, nothing from Google).
- **Top bar.** The main tabs show a TEXT/CTRL wordmark (with the slash in the accent colour) and a square profile button on the right, with a small accent square while TextCtrl is your SMS app. Tapping it opens the You page as before; the tab's name is shown in big type below it.
- **Pushed screens** (a chat, Settings, Blocked numbers, Backup and so on) have a square back button and the title next to it, without a coloured bar.
- **Tab bar.** The floating glass tab bar is replaced by a solid floating bar with rounded corners. The open tab is an accent block with its icon and name; the other tabs show only their icon. On tablets the side rail uses the same accent block.
- **Chat list.** Rows are separated by thin lines; names are a little lighter, times are in mono type (accent when the chat is unread), and the unread counter is a rounded square. Contact pictures and initials are rounded squares instead of circles.
- **Filters** (All, Unread, Unknown senders, and Everyone on the Search tab) are rounded rectangles that fill with the accent colour when chosen.
- **Message bubbles.** Your messages are filled with the accent colour (or the bubble colour you picked) with dark text; messages you receive are dark grey with a thin border. Both have 18dp corners with a smaller corner by the sender. Day headings, times and delivery status use mono type, and reactions and the **Copy code** button are rounded rectangles.
- **Text field and send button.** The message field is a rounded rectangle with a thin border that turns violet while you type; the attach and send buttons are rounded squares (the send button fills with the accent colour when there is something to send). Holding send still schedules the message.
- **Switches** are rounded rectangles: violet with a dark knob when on, dark grey with a grey knob when off. The whole row still toggles, and TalkBack still reads each one as a switch.
- **Settings and the You page.** Settings are grouped in cards with thin lines between rows, under mono section labels (PRIVACY, MESSAGES …). The You page has a violet app badge with your number (or whether TextCtrl is your SMS app), a close button, a violet **Support TextCtrl** button near the bottom and a "GOOGLE-FREE · TELEMETRY-FREE · ARM64" footer.
- **Lock screen.** A violet lock badge, "VKNXWORKS · CTRL" and a big **Locked** title; the PIN shows as small squares that fill in violet; the keypad has large rounded keys in mono type, with the fingerprint key outlined in violet next to 0 and the delete key on the other side. The same keypad is used to choose or change your PIN and for hidden chats. How the lock works hasn't changed.
- **Dialogs and menus** (confirmations, the backup password, reactions, scheduling, date and time pickers, choice lists) are dark grey with rounded corners and a thin border; text fields in them get the same violet focus border.
- **Backup progress** is a slim violet bar; loading spinners are violet.
- **Widget, notifications and launcher icon** use the new violet accent and darker colours (the icon's artwork is unchanged).

### Removed

- **Theme choice and "Use wallpaper colours".** TextCtrl is now always dark (from 0.4.0 it follows your phone's dark/light setting instead), so Settings › Appearance no longer offers Follow system, Light, Dark or Pure black, or wallpaper colours. If you had picked one of these, it is simply ignored.
- The Manrope font, replaced by Space Grotesk and JetBrains Mono.

## v0.2.1 (2026-10-23)

### Fixed

- **Search no longer closes when you pick a person.** On the Search tab, tapping **Everyone** to search one chat closed TextCtrl straight away if two chats had the same name (for example a contact with two numbers). The person list now opens with any names.
- **The time for a scheduled message fits on every phone.** Picking a date and time showed the clock in a dialog that was too narrow on smaller screens or with a large display size, so part of the clock could be cut off. It now has its own full-width dialog.

### Security

- **The backup password dialog is always kept out of screenshots** and screen recordings, from the moment it opens. Before, the first moment of the dialog could slip through on some phones.

## v0.2.0 (2026-10-23)

Scheduling, reactions, hidden chats, encrypted backups and a lot more ways to organise your chats.

### New

- **Scheduled messages.** Hold the send button (or tap the + button › Schedule message) and pick in an hour, tonight, tomorrow morning or afternoon, or any date and time. The message waits in the chat with its time; tap it to send it now, edit it or delete it. It goes out on time even when TextCtrl is closed or the phone restarted in between. You › Scheduled messages lists everything waiting in all chats.
- **Undo send.** After you tap send, a message waits 5 seconds with an **Undo** button before it really goes out; Undo puts it back in the text field. Choose 5 or 10 seconds, or turn it off, under Settings › Messages › Undo send. Replies from a notification still go out straight away.
- **Reactions.** Select a message and tap the smiley to react with 👍 ❤️ 😂 😮 😢 👎 ‼️ or ❓; pick it again to take it back. Reactions are sent as texts in the format iPhones and Google Messages use with phones on plain SMS ("Liked “see you at 8”"), so they show up properly on other phones too, and reactions people send you show as small badges on the message instead of separate texts. No Google services or RCS involved.
- **Copy one-time codes.** Verification codes get a **Copy** button under the message and in its notification, so you don't have to open the chat. Turn it off under Settings › Messages › Copy one-time codes.
- **Select several chats or messages.** Long-press a chat to start selecting; then mark them read or unread, pin, archive, mute, hide, block or delete them in one go. In a chat, long-press messages to copy, forward, save, react to, retry, see details of or delete them.
- **Search in a chat.** Chat menu › Search in chat finds words in that chat, with "2 of 7" and buttons to jump between older and newer matches.
- **Search by person.** On the Search tab, tap **Everyone** to search one chat only.
- **Unread filter.** The Chats tab has **All** and **Unread** filters, with the number of unread chats.
- **Notification sound per chat.** Chat menu › Notification sound gives a chat its own sound and vibration in Android's settings.
- **Unknown senders.** Settings › Privacy and security › Separate unknown senders keeps texts from numbers that aren't in your contacts (and that you never wrote to) out of your chats, in their own **Unknown senders** filter. They can still notify you or arrive quietly. All of this happens on the phone, with no spam service.
- **Hidden chats.** Chat menu › Hide chat moves a chat behind a PIN of its own (separate from the app lock). Hidden chats stay out of Chats, Archived, Search and the widget, their notifications never show names or text, and they close again as soon as you leave TextCtrl. Open them under You › Hidden chats.
- **Delete old messages.** Settings › Privacy and security › Delete old messages removes messages older than 30 days, 90 days or a year, once a day; each chat can have its own choice in its menu, or never delete. TextCtrl asks before turning it on, since it deletes straight away.
- **Backup and restore.** You › Backup and restore saves every text and picture message to one file encrypted with a password you choose, wherever you like (phone, USB stick, any storage app), and restores it on a new phone without adding messages twice. Nothing goes to Google or any server.
- **Bubble colour and text size.** Settings › Appearance: pick the colour of your own message bubbles and a bigger or smaller text size for chats.
- **Recent chats widget** for your home screen, with a New chat button. It hides your chats while the app lock is on and leaves out hidden and archived chats; message text follows "Notification content".
- **Tablets and unfolded foldables** show the chat list and the open chat side by side.

### Security

- **Scheduled messages and the list of hidden chats are encrypted** with a key kept in the phone's secure hardware, so a copy of TextCtrl's files doesn't reveal them. Their attachments are copied into TextCtrl's private storage until they're sent.
- **Backups are encrypted with AES-256-GCM** using a key made from your password (PBKDF2, 600,000 rounds), in chunks that can't be reordered, swapped or cut off without TextCtrl noticing. A wrong password, a damaged file or an incomplete one each get their own clear message.
- **The hidden chats PIN** works like the app lock's (stretched with PBKDF2 and the phone's secure hardware, waits after wrong tries) and its screens are kept out of screenshots. Forgetting it needs your phone's own screen lock to reset, and the hidden chats come back to Chats.
- **Still no internet access.** Scheduling uses Android's alarms, backups go to a file you pick, and reactions are ordinary texts.

## v0.1.0 (2026-10-23)

The first release of TextCtrl: a private, Google-free SMS and MMS app in the Ctrl series.

### New

- **Your phone's SMS app.** On first start TextCtrl asks to become your SMS app (only that app receives texts), then for contacts, notifications and phone access, all optional. If another app takes over later, TextCtrl offers to become your SMS app again. Your existing texts are there straight away, because every SMS app shares Android's message store.
- **Instant messages without push.** Texts and picture-message notices come straight from the mobile network: Android hands them to TextCtrl the moment they arrive. No Google, no ntfy, no server, and nothing running in the background.
- **Texts and picture messages.** Long texts are split into parts automatically, with a counter showing what's left. Picture messages carry text, pictures, videos, audio, contact cards and other files (up to 5 per message); pictures are shrunk to fit your carrier's size limit.
- **Group chats.** A message to several people goes out as one group picture message so everyone sees the replies, and incoming group messages land in the same group chat with each sender's name. Turn **Group texts as picture messages** off to send everyone their own text instead.
- **Chats** tab with every conversation, newest first: contact names and pictures, unread counts, drafts, and a mark for messages that didn't send. Long-press a chat to pin it, mark it read or unread, mute it, archive it, block the number or delete the chat.
- **Archived** tab for chats you've put away. A new message brings a chat back to Chats.
- **Search** tab that finds texts and picture messages by their words; tapping a result opens the chat at that message.
- **Chat screen** with bubbles grouped by day, times, sending, sent, delivered and not-sent states, tappable web links, pictures you can view full screen (pinch or double-tap to zoom), and other attachments that open in the right app. Long-press a message to copy it, forward it, save its pictures or videos, see its details or delete it. Tap a message that didn't send to try again.
- **New chat** screen: search your contacts or type a number. Tapping a contact opens the chat; the group button next to each one builds a group chat instead.
- **Picture messages are fetched automatically** over mobile data, or by tapping them when **Fetch picture messages automatically** is off. A message that couldn't be fetched shows its size and how long it can still be fetched.
- **Notifications** for each chat, with **Reply** and **Mark as read** right in the notification, the sender's name and picture, and chats in Android's Conversations section. Muted chats stay quiet. A separate notification tells you when a message couldn't be sent.
- **Notification content** setting: show name and message, name only, or just "New message". The lock screen only shows "New message" unless Android is set to show everything there.
- **Dual SIM:** each chat sends on the SIM it last used (tap the SIM button by the text field to change it), and you can pick the SIM for new chats in Settings.
- **Delivery reports** (off by default; some carriers charge for them).
- **Blocked numbers** using Android's own block list, shared with your phone app: block from a chat, add a number by hand, or unblock under You › Blocked numbers.
- **Drafts** are kept per chat when you leave it, and shown in the chat list.
- **Text this number** from the dialer, contacts and `sms:` links; **share to TextCtrl** from any app (text and pictures); **forward** messages to other chats; a **New chat** launcher shortcut; **reply with a message** when you decline a call.
- **Call** and **Add to contacts** from a chat.
- **Save to phone** for pictures and videos, into Pictures › TextCtrl.
- **App lock** like every Ctrl app: its own PIN of at least 6 digits, optional fingerprint unlock (strong biometrics only), lock again as soon as you leave or after a while, and a wait after wrong PINs that restarts and clock changes don't skip. Drafts are encrypted with the PIN while the app is locked.
- **Hide in screenshots** and **Hide in recent apps**, two separate switches in **Settings > Privacy and security**, both off by default.
- **Themes:** follow system, light, dark or pure black, optionally with your wallpaper's colours. A floating glass tab bar on phones and a side rail on tablets, like the other Ctrl apps.

### Security

- **No internet access at all.** TextCtrl doesn't have Android's internet permission (it's removed even if a library asks for it), so it can't contact any server. Picture messages are sent and fetched by Android's own MMS service with your carrier.
- **Telemetry-free:** no analytics, crash reporting or tracking.
- **Picture messages are read and written by TextCtrl itself**, from the MMS specifications, with no outside libraries.
- **Pictures you send are re-saved before sending**, which removes their location and camera details.
- **Only Android can deliver messages to TextCtrl:** the incoming-text, picture-message and quick-reply entry points require Android's own system permissions, and the sent, delivered, fetched and notification-action receivers can't be reached by other apps at all.
- **Shares from other apps only accept content they really share:** file paths and links back to TextCtrl's own files are ignored, so another app can't make TextCtrl send its private files.
- **Forgot PIN?** needs your phone's own screen lock before the app lock can be reset, so someone holding your phone can't just turn it off.
- **With the app lock on**, chats aren't added to Android's conversation shortcuts, so names don't show in the launcher or share sheet, and the recent-apps screen always shows a blank card.
- **Android's suggested replies and actions are turned off** on message notifications, so the system doesn't read your messages to make them.
- **Copied message text is marked as sensitive:** Android's clipboard preview and keyboards with a clipboard history don't show or keep it.
- **No backups** of TextCtrl's own data (settings, drafts, app lock).
