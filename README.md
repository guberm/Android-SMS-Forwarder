# SMS Sync: Android to Google Sheets & Gmail

A background Android application that monitors incoming SMS messages and automatically syncs them to a Google Spreadsheet and forwards them to your Gmail inbox.

## Features
- **Real-time Sync**: Log SMS (Sender, Timestamp, Message) to Google Sheets instantly.
- **Gmail Notifications**: Receive a copy of every SMS in your Gmail.
- **Direct Google API Integration**: No middleman scripts; uses official Google Auth.
- **Always Running**: Implemented as a Foreground Service with "Unrestricted" battery usage capability.
- **English UI**: Clean and simple interface.

## Prerequisites
1. **Google Cloud Project**:
   - Enable **Google Sheets API**, **Google Drive API**, and **Gmail API**.
   - Create an **Android OAuth Client ID**.
   - Add your **SHA-1** fingerprint and Package Name: `com.guberdev.smsforwarder`.
2. **Permissions**:
   - The app requires `RECEIVE_SMS` and `POST_NOTIFICATIONS` permissions.
   - You must manually set Battery Usage to **Unrestricted** for the app to run permanently.

## Setup
1. Clone the repository.
2. Open in Android Studio.
3. Build the APK: `Build > Build APK(s)`.
4. Install on your Android device.

## License
MIT
