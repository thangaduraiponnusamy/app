# MyBook – shared expense tracker (Android)

Kotlin + Jetpack Compose app for tracking household, flatmate and trip expenses in **₹ INR**.
Data lives on the phone (Room database), with optional **Google Drive backup** (like WhatsApp).

- Package: `com.mybook.app` · minSdk 26 · targetSdk 35
- Repo: https://github.com/thangaduraiponnusamy/app (APK built by GitHub Actions)

---

## Features

### Books & members
- **Books** = separate ledgers (e.g. Home, Flatmates, Goa trip). Each has its own members (plain names, e.g. "Durai, Thanga").
- **Delete book** requires typing the exact book name (two-step confirmation so nothing is deleted by mistake).

### Home (Dashboard)
- Gradient **Total Balance** card: Income ↙ / Expenses ↗ for the selected period.
- **Week / Month / Year** pills with ‹ › navigation.
- **Spending donut**: centre shows "₹x SPENT"; **tap a slice (or legend item)** to see that category's emoji, name, amount and %.
- **Last 5 recent transactions** with "See all".
- Yellow banner when payments are due.

### Bottom tabs
| Tab | What it shows |
|---|---|
| **Home** | Dashboard (above) |
| **Categories** | All predefined expense and income categories with period totals. Tap one to see its entries and **add a new entry with that category pre-selected** (+ button). "Edit" opens Admin. |
| **Income** | Green Total Income card, breakdown by source, income entries. + adds income. |
| **Reports** | Month / Year / All years (at least the last 5): net savings, monthly bar chart, by category, by payer. |

