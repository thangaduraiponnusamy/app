# MyBook – shared expense tracker (Android POC)

Kotlin + Jetpack Compose proof of concept. All data stays on the phone (Room database); there is no sign-in, cloud sync or server.

## Features
- **Books**: separate expense books (e.g. Home, Flatmates, Goa trip), each with its own currency and members.
- Add, edit and delete expenses with a category, date, payer, equal split, note and optional receipt photo.
- Balances and settle-up suggestions, with "Mark paid" settlement entries.
- Reports by month, by year and across all years (at least the last 5), by category and by payer.
- CSV export with a UTF-8 BOM, so it opens directly in Excel.

## Build the APK with GitHub Actions
The workflow `.github/workflows/build-apk.yml` runs on every push to `main` or on a manual run.

- Without signing secrets it builds a **debug APK**. Every build has a different key, so uninstall the previous build before installing a new one (this deletes the app's data).
- With signing secrets it builds a **release APK** that installs over older ones and keeps the data. To set it up:
1. Create the signing keystore once, on your PC and outside this folder. Back it up: without it you cannot ship updates that install over the existing app.
   ```powershell
   keytool -genkeypair -v -keystore "$HOME\mybook-release.jks" -alias mybook -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Copy the keystore as Base64 to the clipboard:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\mybook-release.jks")) | Set-Clipboard
   ```
3. Add these under **Settings → Secrets and variables → Actions** in the GitHub repository:

   | Secret | Value |
   |---|---|
   | `AURUM_KEYSTORE_BASE64` | clipboard content from step 2 |
   | `AURUM_KEYSTORE_PASSWORD` | keystore password |
   | `AURUM_KEY_ALIAS` | `mybook` |
   | `AURUM_KEY_PASSWORD` | key password (same as the keystore password if you pressed Enter) |

Download the `mybook-apk-<n>` artifact (a zip containing the APK) from the workflow run, copy the APK to the phone, open it and allow "Install unknown apps".

## Using it
1. Tap **New book**, enter a name, currency and members (comma separated).
2. Add expenses with **+**, choosing who paid and who shares it.
3. Check **Balances** to see who owes whom, and **Reports** for monthly and yearly spending.

## Limitations (POC)
- Data lives only on this phone. Uninstalling the app deletes it unless Android's automatic backup restores it.
- No sharing between phones yet.
