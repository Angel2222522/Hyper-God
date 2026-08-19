# Security and privacy

## Protection model

- Original document/page bytes and the personal profile use versioned AES-GCM envelopes backed by a non-exportable Android Keystore key.
- New portable backups use a versioned `PFBK2` envelope with PBKDF2-HMAC-SHA-256 (600,000 iterations), AES-256-GCM and random salt/IV. Legacy `PFBK1` remains read-compatible; passwords are cleared from mutable buffers when possible.
- `FLAG_SECURE` blocks screenshots and recent-task previews. Device credential/strong biometric can lock the UI and sensitive operations.
- Android automatic backup/device transfer is disabled. There is no `INTERNET` permission, telemetry, analytics, ads, account, or backend.
- FileProvider exposes only explicit cache subdirectories; the private document tree is never directly shared.
- Notifications omit document/case titles and appear as private generic reminders.

## Recovery and malicious-input boundaries

- External share intents require explicit accept/reject consent after unlock. Transient grants are never promoted to persisted SAF grants and incoming URI lists are bounded.
- Provider reads have byte, aggregate-space and deadline limits; import and OCR admission are serialized. Camera output is checked for compressed size, dimensions and pixel count before decoding.
- Imports are validated and bounded before Room commit. AES-GCM writes use same-directory `.part` files and atomic rename.
- Exact content fingerprints are calculated while encrypting; duplicates remain separate records and are only suggested.
- Delete and restore use quarantine/staging plus durable operation-ID journals. Startup recovery chooses a generation from evidence and fails closed on ambiguity.
- A process-wide maintenance lease drains current generation readers and prevents import/export/OCR mutations throughout restore validation, swap, Room commit, rollback and cleanup.
- Restore rejects traversal, absolute paths, duplicate IDs/names, unknown JSON fields, malformed manifests, unsupported versions, oversized/over-expanded archives, missing references, and invalid page counts before live mutation. Every page is authenticated, decrypted and validated as an actual bounded PDF or image before commit.
- Temporary plaintext render/OCR/export/profile files are deleted in `finally` and stale cache recovery.

## Security review

The repository includes the sealed [security scan report](security-scan/report.md) and its canonical JSON artifacts. Six high-confidence findings (2 high, 4 medium) were identified and remediated in the release candidate. The scan could not query private TAC advisories, and physical biometric/camera hardware remains a device-validation item.

## Honest boundary

Room, OCR text, metadata and FTS are private to the Android application sandbox but are not SQLCipher-encrypted. This is documented rather than hidden behind an unaudited custom database wrapper. Ordinary ZIP/PDF exports are plaintext by user request; only `.hgb` backups are password-encrypted.
