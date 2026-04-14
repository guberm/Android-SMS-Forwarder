# Android Notifications Forwarder

An Android background app that monitors incoming SMS and app notifications (WhatsApp, Telegram, Viber, Teams, etc.) and automatically syncs them to a Google Spreadsheet and forwards them to your Gmail inbox.

## Features

- **SMS Monitoring** — captures incoming SMS messages via a content observer
- **App Notifications** — listens to notifications from WhatsApp, Telegram, Viber, Slack, Teams, Discord, and more via Android's NotificationListenerService
- **Google Sheets Sync** — logs every message (timestamp, source, sender, body) to a spreadsheet
- **Gmail Forwarding** — sends a formatted HTML email to yourself for each message
- **Smart Filtering** — skip system/status notifications (backups, media restore, etc.); enable/disable individual sources from the UI
- **Retry with Backoff** — failed syncs retry up to 5× with exponential backoff
- **Deduplication** — prevents double-logging within a 15-second window
- **Always-on Service** — foreground service with boot autostart and battery optimization bypass
- **Log Viewer** — in-app log with filter chips by source/level

## Supported Sources

| App | Package |
|---|---|
| Native SMS | (system) |
| Google Messages | `com.google.android.apps.messaging` |
| Samsung Messages | `com.samsung.android.messaging` |
| WhatsApp | `com.whatsapp` |
| WhatsApp Business | `com.whatsapp.w4b` |
| Telegram | `org.telegram.messenger` |
| Viber | `com.viber.voip` |
| Microsoft Teams | `com.microsoft.teams` |
| Slack | `com.slack` |
| Discord | `com.discord` |
| Facebook Messenger | `com.facebook.orca` |
| Skype | `com.skype.raider` |
| WeChat | `com.tencent.mm` |
| LINE | `jp.naver.line.android` |

## Setup

### 1. Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and create a project.
2. Enable: **Google Sheets API**, **Google Drive API**, **Gmail API**.
3. Create an **OAuth 2.0 Client ID** → type: **Android**.
4. Add your SHA-1 fingerprint and package name: `com.guberdev.smsforwarder`.

### 2. Build & Install

```bash
git clone https://github.com/guberm/Android-SMS-Forwarder.git
cd Android-SMS-Forwarder
./gradlew assembleRelease
# Install the APK from app/build/outputs/apk/release/
```

Or download the latest APK from [Releases](https://github.com/guberm/Android-SMS-Forwarder/releases).

### 3. First Run

1. Open the app and **Sign in with Google**.
2. Tap **Grant Notification Access** and enable the app in system settings.
3. Tap **Disable Battery Restrictions** and set to Unrestricted.
4. Tap **Start Monitoring** — the service starts automatically on next boot too.
5. (Optional) Open **Message Sources** to enable/disable individual apps.

## Permissions

| Permission | Purpose |
|---|---|
| `RECEIVE_SMS` / `READ_SMS` | Read incoming SMS |
| `READ_CONTACTS` | Resolve phone numbers to contact names |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read app notifications |
| `FOREGROUND_SERVICE` | Keep service alive |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Android from killing the service |
| `POST_NOTIFICATIONS` | Show persistent foreground notification |
| `INTERNET` | Send data to Google APIs |

## License

MIT
