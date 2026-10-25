# TextCtrl

**Texts and picture messages, private and Google-free.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/TextCtrl?label=download)](https://github.com/VoidedKN0X/TextCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) ![No internet access](https://img.shields.io/badge/internet-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

TextCtrl is an SMS and MMS app for Android. It works like any other texting app (it becomes your phone's SMS app) but has no internet access, no tracking and nothing from Google.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/TextCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Texts (SMS) and picture messages (MMS)**, sent and received like in any texting app
- **Instant**: new messages arrive the moment your phone receives them (see [How messages arrive](#how-messages-arrive))
- **Group chats** as one group picture message, so everyone sees the replies (or separate texts, if you prefer)
- **Pictures, videos and files**: attach up to 5, pictures are shrunk to fit your carrier's limit; view pictures full screen, save them to your phone or share them
- **Contact cards**: send a contact from your phone's contacts, and see received ones as a card with **Add to contacts**
- **Scheduled messages**: hold the send button to send later, even if the phone restarts in between
- **Undo send**: a few seconds to take a message back before it goes out
- **Reactions** (👍 ❤️ 😂 …) that work with iPhones and Google Messages over plain SMS, shown as badges on the message
- **Copy one-time codes** straight from the message or its notification
- **Notifications** with **Reply** and **Mark as read**, per-chat **mute** and **sound**, and a choice of what they show
- **Bubbles**: read and reply in a floating bubble over other apps, for all chats or the ones you choose (the app lock applies there too)
- **Pin, archive, mute, hide, block and delete** chats; **mark as read or unread**; select several at once
- **Swipe actions** you choose for left and right in the chat list, with **Undo** for archive, delete and block
- **Filters** for unread chats and **unknown senders** (numbers not in your contacts)
- **Hidden chats** behind a PIN of their own (see [Hidden chats](#hidden-chats))
- **Search** all your messages, one person's, or inside a chat, on the phone
- **Delete old messages** automatically, for everything or per chat
- **Encrypted backup and restore** to a file you keep (see [Backup and restore](#backup-and-restore))
- **Dual SIM**: pick the SIM per chat, and a default for new chats
- **Delivery reports** (optional), **retry** for messages that didn't go out, and a counter for long texts
- **Blocked numbers** using Android's own block list, shared with your phone app
- **Drafts** kept per chat
- **"Text this number"** from your dialer, contacts and links, **share to TextCtrl** from any app, and **reply with a message** when you decline a call
- **App lock** with its own PIN and optional fingerprint unlock; drafts stay encrypted while the app is locked (see [App lock](#app-lock))
- **Hide in screenshots** and **Hide in recent apps**, two separate switches, on their own or with the app lock
- The **Ctrl / Signal** look shared by every Ctrl app: high-contrast **dark and light** modes that follow your phone's setting, with your own **bubble colour** and **text size**
- **Recent chats widget** for the home screen
- **Tablets and foldables**: chat list and chat side by side

## How messages arrive

There's no push service involved, and none is needed. Texts don't come over the internet: they arrive over the mobile network, and Android hands every incoming text straight to the phone's SMS app the moment it's received. Picture messages work the same way: the network sends a short notice, Android hands it to the SMS app, and Android's own MMS service fetches the picture over mobile data.

So TextCtrl gets your messages instantly without Google, without ntfy or any other server, and without even having internet access itself. It doesn't run in the background or use battery waiting for messages; Android wakes it when one arrives.

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- A SIM that can send texts. Picture messages need mobile data to be on.
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/TextCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds TextCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/TextCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/TextCtrl` in Obtainium by hand.

## Getting started

1. Open TextCtrl and tap **Make it my SMS app**. Android asks you to confirm; choose TextCtrl. Only your SMS app receives texts, so this is needed.
2. Allow contacts, notifications and phone access, or tap **Not now**. All three are optional and stay on the phone.
3. Your existing texts are already there: every SMS app shares Android's message store.

Settings, your SIMs, blocked numbers and About are behind the profile picture at the top right.

## Tips and troubleshooting

- **Picture messages don't send or arrive:** turn mobile data on (even on Wi-Fi, MMS goes over the mobile network) and check your carrier's MMS settings (APN) in Android's settings.
- **"Picture message, tap to fetch it":** fetching failed or **Fetch picture messages automatically** is off. Tap it to fetch it now.
- **"Not sent":** tap the message to try again. A notification tells you when a message fails while you're elsewhere.
- **Another app became your SMS app:** TextCtrl shows a screen to make it your SMS app again. Texts received in the meantime are in the shared message store, so you'll see them.
- **Group chats:** with **Group texts as picture messages** on (the default), a message to several people goes out as one group message and replies show up in the same chat. Turn it off to send everyone their own text.

## Hidden chats

Open a chat's menu and tap **Hide chat**. The first time, you choose a PIN for hidden chats (separate from the app lock). Hidden chats:

- stay out of Chats, Archived, Search and the widget;
- still notify you, but never with a name or message text;
- open under your profile picture › **Hidden chats**, after the PIN, and close again as soon as you leave TextCtrl.

Which chats are hidden is stored encrypted with a key in the phone's secure hardware. The messages themselves stay in Android's message store, so other SMS apps on the phone can still see them. Forgot the PIN? Confirm with your phone's screen lock and the hidden chats come back to Chats.

## Backup and restore

Under your profile picture › **Backup and restore**, TextCtrl saves every text and picture message (with their pictures and files) to one `.tcbak` file, wherever you choose: the phone, a USB stick, your computer or any storage app. Restore it on a new phone the same way; messages that are already there aren't added twice.

The file is encrypted with a password you choose (at least 8 characters): AES-256-GCM with a key made from the password with PBKDF2 (600,000 rounds), in chunks that can't be changed, reordered or cut off unnoticed. Without the password nobody can open it, and it can't be recovered, so write it down.

## App lock

Turn it on under your profile picture › **Settings > Privacy and security > App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your drafts are encrypted with the PIN while TextCtrl is locked.** The key only exists in memory while the app is unlocked and is wiped the moment it locks. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Picking pictures or files, sharing and opening an attachment don't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered. The lock screen lets you reset the app lock after confirming it's you with your phone's own screen lock; drafts encrypted with the old PIN are deleted, your messages stay.
- **With the app lock on**, the recent-apps screen always shows a blank card, and chats aren't added to Android's conversation shortcuts, so names don't show in the launcher or share sheet.
- **What it protects:** TextCtrl's own screens and drafts. Your texts live in Android's message store, like with every SMS app, so the lock doesn't encrypt them; your phone's own encryption and screen lock do.

Two more switches in the same section work with or without the app lock, and are both off until you turn them on:

- **Hide in screenshots**: screenshots and screen recordings show a blank screen (and so does the recent-apps view).
- **Hide in recent apps**: the recent-apps view shows a blank card instead of TextCtrl. Screenshots still work.

The lock and PIN screens are always kept out of screenshots and recordings, whatever these switches say.

## Privacy

**Telemetry-free.** TextCtrl has no analytics, crash reporting, usage stats or tracking of any kind.

- **No internet access at all.** TextCtrl doesn't have Android's internet permission, so it can't contact any server, not even by mistake. Texts go out over the mobile network to the people you write to. Picture messages are sent and fetched by Android's own MMS service, with your carrier.
- **Your messages** stay in Android's message store on your phone, the same place every SMS app uses. TextCtrl keeps only your settings, drafts and which chats are pinned, archived or muted.
- **Contacts** are only read on the phone, to show names and pictures. Nothing about them is stored by TextCtrl or sent anywhere.
- **Pictures you send** are shrunk and re-saved before sending, which also removes their location and camera details.
- **Notifications** show what you choose under **Notification content**: name and message, name only, or just "New message". The lock screen shows "New message" unless Android is set to show everything there. Android's suggested replies are turned off, so the system doesn't read your messages to make them.
- **Copied text** is marked as sensitive: Android's clipboard preview and keyboards with a clipboard history don't show or keep it.
- **Links** in messages open in your browser only when you tap them; TextCtrl doesn't load link previews.
- **Scheduled messages** are stored encrypted with a key in the phone's secure hardware until they go out, and are sent with Android's own alarms.
- **Unknown senders** are worked out on the phone from your contacts; there's no spam service.
- **Reactions** are ordinary texts, the same format iPhones and Google Messages use with SMS phones; no RCS or Google services.
- **No automatic backups:** Android doesn't back up TextCtrl's own data or move it to a new phone. Make an encrypted backup yourself under **Backup and restore**.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/TextCtrl.git
   cd TextCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

TextCtrl is free and open source. If it's useful to you, tap **Support TextCtrl** under **About** on the You page (tap your profile picture) to sponsor development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Found a problem or have an idea? Tap **Report a bug** in the same section, or [open an issue](https://github.com/VoidedKN0X/TextCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| **TextCtrl** (this app) | Texts and picture messages, private and Google-free |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

TextCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Space Grotesk and JetBrains Mono fonts are licensed under the SIL Open Font License 1.1; see `FONT-LICENSE-SpaceGrotesk.txt` and `FONT-LICENSE-JetBrainsMono.txt`.
