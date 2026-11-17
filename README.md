# Expense Ledger (Android)

Package ID: `com.example.expenseledger`
Version: `1.0` (Code `1`)

## Features Included
- **Live Balance Dashboard**: Isolated `PAID (₹)`, `HOLD (₹)`, `NEED (₹)`, and Grand Commitment calculated strictly from active Expenses.
- **Expenses**: Search bar, `All / Paid / Hold / Need` & label filters, Bulk Select action bar (`Paid / Hold / Need / Bin`), collapsible Quick Templates & Recurring Bills with `Due` badges, 1-tap Duplicate, 1-tap status cycling, multi-select Labels, expandable attached Lists, and audit log.
- **Insights & Calendar**: Monthly budget progress bars per Label (`Within budget / Near limit / Over by ₹...`) and interactive Mon–Sun daily spend Calendar grid.
- **Recycle Bin & Retention (v1.2)**: Soft-delete Expenses, Labels, Lists, and Templates into Bin with `30 / 60 / 90 Days` or `Manual Only` auto-purge, Restore, Delete Permanently, Empty Bin, and quick Snackbar Undo.
- **Labels**: Isolated Paid/Hold/Need sums per label, optional monthly budget cap, linked expense list, direct add-expense inside label, and 1-tap label summary share.
- **Lists**: Itemized notes with automatic numeric sum detection and 1-tap "Use in New Expense".
- **Slide-up Calculator**: `+`, `-`, `÷`, `×`, `=`, `%`, `AC/C`, `00.0`, timestamped History tape, and 1-tap "Send to Expense" / "Insert into List".
- **Export & Backup (v1.2)**: Storage Access Framework CSV Spreadsheet Export (`.csv`), JSON Backup & Restore (`.json`), Shareable plain-text summaries, and custom adaptive vector launcher icon.
- **Persistent SQLite + JSON Storage** (`LedgerStorage.kt`).
- **CI/CD Pipeline** (`.github/workflows/build-apk.yml`): Dual-path PKCS12 signing with `gradle assembleRelease`.
