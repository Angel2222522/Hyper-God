# Architecture

## Dependency direction

`Compose UI → HyperGodViewModel → domain policies / HyperGodRepository → Room + private files + Android system APIs`

`MainActivity` owns only activity-result contracts, secure-window behavior, camera/SAF entry points, and biometric prompts. Durable operations live below the UI.

## Storage ownership

- **Room:** documents, page OCR references, cases, relationships, checklists, timeline, reminders, FTS, suggestions, and audit facts.
- **Private files:** AES-GCM envelopes for originals/pages, with stable document IDs rather than filenames as identity.
- **DataStore/preferences:** only small device-local settings and lock state; never document content or backup passwords.
- **Cache/staging:** camera captures, bounded renders, exports, and restore candidates. They are not durable truth.

File writes use same-directory temporary output and atomic rename. Cross-database/file deletion and restore use journals so process death leaves either the old state or a deterministically recoverable staged state.

## Work ownership

- OCR: unique WorkManager job per document.
- Reminder delivery: unique WorkManager job per reminder.
- Imports, backup, restore, and exports: repository/service operations with one coordinator, cancellation, and visible busy/error state.
- Startup recovery: application-owned, fail-closed gate before sensitive actions.

## Source traceability

Each extracted field stores confidence and provenance in the extraction payload; authoritative metadata is separated from unconfirmed suggestions. Manual ownership prevents later OCR from overwriting user-confirmed facts. Page OCR remains linked to a stable page index.

## Release identity

- Application ID: `com.angel.hypergod`
- Namespace: `com.angel.hypergod`
- First version: `versionCode 1`, `versionName 1.0.0`
- Minimum Android: API 26
- Target / compile Android: API 36

Future releases must keep the application ID, use monotonic version codes, preserve the Room/file/backup formats through explicit migrations, and use the same external release signing certificate.

