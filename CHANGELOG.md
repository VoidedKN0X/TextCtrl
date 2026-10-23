# Changelog

What changed in each TextCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/TextCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

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
