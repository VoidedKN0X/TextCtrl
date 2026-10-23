# Changelog

What changed in each TextCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/TextCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

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
