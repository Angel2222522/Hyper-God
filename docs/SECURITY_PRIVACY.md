# Security and privacy

## Protection model

- Original document/page bytes and the personal profile use versioned AES-GCM envelopes backed by a non-exportable Android Keystore key.
- Portable backups use PBKDF2-HMAC-SHA-256 and AES-GCM with random salt/IV; passwords are cleared from mutable buffers when possible.
- `FLAG_SECURE` blocks screenshots and recent-task previews. Device credential/strong biometric can lock the UI and sensitive operations.
- Android automatic backup/device transfer is disabled. There is no `INTERNET` permission, telemetry, analytics, ads, account, or backend.
- FileProvider exposes only explicit cache subdirectories; the private document tree is never directly shared.
- Notifications omit document/case titles and appear as private generic reminders.

## Recovery and malicious-input boundaries

- Imports are validated and bounded before Room commit. AES-GCM writes use same-directory `.part` files and atomic rename.
- Exact content fingerprints are calculated while encrypting; duplicates remain separate records and are only suggested.
- Delete and restore use quarantine/staging plus durable journals. Startup recovery chooses a generation from evidence and fails closed on ambiguity.
- Restore rejects traversal, absolute paths, duplicate IDs/names, malformed manifests, unsupported versions, oversized archives, missing references, and invalid page counts before live mutation.
- Temporary plaintext render/OCR/export/profile files are deleted in `finally` and stale cache recovery.

## Honest boundary

Room, OCR text, metadata and FTS are private to the Android application sandbox but are not SQLCipher-encrypted. This is documented rather than hidden behind an unaudited custom database wrapper. Ordinary ZIP/PDF exports are plaintext by user request; only `.hgb` backups are password-encrypted.