### Adding expenses & income
- Amount (₹), title, date, category chips (with **"+ New"** to create a category inline), paid/received by.
- **Split is optional**: "Split with others" switch (off by default = payer's own expense). When on, choose who shares it equally.
- Optional receipt photo (expenses only).

### Payment alerts (bell icon)
- Reminders for rent, EMI, school fees, bills: amount, due date, repeat (Once / Weekly / Monthly / Yearly), category.
- The bell shows a **badge** when something is due today or overdue.
- **Mark paid** records an expense and moves the reminder to its next due date.
- In-app only (no system notifications yet).

### Admin page (☰ menu → Admin)
- **Google Drive backup** (see below).
- Add / edit / delete **expense and income categories** (name, emoji, colour). Used categories can't be deleted.
- Add / remove **members** (members with entries can't be removed).

### Balances & settle up (☰ menu)
- Who owes whom across all split expenses, with suggested payments and "Mark paid" settlements.

### Export
- ☰ menu → **Export to CSV (Excel)**: all entries, UTF-8 with BOM, spreadsheet-formula injection neutralised.

---

## Google Drive backup (like WhatsApp)

- Admin → **Choose Google account** → pick any Google account on the phone.
- Backs up the database and receipts as `mybook-backup.zip` in Drive's **hidden app-data folder** (`drive.appdata` scope). It is not visible in normal Drive files and the app can't read your other Drive files.
- **How often**: Off / 15 min / Hourly / Daily / Weekly / Monthly (WorkManager, Wi-Fi or mobile data). **Back up now** and **Restore** buttons.
- **After reinstall**:
  - If Android restored MyBook's settings (it remembers the backup account), the Drive backup is **restored automatically**.
  - Otherwise MyBook asks once: **"Restore from Google Drive"** or **"Skip, start fresh"**.
- Android Auto Backup is also enabled (`allowBackup`). The device-only file `mybook_device.xml` (the "already asked about restore" flag) is excluded via `res/xml/backup_rules.xml` and `data_extraction_rules.xml`.
- Restored zip entries are validated (only `db/mybook.db` and `receipts/<file>`), which blocks zip-slip.

### One-time Google Cloud setup (required for Drive backup)
Google sign-in only works when the APK is signed with a **fixed key** whose SHA-1 is registered:
1. https://console.cloud.google.com → create a project → **APIs & Services → Library → Google Drive API → Enable**.
2. **OAuth consent screen**: External, app name "MyBook", add scope `.../auth/drive.appdata`, add your Gmail and family members' Gmails as **Test users**.
3. **Credentials → Create credentials → OAuth client ID → Android**: package `com.mybook.app`, SHA-1 of the release key (below).
4. Set up release signing (next section) so every build uses the same key.

---

## Build the APK (GitHub Actions)

The workflow `.github/workflows/build-apk.yml` runs on every push to `main` or manually (**Actions → Build APK → Run workflow**). Download the `mybook-apk-<n>` artifact (zip with the APK).

- **Without signing secrets** → debug APK signed with a random key per build. Uninstall the previous build before installing (data is lost), and **Google Drive backup will not work**.
- **With signing secrets** → release APK that installs over older versions, keeps data, and works with Google sign-in.

### Release signing (one-time)
1. Create a keystore on your PC (keep it safe and backed up):
   ```powershell
   keytool -genkeypair -v -keystore "$HOME\mybook-release.jks" -alias mybook -keyalg RSA -keysize 2048 -validity 10000
   ```
2. SHA-1 for the Google Cloud Android client:
   ```powershell
   keytool -list -v -keystore "$HOME\mybook-release.jks" -alias mybook
   ```
3. Base64 to clipboard:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\mybook-release.jks")) | Set-Clipboard
   ```
4. GitHub repo → **Settings → Secrets and variables → Actions**:

   | Secret | Value |
   |---|---|
   | `AURUM_KEYSTORE_BASE64` | clipboard content |
   | `AURUM_KEYSTORE_PASSWORD` | keystore password |
   | `AURUM_KEY_ALIAS` | `mybook` |
   | `AURUM_KEY_PASSWORD` | key password |

   (The secret names keep the old `AURUM_` prefix used by the workflow.)

---

## Project structure

```
src/main/java/com/mybook/app/
  MyBookApp.kt              App + AppContainer (manual DI: prefs, repository, backupManager)
  MainActivity.kt           Edge-to-edge, light system bars
  data/
    Entities.kt             Book, Member, Category, Expense (type EXPENSE/SETTLEMENT/INCOME), Reminder
    Daos.kt, MyBookDatabase.kt   Room v4 (migrations 2→4 no-op, 3→4 drops the old sms_imports table; others destructive)
    BookRepository.kt       All data operations (books, members, categories, entries, reminders, CSV)
    Prefs.kt                Active book, backup account/frequency/last time, device-only restore flag
    backup/DriveBackup.kt   GoogleDriveAuth, DriveBackupClient (REST, appDataFolder), BackupManager (zip/restore/schedule), BackupWorker
  domain/
    Domain.kt               Money (INR, en-IN format, compact ₹3.2k/₹1.2L), DefaultCategories, Period, Balances, Reports, CsvExporter
  ui/
    MyBookRoot.kt           RestoreGate → Books list or per-book NavHost
    theme/Theme.kt          Light lavender (#F7F5FF), violet→pink gradient, white cards
    common/                 ScreenHeader, SquareIconButton, PeriodBar/Pills, CategoryBadge, TransactionRow, CategoryDialog
    books/                  Book list + create dialog
    home/                   HomeScreen (menu, bell, tabs), DashboardTab (balance card, donut), CategoriesTab, IncomeTab, ReportsTab
    expenses/               EntryEditScreen (add/edit, optional split, inline category), CategoryEntriesScreen
    alerts/                 Payment reminders
    balances/               Balances & settle up
    admin/                  Admin (backup, categories, members)
    backup/                 BackupSection (Admin), RestoreGate (startup restore prompt)
```

### Navigation routes (per book)
`home` · `entry/{EXPENSE|INCOME}/{id|new}?category={id}` · `category/{id|all}` · `alerts` · `admin` · `balances`

---

## Limitations / ideas for next steps
- Payment reminders are in-app only: no system notifications yet (add WorkManager + POST_NOTIFICATIONS).
- Bank SMS reading was tried and removed: Play Protect blocks sideloaded apps that request SMS permissions.
- No multi-phone live sharing (each phone has its own data; Drive backup is per Google account).
- Database migrations: 2→4 and 3→4 are migrated; other schema changes reset data (POC).
- Split is equal shares only (no custom amounts or percentages yet).
