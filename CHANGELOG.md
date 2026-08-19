# Changelog

## 1.0.0 — 2026-08-19

- Established the local-first Android architecture and premium Greek design system.
- Added encrypted import, viewer, offline Greek/English OCR, metadata extraction and FTS search.
- Added cases, checklist, timeline, deadlines, reminders, exports and encrypted backup/restore.
- Added exact duplicate fingerprints, conservative relationship/version suggestions and grounded local Q&A.
- Added Keystore-encrypted personal profile and labelled identifier discrepancy detection.
- Added scanner page reorder, rotation, deletion and retake controls.
- Added unit, migration, backup, repository, OCR, viewer and Compose navigation tests plus emulator screenshots in CI.
- Added explicit consent and transient-grant handling for external shares, bounded cancellable provider reads, serialized import/OCR admission and camera payload validation.
- Hardened restore with a generation-wide maintenance barrier, exact ZIP/JSON membership and budgets, actual PDF/image validation, legacy-envelope authentication and operation-bound recovery journals.
- Upgraded new portable backups to a parameterized 600,000-iteration `PFBK2` KDF envelope while retaining authenticated `PFBK1` restore compatibility.
- Completed and published a sealed security scan; all six high-confidence findings were remediated in the release candidate.
