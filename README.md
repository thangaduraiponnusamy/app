# Aurum – shared expense tracker (Android)

Kotlin + Jetpack Compose app for households and groups. Google Drive is the long-term storage, so the app needs no backend server.

## Features
- Groups (tenants): each group is a shared Google Drive folder. The owner invites members by email.
- Add, edit and delete expenses with categories, date, payer, equal split and an optional receipt photo.
- Balances and settle-up suggestions, with "Mark paid" settlement entries.
- Reports by month, by year, and across all years (at least the last 5) by category and by payer.
- CSV export with a UTF-8 BOM, so it opens directly in Excel.
- Offline-first: data is stored locally in Room and synced in the background with WorkManager.

## How storage works
```
My Drive/Aurum - <Group>/               shared with all members (writer access)
  tenant.json                            group name + currency
  expenses_<year>_<member>.json          one file per member per year
  receipts/<date>_<expenseId>.jpg        receipt photos
```
- Each member writes only their own yearly files, so members never overwrite each other's files.
- On sync the app downloads changed files and merges records by ID. When two people edit the same expense, the later edit wins (`updatedAt`).
- Deletions are kept as tombstones, so they sync to every device.
- Files stay in Drive permanently. Splitting files by year keeps them small.

## One-time Google Cloud setup (required)
1. Open https://console.cloud.google.com and create a project.
2. In **APIs & Services → Library**, enable the **Google Drive API**.
3. In **APIs & Services → OAuth consent screen**:
   - Choose User type **External** and fill in the app name and support email.
   - Add the scope `https://www.googleapis.com/auth/drive`.
   - Under **Test users**, add the Gmail address of **every group member** (up to 100 while the app is in Testing mode).
4. In **APIs & Services → Credentials → Create credentials → OAuth client ID → Android**:
   - Package name: `com.aurum.expense`
   - SHA-1: the fingerprint of the release signing key (see "Build the APK with GitHub Actions" below).
   - The app code needs no client ID or secret. Google Play services matches the app by package name and SHA-1.

## Build the APK with GitHub Actions
The workflow `.github/workflows/build-apk.yml` builds a signed release APK on every push to `main` or on a manual run.

1. Create the signing keystore once, on your PC and outside this folder. Back it up: without it you cannot ship updates that install over the existing app.
   ```powershell
   keytool -genkeypair -v -keystore "$HOME\aurum-release.jks" -alias aurum -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Get its SHA-1 and register it in the Google Cloud Android OAuth client (step 4 above):
   ```powershell
   keytool -list -v -keystore "$HOME\aurum-release.jks" -alias aurum
   ```
3. Copy the keystore as Base64 to the clipboard:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\aurum-release.jks")) | Set-Clipboard
   ```
4. Push this folder to a **private** GitHub repository. Then add these under **Settings → Secrets and variables → Actions**:

   | Secret | Value |
   |---|---|
   | `AURUM_KEYSTORE_BASE64` | clipboard content from step 3 |
   | `AURUM_KEYSTORE_PASSWORD` | keystore password |
   | `AURUM_KEY_ALIAS` | `aurum` |
   | `AURUM_KEY_PASSWORD` | key password (same as the keystore password if you pressed Enter) |
5. Go to **Actions → Build APK → Run workflow**. When it finishes, download the `aurum-apk-<n>` artifact (a zip containing the APK).
6. Copy the APK to the phone, open it and allow "Install unknown apps" when asked. Newer builds install over older ones and keep the data.

## Using it
1. **Owner**: sign in, create a group (name + currency), then use **Members** to invite people by email.
2. **Members**: install the app, sign in with the invited Google account, and tap **Join** under **Shared with you**.
3. Add expenses with **+**. Sync runs automatically, or tap the refresh icon.

## Known limitations / next steps
- The `drive` scope is a Google *restricted* scope. Testing mode is fine for family and friends. A public Play Store release requires Google OAuth verification and a security assessment.
- In Testing mode, Google may ask users to grant access again after about 7 days. The app shows the consent screen again when that happens.
- Conflict resolution is last-write-wins per expense.
- Receipts count against the storage quota of the member who uploaded them.
