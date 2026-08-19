# Hyper God — Capability and privacy boundary

This matrix is the implementation contract for the first production line. Local durable data is the source of truth; no screen, worker, or optional intelligence layer may silently invent it.

| Area | Durable local state | Permission / network | Unavailable or failure behavior |
|---|---|---|---|
| Documents | AES-GCM encrypted originals/pages under the app-private files root; metadata and OCR in Room | SAF grants for user-selected files; no broad storage permission; no network | Import is rejected before commit or left safely retryable; existing data remains valid |
| Scanner | Temporary app-cache captures until the user confirms; encrypted pages after import | Camera only when requested | Denial keeps import available; cancellation deletes temporary captures; advanced auto-crop is never claimed when confidence is insufficient |
| OCR | Bundled Greek and English Tesseract models; page text plus document aggregate | None | Explicit queued/processing/failed states, bounded retry, manual metadata remains usable |
| Search | Room FTS over title, filename, OCR, issuer, category, tags, and protocol | None | Empty and failed processing are distinct; no fabricated semantic result |
| Cases | Cases, links, checklists, timeline, notes, status, and deadlines in Room | None | Invalid links/dates are rejected transactionally |
| Reminders | Persisted deadlines and unique WorkManager jobs | Notification permission on Android 13+ | Deadline stays visible when delivery is denied; no silent loss or duplicate jobs |
| Lock | Device credential / strong biometric authorization state | Biometric capability | Already-enabled lock fails closed; a UI lock is not described as database encryption |
| Backup | Versioned password-encrypted portable package with manifest and bounded entries | SAF destination/source | Wrong password, corruption, traversal, oversized or unsupported packages fail before live-state mutation |
| Export | User-requested plaintext PDF/ZIP written through SAF | SAF destination | Clearly labelled plaintext; temporary decrypted files are bounded and cleaned |
| Network | None | No `INTERNET` permission | All core flows remain functional offline |

## Sensitive copies

- Originals and imported pages: encrypted app-private files.
- OCR, metadata, relations, FTS index, timeline, and reminders: app-private Room database. They remain sensitive even though the current SQLite file is not SQLCipher-encrypted.
- Camera, render, export, and restore staging: app cache with strict names, byte/count limits, `finally` cleanup, and startup stale-file recovery.
- Notifications: generic wording without document or case titles.
- Screenshots and recent-app preview: blocked with `FLAG_SECURE`.
- Android Auto Backup/device transfer: disabled; portability is provided only by explicit encrypted backup.
- Logs: no OCR text, passwords, document contents, or user identifiers.

## Bounded work

`LibraryLimits` is the single policy for document count, pages, bytes, decoded pixels, OCR characters, metadata lengths, archive entries, and aggregate restore size. PDF rendering is page-at-a-time, bitmaps are sampled, OCR is serialized through unique work, and cancellation is propagated.

## Explicit degradation

- Search is deterministic FTS in v1. No local embedding or LLM is required.
- Grounded answers are deterministic retrieval and fact/timeline synthesis; absent evidence returns “Δεν βρέθηκαν αρκετές πληροφορίες”.
- Scanner enhancement is conservative. Manual page order, rotation, removal, and retake are supported; unreliable automatic perspective claims are excluded until device-validated.
- Document bytes are encrypted. Database/FTS encryption remains a documented boundary rather than an unsafe home-grown SQLite wrapper.

